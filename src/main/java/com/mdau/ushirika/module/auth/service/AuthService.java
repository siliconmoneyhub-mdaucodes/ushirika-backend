package com.mdau.ushirika.module.auth.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.exception.TooManyRequestsException;
import com.mdau.ushirika.common.util.TextNormalizer;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.dto.*;
import com.mdau.ushirika.module.auth.entity.RefreshToken;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.RefreshTokenRepository;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.core.context.SecurityContextHolder;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final PasswordResetRateLimiter passwordResetRateLimiter;
    private final EmailVerificationOtpRateLimiter emailVerificationOtpRateLimiter;
    private final AuditLogService auditLogService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    private static final int OTP_EXPIRY_MINUTES = 15;

    static final String GENERIC_LOGIN_FAILURE =
            "Those details didn't match an account. Check your email, member ID or name and your password "
                    + "— or use \"Forgot password?\" to reset it.";
    static final String TOO_MANY_LOGIN_ATTEMPTS =
            "Too many sign-in attempts. Please wait 15 minutes and try again, or reset your password.";
    static final String TOO_MANY_CODE_ATTEMPTS =
            "Too many incorrect codes. Please wait 15 minutes and try again, or request a new code.";

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public void register(RegisterRequest req) {
        String email = TextNormalizer.normalizeEmail(req.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        if (userRepository.existsByPhone(req.phone())) {
            throw new ConflictException("An account with this phone number already exists");
        }

        PasswordPolicy.validate(req.password(), email, req.firstName(), req.lastName());

        String otp = generateOtp();
        User user = User.builder()
                .firstName(TextNormalizer.titleCase(req.firstName()))
                .middleName(TextNormalizer.titleCase(req.middleName()))
                .lastName(TextNormalizer.titleCase(req.lastName()))
                .email(email)
                .phone(req.phone())
                .password(passwordEncoder.encode(req.password()))
                .emailVerified(false)
                .emailVerificationOtp(otp)
                .emailVerificationOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .active(true)
                .build();

        userRepository.save(user);
        emailService.sendEmailVerificationOtp(user.getEmail(), user.getFirstName(), otp);
        log.info("User registered: {}", user.getEmail());
    }

    // ── Verify Email ──────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest req) {
        String otpKey = "verify:" + req.email().toLowerCase();
        if (loginAttemptLimiter.isOtpBlocked(otpKey)) {
            throw new TooManyRequestsException(TOO_MANY_CODE_ATTEMPTS);
        }
        // Unknown email gets the same answer as a wrong code (no account enumeration).
        User user = userRepository.findByEmail(req.email().toLowerCase()).orElse(null);
        if (user == null) {
            loginAttemptLimiter.recordOtpFailure(otpKey);
            throw new BadRequestException("Invalid verification code");
        }

        if (user.isEmailVerified()) {
            throw new BadRequestException("Email is already verified");
        }
        if (user.getEmailVerificationOtp() == null
                || !user.getEmailVerificationOtp().equals(req.otp())) {
            loginAttemptLimiter.recordOtpFailure(otpKey);
            throw new BadRequestException("Invalid verification code");
        }
        if (LocalDateTime.now().isAfter(user.getEmailVerificationOtpExpiry())) {
            throw new BadRequestException("Verification code has expired. Request a new one");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationOtp(null);
        user.setEmailVerificationOtpExpiry(null);
        userRepository.save(user);
        loginAttemptLimiter.clearOtp(otpKey);

        log.info("Email verified for: {}", user.getEmail());
        return issueTokens(user);
    }

    // ── Resend OTP ────────────────────────────────────────────────────────────

    /** Always returns success regardless of whether the email is registered, already verified,
     *  or genuinely gets a fresh code -- same anti-enumeration guarantee as forgotPassword().
     *  Previously threw a 404 for an unregistered email and a distinct 400 for an
     *  already-verified one, letting anyone probe which emails have platform accounts. */
    @Transactional
    public void resendVerificationOtp(String email) {
        String normalized = TextNormalizer.normalizeEmail(email);

        if (!emailVerificationOtpRateLimiter.tryConsume(normalized)) {
            throw new TooManyRequestsException(
                    "Too many code requests for this email — max " + emailVerificationOtpRateLimiter.getMaxPerHour() +
                            " per hour. Please wait and try again.");
        }

        userRepository.findByEmail(normalized).ifPresent(user -> {
            if (user.isEmailVerified()) return;
            String otp = generateOtp();
            user.setEmailVerificationOtp(otp);
            user.setEmailVerificationOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
            userRepository.save(user);
            emailService.sendEmailVerificationOtp(user.getEmail(), user.getFirstName(), otp);
        });
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String rawUsername = req.username().trim();

        // Per-account throttle, checked BEFORE the credentials are evaluated so a locked key
        // reveals nothing about whether the guess was right.
        if (loginAttemptLimiter.isLoginBlocked(rawUsername)) {
            throw new TooManyRequestsException(TOO_MANY_LOGIN_ATTEMPTS);
        }

        // Resolve the account regardless of whether the user typed an email,
        // member ID (UW-YYYY-XXXX), or full name.
        User user;
        try {
            user = resolveUser(rawUsername);
        } catch (ResourceNotFoundException | IncorrectResultSizeDataAccessException e) {
            // The real reason stays visible to admins in the audit log; the caller only ever gets
            // the one generic message (no account enumeration, and an ambiguous name -- two members
            // sharing it -- fails the same way rather than as a 500).
            auditLogService.logUnknownAttempt("LOGIN_FAILED", rawUsername,
                    e instanceof ResourceNotFoundException
                            ? "Login attempt failed — no account matches \"" + rawUsername + "\""
                            : "Login attempt failed — \"" + rawUsername + "\" matches more than one account");
            loginAttemptLimiter.recordLoginFailure(rawUsername);
            throw new BadRequestException(GENERIC_LOGIN_FAILURE);
        }

        // Different aliases (email / member ID / name) of one account share a throttle too.
        String accountKey = user.getEmail();
        if (loginAttemptLimiter.isLoginBlocked(accountKey)) {
            throw new TooManyRequestsException(TOO_MANY_LOGIN_ATTEMPTS);
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), req.password())
            );
        } catch (AuthenticationException e) {
            auditLogService.log(user, "LOGIN_FAILED", "User", user.getId(),
                    "Login attempt failed — incorrect password or account not enabled");
            loginAttemptLimiter.recordLoginFailure(rawUsername);
            // Signing in with the email itself makes both keys the same bucket -- count it once, not twice.
            if (!accountKey.trim().equalsIgnoreCase(rawUsername.trim())) {
                loginAttemptLimiter.recordLoginFailure(accountKey);
            }
            // A suspended/unverified account is rejected by Spring before the password is even
            // looked at. Only tell the caller so if they also supplied the right password --
            // otherwise anyone could probe which accounts exist and are suspended.
            if ((e instanceof DisabledException || e instanceof LockedException)
                    && passwordEncoder.matches(req.password(), user.getPassword())) {
                throw e;
            }
            throw new BadRequestException(GENERIC_LOGIN_FAILURE);
        }

        loginAttemptLimiter.clearLogin(rawUsername);
        loginAttemptLimiter.clearLogin(accountKey);
        refreshTokenRepository.revokeAllUserTokens(user);
        AuthResponse response = issueTokens(user);
        auditLogService.log(user, "LOGIN_SUCCESS", "User", user.getId(), "Signed in successfully");
        return response;
    }

    // ── Magic login (onboarding "Continue Your Application" link) ──────────────

    private static final int ONBOARDING_LOGIN_TOKEN_MAX_USES = 5;

    @Transactional
    public AuthResponse magicLogin(MagicLoginRequest req) {
        User user = userRepository.findByOnboardingLoginToken(req.token())
                .orElseThrow(() -> new BadRequestException("This link is invalid or has already been used."));

        if (user.getOnboardingLoginTokenExpiry() == null
                || LocalDateTime.now().isAfter(user.getOnboardingLoginTokenExpiry())) {
            throw new BadRequestException("This link has expired. Please sign in with your email and password instead.");
        }

        // Tolerates a few false starts (closed tab, dropped connection) before fully
        // spending the link — capped rather than single-use or unlimited.
        int uses = user.getOnboardingLoginTokenUses() + 1;
        if (uses >= ONBOARDING_LOGIN_TOKEN_MAX_USES) {
            user.setOnboardingLoginToken(null);
            user.setOnboardingLoginTokenExpiry(null);
            user.setOnboardingLoginTokenUses(0);
        } else {
            user.setOnboardingLoginTokenUses(uses);
        }
        userRepository.save(user);

        refreshTokenRepository.revokeAllUserTokens(user);
        AuthResponse response = issueTokens(user);
        auditLogService.log(user, "LOGIN_SUCCESS", "User", user.getId(), "Signed in via onboarding magic link");
        return response;
    }

    // ── Resolve user from flexible username ───────────────────────────────────

    private User resolveUser(String raw) {
        // Collapse any runs of whitespace a user might type accidentally
        String input = raw.trim().replaceAll("\\s+", " ");

        // 1. Email
        if (input.contains("@")) {
            return userRepository.findByEmail(input.toLowerCase())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No account found for that email address. Check for a typo or use your member ID."));
        }
        // 2. Member ID (UW-YYYY-XXXX, case-insensitive)
        if (input.toUpperCase().matches("UW-\\d{4}-\\d{4}")) {
            return profileRepository.findByMemberId(input.toUpperCase())
                    .map(MemberProfile::getUser)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No account found for member ID " + input.toUpperCase()
                            + ". Contact the administrator if you believe this is an error."));
        }
        // 3. Full name — case-insensitive, accepts "First Last" or "Last First"
        return userRepository.findByFullNameIgnoreCase(input)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account found matching that name. Try your email or member ID (UW-YYYY-XXXX) instead."));
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (!stored.isValid()) {
            throw new BadRequestException("Refresh token has expired or been revoked. Please log in again");
        }

        User refreshUser = stored.getUser();
        MemberProfile refreshProfile = profileRepository.findByUser(refreshUser).orElse(null);
        String newAccessToken = jwtService.generateAccessToken(refreshUser);
        return AuthResponse.of(
                newAccessToken,
                stored.getToken(),
                jwtService.getAccessTokenExpiryMs() / 1000,
                UserProfileDto.from(refreshUser, refreshProfile)
        );
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null) return;
        refreshTokenRepository.findByToken(rawRefreshToken).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
            auditLogService.log(t.getUser(), "LOGOUT", "User", t.getUser().getId(), "Signed out");
        });
    }

    // ── Forgot Password ───────────────────────────────────────────────────────

    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        String email = req.email().toLowerCase();

        // Cap is keyed on the submitted email itself (not on whether it's registered)
        // so a flood of requests can't be used to distinguish real accounts from fake
        // ones — it always fails the same way regardless of existence.
        if (!passwordResetRateLimiter.tryConsume(email)) {
            throw new TooManyRequestsException(
                    "Too many reset requests for this email — max " + passwordResetRateLimiter.getMaxPerHour() +
                            " per hour. Please wait and try again."
            );
        }

        // Always return success to prevent email enumeration
        userRepository.findByEmail(email).ifPresent(user -> {
            String otp = generateOtp();
            user.setPasswordResetOtp(otp);
            user.setPasswordResetOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
            userRepository.save(user);
            emailService.sendPasswordResetOtp(user.getEmail(), user.getFirstName(), otp);
        });
    }

    // ── Reset Password ────────────────────────────────────────────────────────

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String otpKey = "reset:" + req.email().toLowerCase();
        if (loginAttemptLimiter.isOtpBlocked(otpKey)) {
            throw new TooManyRequestsException(TOO_MANY_CODE_ATTEMPTS);
        }
        // Unknown email is indistinguishable from a wrong code (no account enumeration).
        User user = userRepository.findByEmail(req.email().toLowerCase()).orElse(null);
        if (user == null || user.getPasswordResetOtp() == null || !user.getPasswordResetOtp().equals(req.otp())) {
            loginAttemptLimiter.recordOtpFailure(otpKey);
            throw new BadRequestException("Invalid or expired reset code");
        }
        if (LocalDateTime.now().isAfter(user.getPasswordResetOtpExpiry())) {
            throw new BadRequestException("Reset code has expired. Request a new one");
        }

        PasswordPolicy.validate(req.newPassword(), user.getEmail(), user.getFirstName(), user.getLastName());

        user.setPassword(passwordEncoder.encode(req.newPassword()));
        // They have now chosen their own password, so any pending "must set a password" is satisfied.
        user.setPasswordSetAt(LocalDateTime.now());
        user.setMustSetPassword(false);
        user.setPasswordResetOtp(null);
        user.setPasswordResetOtpExpiry(null);
        userRepository.save(user);
        loginAttemptLimiter.clearOtp(otpKey);

        // Revoke all refresh tokens after password change
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("Password reset for: {}", user.getEmail());
        auditLogService.log(user, "PASSWORD_RESET", "User", user.getId(), "Password reset via emailed code");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Fresh access + refresh token pair (persisted) for the given user. Used by ActivationService
     *  after it has revoked the old ones. */
    @Transactional
    public AuthResponse issueSession(User user) {
        return issueTokens(user);
    }

    /**
     * Marks a user's activation link as spent: every one-time secret is wiped, but the link's hash
     * is kept with a null expiry so the same link later reads as USED ("already used, sign in")
     * rather than as an unrecognisable INVALID one. The next issue() overwrites the hash.
     */
    static void markActivationConsumed(User user) {
        user.setActivationTokenExpiry(null);
        user.setActivationOtpHash(null);
        user.setActivationOtpExpiry(null);
        user.setActivationOtpAttempts(0);
        user.setActivationOtpLastSentAt(null);
        user.setActivationTicketHash(null);
        user.setActivationTicketExpiry(null);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(rawRefreshToken)
                .user(user)
                .expiresAt(LocalDateTime.now().plusNanos(refreshTokenExpiryMs * 1_000_000L))
                .build();
        refreshTokenRepository.save(refreshToken);

        MemberProfile profile = profileRepository.findByUser(user).orElse(null);
        return AuthResponse.of(
                accessToken,
                rawRefreshToken,
                jwtService.getAccessTokenExpiryMs() / 1000,
                UserProfileDto.from(user, profile)
        );
    }

    // ── Update credentials (email and/or password) ────────────────────────────

    @Transactional
    public void updateCredentials(UpdateCredentialsRequest req) {
        if ((req.newEmail() == null || req.newEmail().isBlank()) &&
            (req.newPassword() == null || req.newPassword().isBlank())) {
            throw new BadRequestException("Provide a new email, a new password, or both");
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = findByEmail(email);

        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        if (req.newEmail() != null && !req.newEmail().isBlank()) {
            String normalised = req.newEmail().trim().toLowerCase();
            if (!normalised.equals(user.getEmail()) && userRepository.existsByEmail(normalised)) {
                throw new ConflictException("An account with that email already exists");
            }
            user.setEmail(normalised);
        }

        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            if (passwordEncoder.matches(req.newPassword(), user.getPassword())) {
                throw new BadRequestException("New password must be different from the current password");
            }
            PasswordPolicy.validate(req.newPassword(), user.getEmail(), user.getFirstName(), user.getLastName());
            user.setPassword(passwordEncoder.encode(req.newPassword()));
            user.setPasswordSetAt(LocalDateTime.now());
            user.setMustSetPassword(false);
        }

        userRepository.save(user);
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("Credentials updated for user: {}", email);
        auditLogService.log(user, "CREDENTIALS_SELF_UPDATED", "User", user.getId(),
                "Updated their own login email and/or password");
    }

    // ── Change password (authenticated) ───────────────────────────────────────

    /**
     * Revokes every existing refresh token (so OTHER sessions must sign in again) but immediately
     * issues the caller a fresh pair, which the controller turns back into cookies -- so a password
     * change mid-onboarding no longer silently kills the caller's own session 15 minutes later.
     */
    @Transactional
    public AuthResponse changePassword(ChangePasswordRequest req) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = findByEmail(email);

        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw new BadRequestException("New password must be different from the current password");
        }
        PasswordPolicy.validate(req.newPassword(), user.getEmail(), user.getFirstName(), user.getLastName());

        user.setPassword(passwordEncoder.encode(req.newPassword()));
        user.setPasswordSetAt(LocalDateTime.now());
        user.setMustSetPassword(false);
        userRepository.save(user);

        refreshTokenRepository.revokeAllUserTokens(user);
        AuthResponse session = issueTokens(user);
        log.info("Password changed for user: {}", email);
        auditLogService.log(user, "PASSWORD_CHANGED", "User", user.getId(), "Changed their own password");
        return session;
    }

    // ── Set initial password (no current password -- for an account that never chose one) ──────

    @Transactional
    public AuthResponse setInitialPassword(SetInitialPasswordRequest req) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = findByEmail(email);

        if (!user.isMustSetPassword()) {
            throw new BadRequestException("Your password is already set — use Change Password instead.");
        }
        PasswordPolicy.validate(req.newPassword(), user.getEmail(), user.getFirstName(), user.getLastName());

        user.setPassword(passwordEncoder.encode(req.newPassword()));
        user.setPasswordSetAt(LocalDateTime.now());
        user.setMustSetPassword(false);
        markActivationConsumed(user); // any still-pending setup link/code is now moot
        userRepository.save(user);

        refreshTokenRepository.revokeAllUserTokens(user);
        AuthResponse session = issueTokens(user);
        auditLogService.log(user, "PASSWORD_SET_INITIAL", "User", user.getId(),
                "Chose their own password (no current password required)");
        return session;
    }

    // ── Admin panel entry step-up ────────────────────────────────────────────

    /** Emails the current user a fresh admin-entry OTP — called the first time a session
     *  moves from the member portal into /admin. */
    @Transactional
    public void requestAdminEntryOtp() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = findByEmail(email);

        String otp = generateOtp();
        user.setAdminEntryOtp(otp);
        user.setAdminEntryOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        userRepository.save(user);
        emailService.sendAdminEntryOtp(user.getEmail(), user.getFirstName(), otp);
    }

    /** Verifies the code from requestAdminEntryOtp(). Only clears the stored OTP on success or
     *  genuine expiry -- previously cleared it unconditionally on every call, so a single
     *  mistyped digit permanently burned an otherwise-valid code and the error message gave
     *  no indication a fresh Resend was now required instead of just retyping it. */
    @Transactional
    public void verifyAdminEntryOtp(String otp) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = findByEmail(email);

        String storedOtp = user.getAdminEntryOtp();
        LocalDateTime expiry = user.getAdminEntryOtpExpiry();

        if (storedOtp == null) {
            throw new BadRequestException("No confirmation code was requested, or it was already used. Request a new one.");
        }
        if (expiry == null || LocalDateTime.now().isAfter(expiry)) {
            user.setAdminEntryOtp(null);
            user.setAdminEntryOtpExpiry(null);
            userRepository.save(user);
            auditLogService.log(user, "ADMIN_ENTRY_DENIED", "User", user.getId(),
                    "Admin panel entry denied — confirmation code expired");
            throw new BadRequestException("Confirmation code has expired. Request a new one.");
        }
        if (!storedOtp.equals(otp)) {
            auditLogService.log(user, "ADMIN_ENTRY_DENIED", "User", user.getId(),
                    "Admin panel entry denied — incorrect confirmation code");
            throw new BadRequestException("Incorrect code. Please try again.");
        }

        user.setAdminEntryOtp(null);
        user.setAdminEntryOtpExpiry(null);
        userRepository.save(user);
        auditLogService.log(user, "ADMIN_ENTRY_GRANTED", "User", user.getId(), "Entered the admin panel");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User findByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));
    }

    private String generateOtp() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }
}
