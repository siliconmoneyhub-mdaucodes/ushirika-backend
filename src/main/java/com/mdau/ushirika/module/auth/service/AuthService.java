package com.mdau.ushirika.module.auth.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.exception.TooManyRequestsException;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    private static final int OTP_EXPIRY_MINUTES = 15;

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public void register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("An account with this email already exists");
        }
        if (userRepository.existsByPhone(req.phone())) {
            throw new ConflictException("An account with this phone number already exists");
        }

        String otp = generateOtp();
        User user = User.builder()
                .firstName(req.firstName())
                .lastName(req.lastName())
                .email(req.email().toLowerCase())
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
        User user = findByEmail(req.email());

        if (user.isEmailVerified()) {
            throw new BadRequestException("Email is already verified");
        }
        if (user.getEmailVerificationOtp() == null
                || !user.getEmailVerificationOtp().equals(req.otp())) {
            throw new BadRequestException("Invalid verification code");
        }
        if (LocalDateTime.now().isAfter(user.getEmailVerificationOtpExpiry())) {
            throw new BadRequestException("Verification code has expired. Request a new one");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationOtp(null);
        user.setEmailVerificationOtpExpiry(null);
        userRepository.save(user);

        log.info("Email verified for: {}", user.getEmail());
        return issueTokens(user);
    }

    // ── Resend OTP ────────────────────────────────────────────────────────────

    @Transactional
    public void resendVerificationOtp(String email) {
        User user = findByEmail(email);
        if (user.isEmailVerified()) {
            throw new BadRequestException("Email is already verified");
        }
        String otp = generateOtp();
        user.setEmailVerificationOtp(otp);
        user.setEmailVerificationOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        userRepository.save(user);
        emailService.sendEmailVerificationOtp(user.getEmail(), user.getFirstName(), otp);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest req) {
        // Resolve the account regardless of whether the user typed an email,
        // member ID (UW-YYYY-XXXX), or full name.
        User user = resolveUser(req.username().trim());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), req.password())
        );
        refreshTokenRepository.revokeAllUserTokens(user);
        return issueTokens(user);
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
        return issueTokens(user);
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
    public AuthResponse refresh(RefreshTokenRequest req) {
        RefreshToken stored = refreshTokenRepository.findByToken(req.refreshToken())
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
    public void logout(RefreshTokenRequest req) {
        refreshTokenRepository.findByToken(req.refreshToken()).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
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
        User user = findByEmail(req.email());

        if (user.getPasswordResetOtp() == null || !user.getPasswordResetOtp().equals(req.otp())) {
            throw new BadRequestException("Invalid or expired reset code");
        }
        if (LocalDateTime.now().isAfter(user.getPasswordResetOtpExpiry())) {
            throw new BadRequestException("Reset code has expired. Request a new one");
        }

        user.setPassword(passwordEncoder.encode(req.newPassword()));
        user.setPasswordResetOtp(null);
        user.setPasswordResetOtpExpiry(null);
        userRepository.save(user);

        // Revoke all refresh tokens after password change
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("Password reset for: {}", user.getEmail());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
            user.setPassword(passwordEncoder.encode(req.newPassword()));
        }

        userRepository.save(user);
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("Credentials updated for user: {}", email);
    }

    // ── Change password (authenticated) ───────────────────────────────────────

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = findByEmail(email);

        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw new BadRequestException("New password must be different from the current password");
        }

        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        // Revoke all existing refresh tokens so other sessions must re-login
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("Password changed for user: {}", email);
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
