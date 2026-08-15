package com.mdau.ushirika.module.mgr.service;

import com.mdau.ushirika.module.mgr.entity.MgrContribution;
import com.mdau.ushirika.module.mgr.entity.MgrCycle;
import com.mdau.ushirika.module.mgr.enums.ContributionStatus;
import com.mdau.ushirika.module.mgr.enums.CycleStatus;
import com.mdau.ushirika.module.mgr.repository.MgrContributionRepository;
import com.mdau.ushirika.module.mgr.repository.MgrCycleRepository;
import com.mdau.ushirika.module.notification.enums.InAppNotificationCategory;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import com.mdau.ushirika.module.notification.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Sends MGR contribution reminders at 14 / 7 / 3 days before the monthly
 * payout / contribution cutoff date, on the cutoff date, and weekly past-due
 * reminders (every Monday) for contributions from prior months.
 * Runs daily at 08:00 UTC.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MgrReminderScheduler {

    private static final int[] REMINDER_DAYS = {14, 7, 3, 0};
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final MgrCycleRepository       cycleRepository;
    private final MgrContributionRepository contributionRepository;
    private final EmailService             emailService;
    private final SmsService               smsService;
    private final InAppNotificationService notificationService;

    @Scheduled(cron = "0 0 8 * * *")
    public void sendMgrReminders() {
        LocalDate today = LocalDate.now();
        List<MgrCycle> activeCycles = cycleRepository.findAllByStatus(CycleStatus.ACTIVE);
        int sent = 0;

        for (MgrCycle cycle : activeCycles) {
            int currentMonth = (int) ChronoUnit.MONTHS.between(cycle.getStartDate(), today) + 1;
            // Every cycle runs a fixed 12 months (see MgrService.createCycle -- endDate is always
            // startDate + 11 months) regardless of totalSlots, which is member capacity, not cycle
            // length. Comparing against totalSlots here silently stopped all reminders -- including
            // weekly past-due nagging -- partway through any cycle configured with fewer than 12 slots.
            if (currentMonth < 1 || currentMonth > 12) continue;

            LocalDate rawCutoff = cycle.getStartDate()
                    .plusMonths(currentMonth - 1)
                    .withDayOfMonth(1)
                    .plusMonths(1)
                    .minusDays(1); // end of the contribution month
            // Cutoff = last day of contribution month, or benefitPayoutDay if it falls earlier
            LocalDate cutoff = cycle.getBenefitPayoutDay() > 0
                    ? cycle.getStartDate().plusMonths(currentMonth - 1)
                              .withDayOfMonth(Math.min(cycle.getBenefitPayoutDay(),
                                  cycle.getStartDate().plusMonths(currentMonth - 1).lengthOfMonth()))
                    : rawCutoff;

            long daysUntilCutoff = ChronoUnit.DAYS.between(today, cutoff);

            // Current-month reminders
            for (int days : REMINDER_DAYS) {
                if (daysUntilCutoff == days) {
                    List<MgrContribution> pending = contributionRepository
                            .findByCycleAndContributionMonthOrderBySlotSlotNumber(cycle, currentMonth)
                            .stream()
                            .filter(c -> c.getStatus() == ContributionStatus.PENDING)
                            .toList();

                    String formattedCutoff = cutoff.format(DATE_FMT);
                    String subject = days == 0
                            ? "MGR contribution due TODAY — $" + cycle.getMonthlyContribution()
                            : "MGR contribution due in " + days + " day" + (days == 1 ? "" : "s") + " — $" + cycle.getMonthlyContribution();

                    for (MgrContribution contrib : pending) {
                        var user = contrib.getSlot().getUser();
                        String body = buildCurrentBody(user.getFullName(), cycle, currentMonth, formattedCutoff, days);

                        try { emailService.sendPlain(user.getEmail(), user.getFullName(), subject, toHtml(cycle, currentMonth, formattedCutoff, days, false)); }
                        catch (Exception e) { log.warn("MGR email failed for {}: {}", user.getEmail(), e.getMessage()); }

                        if (user.getPhone() != null) {
                            try { smsService.send(user.getPhone(), user.getFullName(), subject + "\n" + body); }
                            catch (Exception e) { log.warn("MGR SMS failed for {}: {}", user.getPhone(), e.getMessage()); }
                        }

                        notificationService.createForUser(
                                user.getId(),
                                InAppNotificationCategory.MGR_PAYMENT,
                                subject,
                                body,
                                "/portal/mgr"
                        );
                        sent++;
                    }
                }
            }

            // Past-due reminders — every Monday for previous months
            if (today.getDayOfWeek() == DayOfWeek.MONDAY && currentMonth > 1) {
                for (int pastMonth = 1; pastMonth < currentMonth; pastMonth++) {
                    List<MgrContribution> pastDue = contributionRepository
                            .findByCycleAndContributionMonthOrderBySlotSlotNumber(cycle, pastMonth)
                            .stream()
                            .filter(c -> c.getStatus() == ContributionStatus.PENDING)
                            .toList();

                    if (pastDue.isEmpty()) continue;
                    int pm = pastMonth;
                    String subject = "Overdue MGR contribution — Month " + pm + " of " + cycle.getName();

                    for (MgrContribution contrib : pastDue) {
                        var user = contrib.getSlot().getUser();
                        String body = String.format(
                            "Hi %s, your MGR contribution of $%s for Month %d of %s is overdue. " +
                            "Please contact the administrator to arrange payment. " +
                            "View your MGR dashboard: https://ushirikacommunity.site/portal/mgr",
                            user.getFullName(), cycle.getMonthlyContribution(), pm, cycle.getName());

                        try { emailService.sendPlain(user.getEmail(), user.getFullName(), subject, "<p>" + body + "</p>"); }
                        catch (Exception e) { log.warn("MGR past-due email failed for {}: {}", user.getEmail(), e.getMessage()); }

                        if (user.getPhone() != null) {
                            try { smsService.send(user.getPhone(), user.getFullName(), subject + "\n" + body); }
                            catch (Exception e) { log.warn("MGR past-due SMS failed for {}: {}", user.getPhone(), e.getMessage()); }
                        }

                        notificationService.createForUser(
                                user.getId(),
                                InAppNotificationCategory.MGR_PAYMENT,
                                subject,
                                body,
                                "/portal/mgr"
                        );
                        sent++;
                    }
                }
            }
        }
        if (sent > 0) log.info("MgrReminderScheduler: sent {} reminder(s)", sent);
    }

    private static String buildCurrentBody(String name, MgrCycle cycle, int month, String cutoff, int days) {
        String when = days == 0 ? "TODAY" : "in " + days + " day" + (days == 1 ? "" : "s");
        return String.format(
            "Hi %s, your MGR contribution of $%s for Month %d of %s is due %s (%s). " +
            "Please ensure payment is received before the cutoff to remain in good standing. " +
            "View your MGR dashboard: https://ushirikacommunity.site/portal/mgr",
            name, cycle.getMonthlyContribution(), month, cycle.getName(), when, cutoff);
    }

    private static String toHtml(MgrCycle cycle, int month, String cutoff, int days, boolean pastDue) {
        String when = days == 0 ? "TODAY" : "in " + days + " day" + (days == 1 ? "" : "s");
        String color = pastDue ? "#C0392B" : "#007834";
        return """
            <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
              <h2 style="color:%s">MGR Contribution Reminder</h2>
              <table style="width:100%;border-collapse:collapse;margin:16px 0">
                <tr><td style="padding:8px;color:#666">Cycle</td><td style="padding:8px;font-weight:600">%s</td></tr>
                <tr style="background:#f9f9f9"><td style="padding:8px;color:#666">Month</td><td style="padding:8px;font-weight:600">%d</td></tr>
                <tr><td style="padding:8px;color:#666">Amount</td><td style="padding:8px;font-weight:600">$%s</td></tr>
                <tr style="background:#f9f9f9"><td style="padding:8px;color:#666">Due</td><td style="padding:8px;font-weight:700;color:%s">%s (%s)</td></tr>
              </table>
              <p><a href="https://ushirikacommunity.site/portal/mgr"
                    style="background:%s;color:#fff;padding:10px 20px;border-radius:8px;text-decoration:none">
                View MGR Dashboard
              </a></p>
            </div>
            """.formatted(color, cycle.getName(), month, cycle.getMonthlyContribution(), color, cutoff, when.toUpperCase(), color);
    }
}
