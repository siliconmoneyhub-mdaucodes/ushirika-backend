package com.mdau.ushirika.module.auth.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.dto.ActivationLookupDto;
import com.mdau.ushirika.module.auth.dto.ActivationTicketDto;
import com.mdau.ushirika.module.auth.dto.AuthResponse;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.RefreshTokenRepository;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.module.member.repository.MembershipApplicationRepository;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.common.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Account activation: replaces the emailed temporary password. An applicant (or an admin-created
 * member) gets ONE email holding a setup link (long random token) plus a 6-digit confirmation code,
 * proves they own the inbox by entering the code, then chooses their own password.
 *
 * Everything secret is stored only as a SHA-256 digest: the link token, the code (salted with the
 * user id so it is not a 10^6-entry rainbow-table lookup) and the short-lived "ticket" handed back
 * after a correct code. Must not depend on MembershipService (it depends on this class).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivationService {

    public static final int TOKEN_TTL_HOURS = 72;
    public static final int OTP_TTL_MINUTES = 15;
    public static final int TICKET_TTL_MINUTES = 15;
    public static final int MAX_OTP_ATTEMPTS = 5;
    public static final int RESEND_COOLDOWN_SECONDS = 60;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MembershipApplicationRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final ActivationRateLimiter rateLimiter;
    private final AuthService authService;

    @org.springframework.beans.factory.annotation.Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl = "https://ushirikacommunity.site";

    /** Raw (unhashed) secrets -- returned once, to be emailed, and never stored. */
    public record IssuedActivation(String rawToken, String rawOtp,
                                   LocalDateTime tokenExpiry, LocalDateTime otpExpiry) {}

    // -- Issue ---------------------------------------------------------------------------

    /** Issues (or re-issues) a token + OTP pair. Does NOT touch the user's password. */
    @Transactional
    public IssuedActivation issue(User user) {
        LocalDateTime now = LocalDateTime.now();
        String rawToken = randomToken();
        String rawOtp = randomOtp();
        LocalDateTime tokenExpiry = now.plusHours(TOKEN_TTL_HOURS);
        LocalDateTime otpExpiry = now.plusMinutes(OTP_TTL_MINUTES);

        user.setActivationTokenHash(sha256Hex(rawToken));
        user.setActivationTokenExpiry(tokenExpiry);
        user.setActivationOtpHash(hashOtp(rawOtp, user));
        user.setActivationOtpExpiry(otpExpiry);
        user.setActivationOtpAttempts(0);
        user.setActivationOtpLastSentAt(now);
        user.setActivationTicketHash(null);
        user.setActivationTicketExpiry(null);
        userRepository.save(user);
        return new IssuedActivation(rawToken, rawOtp, tokenExpiry, otpExpiry);
    }

    // -- Lookup --------------------------------------------------------------------------

    /** Never throws: an unknown/garbled token is simply INVALID (never a 404, never distinguishable). */
    @Transactional(readOnly = true)
    public ActivationLookupDto lookup(String rawToken) {
        Optional<User> found = findByToken(rawToken);
        if (found.isEmpty()) return ActivationLookupDto.of("INVALID");
        User user = found.get();
        if (!user.isActive()) return ActivationLookupDto.of("INVALID");

        // A consumed link keeps its hash but loses its expiry -- see markConsumed().
        if (user.getActivationTokenExpiry() == null) return ActivationLookupDto.of("USED");
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(user.getActivationTokenExpiry())) return ActivationLookupDto.of("EXPIRED");
        if (user.getActivationOtpAttempts() >= MAX_OTP_ATTEMPTS) return ActivationLookupDto.of("LOCKED");

        LocalDateTime resendAt = user.getActivationOtpLastSentAt() == null
                ? now
                : user.getActivationOtpLastSentAt().plusSeconds(RESEND_COOLDOWN_SECONDS);
        return new ActivationLookupDto(
                "VALID",
                user.getFirstName(),
                maskEmail(user.getEmail()),
                toInstant(user.getActivationOtpExpiry()),
                toInstant(resendAt),
                Math.max(0, MAX_OTP_ATTEMPTS - user.getActivationOtpAttempts()));
    }

    // -- Verify --------------------------------------------------------------------------

    /**
     * Consumes one attempt. A wrong code increments the counter and the 5th wrong one locks the
     * link. The failure paths throw, so noRollbackFor is essential: without it the increment would
     * be rolled back together with the exception and the limit would never bite.
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public ActivationTicketDto verify(String rawToken, String otp) {
        User user = findByToken(rawToken).filter(User::isActive)
                .orElseThrow(() -> new BadRequestException(
                        "This setup link isn't valid. Please request a new one."));

        LocalDateTime now = LocalDateTime.now();
        if (user.getActivationTokenExpiry() == null) {
            throw new BadRequestException("This setup link has already been used. Please sign in instead.");
        }
        if (now.isAfter(user.getActivationTokenExpiry())) {
            throw new BadRequestException("This setup link has expired. Please request a new one.");
        }
        if (user.getActivationOtpAttempts() >= MAX_OTP_ATTEMPTS) {
            throw new BadRequestException(
                    "Too many incorrect codes. For your security this link is locked -- please request a new one.");
        }
        if (user.getActivationOtpHash() == null || user.getActivationOtpExpiry() == null
                || now.isAfter(user.getActivationOtpExpiry())) {
            throw new BadRequestException("That code has expired. Request a new code.");
        }

        String candidate = hashOtp(otp == null ? "" : otp.trim(), user);
        if (!constantTimeEquals(candidate, user.getActivationOtpHash())) {
            int attempts = user.getActivationOtpAttempts() + 1;
            user.setActivationOtpAttempts(attempts);
            if (attempts >= MAX_OTP_ATTEMPTS) {
                // Burn the code as well so it can't be tried again through any other path.
                user.setActivationOtpHash(null);
                user.setActivationOtpExpiry(null);
            }
            userRepository.save(user);
            auditLogService.log(user, "ACTIVATION_CODE_FAILED", "User", user.getId(),
                    "Incorrect account-setup confirmation code (attempt " + attempts + " of " + MAX_OTP_ATTEMPTS + ")");
            if (attempts >= MAX_OTP_ATTEMPTS) {
                throw new BadRequestException(
                        "Too many incorrect codes. For your security this link is now locked -- please request a new one.");
            }
            int left = MAX_OTP_ATTEMPTS - attempts;
            throw new BadRequestException("That code isn't right. " + left + (left == 1 ? " attempt" : " attempts") + " left.");
        }

        // Correct: the code is single-use; hand back a short-lived ticket for the password step.
        String rawTicket = randomToken();
        LocalDateTime ticketExpiry = now.plusMinutes(TICKET_TTL_MINUTES);
        user.setActivationOtpHash(null);
        user.setActivationOtpExpiry(null);
        user.setActivationOtpAttempts(0);
        user.setActivationTicketHash(sha256Hex(rawTicket));
        user.setActivationTicketExpiry(ticketExpiry);
        userRepository.save(user);
        return new ActivationTicketDto(rawTicket, toInstant(ticketExpiry));
    }

    // -- Complete ------------------------------------------------------------------------

    /**
     * Sets the password, spends every activation secret, verifies the email, stamps the
     * application's email-reverified marker (so the wizard skips its old account-setup step),
     * revokes every existing refresh token and returns a fresh session for the caller to turn
     * into cookies. The ticket is single-use.
     */
    @Transactional
    public AuthResponse complete(String rawTicket, String newPassword) {
        User user = findByTicket(rawTicket)
                .filter(User::isActive)
                .filter(u -> u.getActivationTicketExpiry() != null
                        && !LocalDateTime.now().isAfter(u.getActivationTicketExpiry()))
                .orElseThrow(() -> new BadRequestException(
                        "This setup session expired. Please open your setup link again."));

        // Validated BEFORE any mutation so a rejected password leaves the ticket usable for a retry.
        PasswordPolicy.validate(newPassword, user.getEmail(), user.getFirstName(), user.getLastName());

        LocalDateTime now = LocalDateTime.now();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordSetAt(now);
        user.setMustSetPassword(false);
        user.setEmailVerified(true);
        AuthService.markActivationConsumed(user);
        userRepository.save(user);

        applicationRepository.findByUser(user).ifPresent(app -> markApplicationReverified(app, now));

        refreshTokenRepository.revokeAllUserTokens(user);
        AuthResponse session = authService.issueSession(user);
        auditLogService.log(user, "ACCOUNT_SETUP_COMPLETED", "User", user.getId(),
                "Set their own password via the account setup link");
        log.info("Account setup completed for {}", user.getEmail());
        return session;
    }

    private void markApplicationReverified(MembershipApplication app, LocalDateTime now) {
        if (app.getEmailReverifiedAt() == null) {
            app.setEmailReverifiedAt(now);
        }
        if (app.getStatus() == ApplicationStatus.FORM_SENT) {
            app.setStatus(ApplicationStatus.ONBOARDING_IN_PROGRESS);
        }
        applicationRepository.save(app);
    }

    // -- Resend (code only) ----------------------------------------------------------------

    /** 60s cooldown + 5/hour. Silent no-op on every failure path -- the controller always answers 200. */
    @Transactional
    public void resend(String rawToken) {
        Optional<User> found = findByToken(rawToken).filter(User::isActive);
        if (found.isEmpty()) return;
        User user = found.get();

        LocalDateTime now = LocalDateTime.now();
        if (user.getActivationTokenExpiry() == null || now.isAfter(user.getActivationTokenExpiry())) return;
        if (user.getActivationOtpAttempts() >= MAX_OTP_ATTEMPTS) return; // locked: needs a brand-new link
        if (user.getActivationOtpLastSentAt() != null
                && now.isBefore(user.getActivationOtpLastSentAt().plusSeconds(RESEND_COOLDOWN_SECONDS))) return;
        if (!rateLimiter.tryConsumeResend(user.getId())) {
            log.warn("Activation code resend cap reached for user {}", user.getId());
            return;
        }

        String rawOtp = randomOtp();
        user.setActivationOtpHash(hashOtp(rawOtp, user));
        user.setActivationOtpExpiry(now.plusMinutes(OTP_TTL_MINUTES));
        // A fresh code is a fresh 10^6 search space, so the wrong-guess counter restarts. Abuse is
        // bounded by the 60s cooldown, the 5/hour cap above and the per-IP verify cap.
        user.setActivationOtpAttempts(0);
        user.setActivationOtpLastSentAt(now);
        user.setActivationTicketHash(null);
        user.setActivationTicketExpiry(null);
        userRepository.save(user);
        emailService.sendActivationCodeOnly(user.getEmail(), user.getFirstName(), rawOtp);
    }

    // -- Request a new link by email ----------------------------------------------------------

    /**
     * Anti-enumeration: always returns normally. Re-issues only for an APPLICANT whose application is
     * FORM_SENT or ONBOARDING_IN_PROGRESS, or for any active user still flagged mustSetPassword.
     */
    @Transactional
    public void requestByEmail(String email) {
        if (email == null || email.isBlank()) return;
        Optional<User> found = userRepository.findByEmail(TextNormalizer.normalizeEmail(email)).filter(User::isActive);
        if (found.isEmpty()) return;
        User user = found.get();
        if (!isEligibleForActivation(user)) return;

        LocalDateTime now = LocalDateTime.now();
        if (user.getActivationOtpLastSentAt() != null
                && now.isBefore(user.getActivationOtpLastSentAt().plusSeconds(RESEND_COOLDOWN_SECONDS))) return;
        if (!rateLimiter.tryConsumeResend(user.getId())) {
            log.warn("Activation link request cap reached for user {}", user.getId());
            return;
        }

        boolean returning = user.getPasswordSetAt() != null;
        IssuedActivation issued = issue(user);
        String url = siteUrl + "/activate?t=" + issued.rawToken();
        emailService.sendActivationInvite(user.getEmail(), user.getFirstName(), url, issued.rawOtp(),
                TOKEN_TTL_HOURS, returning);
    }

    private boolean isEligibleForActivation(User user) {
        if (user.isMustSetPassword()) return true;
        if (user.getRole() != UserRole.APPLICANT) return false;
        return applicationRepository.findByUser(user)
                .map(a -> a.getStatus() == ApplicationStatus.FORM_SENT
                        || a.getStatus() == ApplicationStatus.ONBOARDING_IN_PROGRESS)
                .orElse(false);
    }

    // -- Helpers ---------------------------------------------------------------------------

    private Optional<User> findByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        return userRepository.findByActivationTokenHash(sha256Hex(rawToken.trim()));
    }

    private Optional<User> findByTicket(String rawTicket) {
        if (rawTicket == null || rawTicket.isBlank()) return Optional.empty();
        return userRepository.findByActivationTicketHash(sha256Hex(rawTicket.trim()));
    }

    /** "brian@gmail.com" -> "b***n@gmail.com" (bullet characters). */
    static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0) return email;
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) return local.charAt(0) + "•••" + domain;
        return local.charAt(0) + "•".repeat(Math.min(5, local.length() - 2))
                + local.charAt(local.length() - 1) + domain;
    }

    private static Instant toInstant(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).toInstant();
    }

    /** 256 bits of randomness as 64 hex characters -- URL safe. */
    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String randomOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** Package-visible so tests can build a matching code hash. */
    static String hashOtp(String otp, User user) {
        return sha256Hex(otp + ":" + user.getId());
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
