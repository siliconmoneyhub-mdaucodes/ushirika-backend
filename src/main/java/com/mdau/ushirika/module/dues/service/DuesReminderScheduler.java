package com.mdau.ushirika.module.dues.service;

import com.mdau.ushirika.module.dues.entity.MembershipDue;
import com.mdau.ushirika.module.dues.enums.DuesStatus;
import com.mdau.ushirika.module.dues.repository.MembershipDueRepository;
import com.mdau.ushirika.module.notification.enums.InAppNotificationCategory;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import com.mdau.ushirika.module.notification.service.NotificationCategory;
import com.mdau.ushirika.module.notification.service.NotificationDispatcher;
import com.mdau.ushirika.module.notification.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sends membership dues renewal reminders at 60 / 30 / 14 / 7 days before
 * the due date and on the due date itself, via Email, SMS, and in-app.
 * Runs daily at 07:00 UTC.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DuesReminderScheduler {

    private static final int[] REMINDER_DAYS = {60, 30, 14, 7, 0};
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final MembershipDueRepository dueRepository;
    private final EmailService            emailService;
    private final SmsService              smsService;
    private final NotificationDispatcher  notificationDispatcher;
    private final InAppNotificationService notificationService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    @Scheduled(cron = "0 0 7 * * *")
    @Transactional(readOnly = true)
    public void sendDuesReminders() {
        LocalDate today = LocalDate.now();
        int sent = 0;

        for (int days : REMINDER_DAYS) {
            LocalDate targetDate = today.plusDays(days);
            List<MembershipDue> dues =
                    dueRepository.findByStatusAndDueDate(DuesStatus.PENDING, targetDate);

            for (MembershipDue due : dues) {
                var user = due.getUser();
                String formattedDate = due.getDueDate().format(DATE_FMT);
                String subject = days == 0
                        ? "Your Ushirika membership dues are due TODAY"
                        : "Ushirika membership dues due in " + days + " day" + (days == 1 ? "" : "s");
                String message = buildMessage(user.getFullName(), due.getYear(), formattedDate, days);

                try { emailService.sendPlain(user.getEmail(), user.getFullName(), subject, toHtml(message)); }
                catch (Exception e) { log.warn("Dues email failed for {}: {}", user.getEmail(), e.getMessage()); }

                if (user.getPhone() != null) {
                    try { smsService.send(user.getPhone(), user.getFullName(), subject + "\n" + message); }
                    catch (Exception e) { log.warn("Dues SMS failed for {}: {}", user.getPhone(), e.getMessage()); }
                }

                notificationDispatcher.dispatchWhatsApp(NotificationCategory.PAYMENT_REMINDER,
                        user.getPhone(), user.getFullName(), List.of(
                                user.getFullName(),
                                "annual membership dues",
                                "100.00",
                                formattedDate,
                                siteUrl + "/portal/payments"
                        ));

                notificationService.createForUser(
                        user.getId(),
                        InAppNotificationCategory.DUES_REMINDER,
                        subject,
                        message,
                        "/portal/payments"
                );
                sent++;
            }
        }
        if (sent > 0) log.info("DuesReminderScheduler: sent {} reminder(s)", sent);
    }

    private static String buildMessage(String name, int year, String dueDate, int days) {
        if (days == 0) {
            return String.format(
                "Hi %s, your %d Ushirika Welfare Organization annual membership dues ($100) are due TODAY (%s). " +
                "Please make your payment as soon as possible to maintain your active status. " +
                "Log in to your member portal to pay: https://ushirikacommunity.site/portal/payments",
                name, year, dueDate);
        }
        return String.format(
            "Hi %s, your %d Ushirika Welfare Organization annual membership dues ($100) are due on %s — that's %d day%s from now. " +
            "Stay active by paying before the due date. " +
            "Log in to pay: https://ushirikacommunity.site/portal/payments",
            name, year, dueDate, days, days == 1 ? "" : "s");
    }

    private static String toHtml(String text) {
        return """
            <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
              <h2 style="color:#007834">Ushirika Welfare Organization — Dues Reminder</h2>
              <p>%s</p>
              <p style="color:#888;font-size:12px">
                To stop receiving these reminders, pay your dues at
                <a href="https://ushirikacommunity.site/portal/payments">ushirikacommunity.site</a>.
              </p>
            </div>
            """.formatted(text.replace("\n", "<br/>"));
    }
}
