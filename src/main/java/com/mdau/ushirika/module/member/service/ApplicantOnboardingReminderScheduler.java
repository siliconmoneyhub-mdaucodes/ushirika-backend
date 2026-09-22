package com.mdau.ushirika.module.member.service;

import com.mdau.ushirika.common.util.AppClock;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.module.member.repository.MembershipApplicationRepository;
import com.mdau.ushirika.module.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Daily nudge for applicants sitting at FORM_SENT or ONBOARDING_IN_PROGRESS who haven't finished
 * onboarding -- they have real login credentials and a form waiting, but never came back to
 * complete it. Capped at once per applicant per ~24h via onboardingReminderSentAt regardless of
 * how often this fires, same guard shape as MeetingUpcomingReminderScheduler's "sent" flags.
 * Runs indefinitely (no reminder-count cap) until the applicant finishes onboarding or the
 * application is voided/rejected, since neither status is in the target set below.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicantOnboardingReminderScheduler {

    private static final List<ApplicationStatus> TARGET_STATUSES =
            List.of(ApplicationStatus.FORM_SENT, ApplicationStatus.ONBOARDING_IN_PROGRESS);

    private final MembershipApplicationRepository applicationRepository;
    private final EmailService emailService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    @Scheduled(cron = "0 0 9 * * *", zone = "America/Chicago")
    @Transactional
    public void sendReminders() {
        LocalDateTime now = AppClock.now();
        LocalDateTime cutoff = now.minusHours(23);

        List<MembershipApplication> candidates = applicationRepository.findByStatusIn(TARGET_STATUSES).stream()
                .filter(a -> a.getUser() != null)
                .filter(a -> a.getOnboardingReminderSentAt() == null || a.getOnboardingReminderSentAt().isBefore(cutoff))
                .toList();

        int sent = 0;
        for (MembershipApplication application : candidates) {
            User applicant = application.getUser();
            try {
                emailService.sendPlain(applicant.getEmail(), applicant.getFullName(),
                        "Finish setting up your Ushirika membership",
                        toHtml(applicant.getFirstName()));
                sent++;
            } catch (Exception e) {
                log.warn("Onboarding-reminder email failed for {}: {}", applicant.getEmail(), e.getMessage());
                continue; // don't mark as sent if it actually failed -- retried tomorrow
            }
            application.setOnboardingReminderSentAt(now);
            applicationRepository.save(application);
        }

        if (sent > 0) log.info("ApplicantOnboardingReminderScheduler: sent {} reminder(s)", sent);
    }

    private String toHtml(String firstName) {
        return """
            <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
              <h2 style="color:#007834">You're almost a member!</h2>
              <p>Hi %s,</p>
              <p>We noticed you started your Ushirika Welfare Organization onboarding but haven't
              finished yet. It only takes a few minutes to complete the remaining steps.</p>
              <p><a href="%s/login" style="display:inline-block;background:#007834;color:#fff;
                 padding:10px 20px;border-radius:24px;text-decoration:none;font-weight:600">
                 Continue My Application
              </a></p>
              <p style="color:#666;font-size:13px">Log in with the email and temporary password we
              sent you when your form was first issued. If you've lost that email, contact us and
              we'll help you back in.</p>
            </div>
            """.formatted(firstName, siteUrl);
    }
}
