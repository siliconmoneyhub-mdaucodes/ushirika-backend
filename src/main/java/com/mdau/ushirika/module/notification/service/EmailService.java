package com.mdau.ushirika.module.notification.service;

/**
 * Pluggable email abstraction. Implemented by BrevoEmailService.
 * In dev, falls back to logging when API key is not configured.
 */
public interface EmailService {

    void sendEmailVerificationOtp(String toEmail, String name, String otp);

    void sendPasswordResetOtp(String toEmail, String name, String otp);

    /** Step-up confirmation code before an admin-tier user's portal session enters /admin. */
    void sendAdminEntryOtp(String toEmail, String name, String otp);

    void sendWelcome(String toEmail, String name, String memberId);

    /**
     * Sent when admin clicks "Send Form" — applicant's onboarding login credentials.
     * @deprecated a password is never emailed any more; use {@link #sendActivationInvite}.
     */
    @Deprecated
    void sendFormSentCredentials(String toEmail, String name, String tempPassword, String onboardingUrl);

    /**
     * The single account-setup email: a setup link plus a 6-digit confirmation code. Never contains a
     * password. {@code returning == true} switches to "continue where you left off" copy for an
     * applicant who already chose a password / has progress (admin "Resend").
     */
    void sendActivationInvite(String toEmail, String name, String activationUrl, String otp,
                              int expiryHours, boolean returning);

    /** Same as the 6-arg form with {@code returning = false} (first-time setup copy). */
    void sendActivationInvite(String toEmail, String name, String activationUrl, String otp, int expiryHours);

    /** Setup invite for an admin-created, already-approved member (no onboarding steps to mention). */
    void sendMemberActivationInvite(String toEmail, String name, String memberId,
                                    String activationUrl, String otp, int expiryHours);

    /** Fresh confirmation code only, for the "resend code" button on the setup page. */
    void sendActivationCodeOnly(String toEmail, String name, String otp);

    /** Sent when an applicant's role flips from APPLICANT to MEMBER after registration fee verification. */
    void sendMembershipApproved(String toEmail, String name, String memberId);

    void sendPlain(String toEmail, String toName, String subject, String htmlBody);
}
