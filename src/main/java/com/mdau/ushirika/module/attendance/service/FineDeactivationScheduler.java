package com.mdau.ushirika.module.attendance.service;

import com.mdau.ushirika.common.util.AppClock;
import com.mdau.ushirika.module.attendance.entity.Fine;
import com.mdau.ushirika.module.attendance.enums.FineStatus;
import com.mdau.ushirika.module.attendance.repository.FineRepository;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.enums.MemberStatus;
import com.mdau.ushirika.module.member.enums.MemberStatusReason;
import com.mdau.ushirika.module.member.service.MemberStatusChangeService;
import com.mdau.ushirika.module.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * A PENDING fine unpaid seven days past its due date deactivates the member -- previously fines
 * only ever sent reminders (see FineReminderScheduler) and had no real enforcement behind them.
 * Runs daily; idempotent by exact-date match on dueDate (same pattern FineReminderScheduler uses
 * for its own 7/3/0-day reminders), so each fine only ever triggers this once, and the
 * already-inactive check means a member deactivated by some other rule isn't double-processed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FineDeactivationScheduler {

    private final FineRepository fineRepository;
    private final UserRepository userRepository;
    private final MemberStatusChangeService statusChangeService;
    private final EmailService emailService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Chicago")
    @Transactional
    public void deactivateOverdueFines() {
        LocalDate sevenDaysAgo = AppClock.today().minusDays(7);
        List<Fine> overdue = fineRepository.findByStatusAndDueDate(FineStatus.PENDING, sevenDaysAgo);

        int deactivated = 0;
        for (Fine fine : overdue) {
            User user = fine.getUser();
            if (!user.isActive()) continue;

            MemberStatus previousStatus = MemberStatusChangeService.statusOf(user.isActive(), user.isMembershipCeased());
            user.setActive(false);
            userRepository.save(user);
            statusChangeService.record(user, previousStatus, MemberStatus.INACTIVE,
                    MemberStatusReason.FINE_NONPAYMENT, null,
                    "Fine of $" + fine.getAmount() + " (" + fine.getReason() + ") unpaid 7 days past due");
            sendDeactivationEmail(user, fine);
            deactivated++;
        }
        if (deactivated > 0) {
            log.info("FineDeactivationScheduler: deactivated {} member(s) for unpaid fines", deactivated);
        }
    }

    private void sendDeactivationEmail(User user, Fine fine) {
        String html = """
                <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
                  <h2 style="color:#B91C1C">Membership Status: Inactive</h2>
                  <p>Hi %s,</p>
                  <p>Your Ushirika Welfare Organization membership has been set to <strong>Inactive</strong>
                     because a fine of <strong>$%s</strong> ("%s") went unpaid for 7 days past its due date.</p>
                  <p>To restore your Active status, please log in to your member portal and settle the
                     outstanding fine. Your access to programs and benefits is paused until it's cleared.</p>
                  <p>
                    <a href="%s/portal/meetings"
                       style="display:inline-block;background:#007834;color:#fff;padding:10px 22px;
                              border-radius:999px;text-decoration:none;font-weight:600">
                      Pay Now &rarr;
                    </a>
                  </p>
                </div>
                """.formatted(user.getFullName(), fine.getAmount(), fine.getReason(), siteUrl);
        try {
            emailService.sendPlain(user.getEmail(), user.getFullName(),
                    "Your Ushirika Membership is Now Inactive — Unpaid Fine", html);
        } catch (Exception e) {
            log.warn("Could not send fine-deactivation email to {}: {}", user.getEmail(), e.getMessage());
        }
    }
}
