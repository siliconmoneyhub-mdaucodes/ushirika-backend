package com.mdau.ushirika.module.attendance.service;

import com.mdau.ushirika.module.attendance.entity.Fine;
import com.mdau.ushirika.module.attendance.enums.FineStatus;
import com.mdau.ushirika.module.attendance.repository.FineRepository;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sends fine payment reminders at 7 / 3 days before the due date and on the
 * due date. The initial notification on fine creation is handled by FineService.
 * Runs daily at 07:30 UTC.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FineReminderScheduler {

    private static final int[] REMINDER_DAYS = {7, 3, 0};
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final FineRepository           fineRepository;
    private final EmailService             emailService;
    private final SmsService               smsService;
    private final NotificationDispatcher   notificationDispatcher;
    private final InAppNotificationService notificationService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    @Scheduled(cron = "0 30 7 * * *")
    public void sendFineReminders() {
        LocalDate today = LocalDate.now();
        int sent = 0;

        for (int days : REMINDER_DAYS) {
            LocalDate targetDate = today.plusDays(days);
            List<Fine> fines = fineRepository.findByStatusAndDueDate(FineStatus.PENDING, targetDate);

            for (Fine fine : fines) {
                var user = fine.getUser();
                String formattedDue = fine.getDueDate().format(DATE_FMT);
                String subject = days == 0
                        ? "Fine payment due TODAY — $" + fine.getAmount()
                        : "Fine payment due in " + days + " day" + (days == 1 ? "" : "s") + " — $" + fine.getAmount();
                String body = buildBody(user.getFullName(), fine, formattedDue, days);

                try { emailService.sendPlain(user.getEmail(), user.getFullName(), subject, toHtml(fine, formattedDue, days)); }
                catch (Exception e) { log.warn("Fine email failed for {}: {}", user.getEmail(), e.getMessage()); }

                if (user.getPhone() != null) {
                    try { smsService.send(user.getPhone(), user.getFullName(), subject + "\n" + body); }
                    catch (Exception e) { log.warn("Fine SMS failed for {}: {}", user.getPhone(), e.getMessage()); }
                }

                notificationDispatcher.dispatchWhatsApp(NotificationCategory.PAYMENT_REMINDER,
                        user.getPhone(), user.getFullName(), List.of(
                                user.getFullName(),
                                "fine (" + fine.getReason() + ")",
                                fine.getAmount().toPlainString(),
                                formattedDue,
                                siteUrl + "/portal/meetings"
                        ));

                notificationService.createForUser(
                        user.getId(),
                        InAppNotificationCategory.FINE,
                        subject,
                        body,
                        "/portal/meetings"
                );
                sent++;
            }
        }
        if (sent > 0) log.info("FineReminderScheduler: sent {} reminder(s)", sent);
    }

    private static String buildBody(String name, Fine fine, String formattedDue, int days) {
        String when = days == 0 ? "TODAY" : "in " + days + " day" + (days == 1 ? "" : "s");
        return String.format(
            "Hi %s, you have an outstanding fine of $%s for \"%s\" due %s (%s). " +
            "Please arrange payment to avoid additional penalties. " +
            "View your fine history: https://ushirikacommunity.site/portal/meetings",
            name, fine.getAmount(), fine.getReason(), when, formattedDue);
    }

    private static String toHtml(Fine fine, String formattedDue, int days) {
        String when = days == 0 ? "TODAY" : "in " + days + " day" + (days == 1 ? "" : "s");
        return """
            <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
              <h2 style="color:#C0392B">Outstanding Fine — Payment Reminder</h2>
              <table style="width:100%;border-collapse:collapse;margin:16px 0">
                <tr><td style="padding:8px;color:#666">Reason</td><td style="padding:8px;font-weight:600">%s</td></tr>
                <tr style="background:#f9f9f9"><td style="padding:8px;color:#666">Amount</td><td style="padding:8px;font-weight:600;color:#C0392B">$%s</td></tr>
                <tr><td style="padding:8px;color:#666">Due Date</td><td style="padding:8px;font-weight:600">%s</td></tr>
                <tr style="background:#f9f9f9"><td style="padding:8px;color:#666">Status</td><td style="padding:8px;font-weight:700;color:#C0392B">DUE %s</td></tr>
              </table>
              <p>Please arrange payment to avoid additional penalties.</p>
              <p><a href="https://ushirikacommunity.site/portal/meetings"
                    style="background:#007834;color:#fff;padding:10px 20px;border-radius:8px;text-decoration:none">
                View Fine Details
              </a></p>
            </div>
            """.formatted(fine.getReason(), fine.getAmount(), formattedDue, when.toUpperCase());
    }
}
