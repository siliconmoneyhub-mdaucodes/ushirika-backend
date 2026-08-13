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

    /** Sent when admin clicks "Send Form" — applicant's onboarding login credentials. */
    void sendFormSentCredentials(String toEmail, String name, String tempPassword, String onboardingUrl);

    /** Sent when an applicant's role flips from APPLICANT to MEMBER after registration fee verification. */
    void sendMembershipApproved(String toEmail, String name, String memberId);

    void sendPlain(String toEmail, String toName, String subject, String htmlBody);
}
