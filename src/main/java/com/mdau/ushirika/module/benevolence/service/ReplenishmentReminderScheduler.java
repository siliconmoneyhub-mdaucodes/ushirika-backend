package com.mdau.ushirika.module.benevolence.service;

import com.mdau.ushirika.module.benevolence.entity.BenevolenceReplenishment;
import com.mdau.ushirika.module.benevolence.entity.ReplenishmentPayment;
import com.mdau.ushirika.module.benevolence.enums.ReplenishmentPaymentStatus;
import com.mdau.ushirika.module.benevolence.enums.ReplenishmentStatus;
import com.mdau.ushirika.module.benevolence.repository.BenevolenceReplenishmentRepository;
import com.mdau.ushirika.module.benevolence.repository.ReplenishmentPaymentRepository;
import com.mdau.ushirika.module.notification.enums.InAppNotificationCategory;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import com.mdau.ushirika.module.notification.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Sends benevolence replenishment reminders at 14 / 7 days before the due date
 * and on the due date itself. The immediate notification on replenishment creation
 * is handled by BenevolenceService.
 * Runs daily at 08:30 UTC.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplenishmentReminderScheduler {

    private static final Set<Long> REMINDER_THRESHOLDS = Set.of(14L, 7L, 0L);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final BenevolenceReplenishmentRepository replenishmentRepository;
    private final ReplenishmentPaymentRepository     paymentRepository;
    private final EmailService                       emailService;
    private final SmsService                         smsService;
    private final InAppNotificationService           notificationService;

    @Scheduled(cron = "0 30 8 * * *")
    public void sendReplenishmentReminders() {
        LocalDate today = LocalDate.now();
        List<BenevolenceReplenishment> active =
                replenishmentRepository.findAllByStatus(ReplenishmentStatus.ACTIVE);
        int sent = 0;

        for (BenevolenceReplenishment replenishment : active) {
            long daysUntilDue = ChronoUnit.DAYS.between(today, replenishment.getDueDate());
            if (!REMINDER_THRESHOLDS.contains(daysUntilDue)) continue;

            String formattedDue = replenishment.getDueDate().format(DATE_FMT);
            String subject = daysUntilDue == 0
                    ? "Benevolence replenishment due TODAY — $" + replenishment.getPerMemberAmount()
                    : "Benevolence replenishment due in " + daysUntilDue + " day" + (daysUntilDue == 1 ? "" : "s");

            List<ReplenishmentPayment> pending = paymentRepository
                    .findByReplenishment(replenishment)
                    .stream()
                    .filter(p -> p.getStatus() == ReplenishmentPaymentStatus.PENDING)
                    .toList();

            for (ReplenishmentPayment payment : pending) {
                var user = payment.getEnrollment().getUser();
                String body = buildBody(user.getFullName(), payment, replenishment, formattedDue, daysUntilDue);

                try { emailService.sendPlain(user.getEmail(), user.getFullName(), subject, toHtml(payment, replenishment, formattedDue, daysUntilDue)); }
                catch (Exception e) { log.warn("Replenishment email failed for {}: {}", user.getEmail(), e.getMessage()); }

                if (user.getPhone() != null) {
                    try { smsService.send(user.getPhone(), user.getFullName(), subject + "\n" + body); }
                    catch (Exception e) { log.warn("Replenishment SMS failed for {}: {}", user.getPhone(), e.getMessage()); }
                }

                notificationService.createForUser(
                        user.getId(),
                        InAppNotificationCategory.REPLENISHMENT,
                        subject,
                        body,
                        "/portal/benevolence"
                );
                sent++;
            }
        }
        if (sent > 0) log.info("ReplenishmentReminderScheduler: sent {} reminder(s)", sent);
    }

    private static String buildBody(String name, ReplenishmentPayment payment,
                                     BenevolenceReplenishment replenishment,
                                     String formattedDue, long days) {
        String when = days == 0 ? "TODAY" : "in " + days + " day" + (days == 1 ? "" : "s");
        return String.format(
            "Hi %s, a benevolence replenishment contribution of $%s is due %s (%s). " +
            "This replenishment funds the welfare pool for fellow members. " +
            "%s" +
            "Please arrange payment to maintain your benevolence eligibility. " +
            "View details: https://ushirikacommunity.site/portal/benevolence",
            name, payment.getAmountDue(), when, formattedDue,
            replenishment.getNotes() != null ? replenishment.getNotes() + " " : "");
    }

    private static String toHtml(ReplenishmentPayment payment,
                                  BenevolenceReplenishment replenishment,
                                  String formattedDue, long days) {
        String when = days == 0 ? "TODAY" : "in " + days + " day" + (days == 1 ? "" : "s");
        return """
            <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
              <h2 style="color:#007834">Benevolence Replenishment Reminder</h2>
              <p>A replenishment contribution is due to maintain the welfare fund balance.</p>
              <table style="width:100%;border-collapse:collapse;margin:16px 0">
                <tr><td style="padding:8px;color:#666">Amount Due</td><td style="padding:8px;font-weight:600;color:#C0392B">$%s</td></tr>
                <tr style="background:#f9f9f9"><td style="padding:8px;color:#666">Due Date</td><td style="padding:8px;font-weight:600">%s</td></tr>
                <tr><td style="padding:8px;color:#666">Status</td><td style="padding:8px;font-weight:700;color:#E67E22">DUE %s</td></tr>
                %s
              </table>
              <p>Please arrange payment to keep your benevolence eligibility active.</p>
              <p><a href="https://ushirikacommunity.site/portal/benevolence"
                    style="background:#007834;color:#fff;padding:10px 20px;border-radius:8px;text-decoration:none">
                View Benevolence Dashboard
              </a></p>
            </div>
            """.formatted(
                payment.getAmountDue(),
                formattedDue,
                when.toUpperCase(),
                replenishment.getNotes() != null
                        ? "<tr style=\"background:#f9f9f9\"><td style=\"padding:8px;color:#666\">Notes</td><td style=\"padding:8px\">" + replenishment.getNotes() + "</td></tr>"
                        : ""
        );
    }
}
