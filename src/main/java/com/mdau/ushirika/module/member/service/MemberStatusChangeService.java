package com.mdau.ushirika.module.member.service;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.entity.MemberStatusChange;
import com.mdau.ushirika.module.member.enums.MemberStatus;
import com.mdau.ushirika.module.member.enums.MemberStatusReason;
import com.mdau.ushirika.module.member.repository.MemberStatusChangeRepository;
import com.mdau.ushirika.module.notification.enums.InAppNotificationCategory;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Records every active/membershipCeased transition to history with a reason -- previously these
 * two booleans changed silently, with at most an internal audit-log line, no reason a member
 * could ever see and nothing a report could group by. Called from every existing site that
 * already flips active/membershipCeased (dues nonpayment, attendance-ceased, admin manual
 * toggle, reinstatement); this service does not itself decide when a status should change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberStatusChangeService {

    private final MemberStatusChangeRepository repository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final InAppNotificationService notificationService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    public static MemberStatus statusOf(boolean active, boolean membershipCeased) {
        if (membershipCeased) return MemberStatus.CEASED;
        return active ? MemberStatus.ACTIVE : MemberStatus.INACTIVE;
    }

    /** Call with the status as it stood immediately before the caller's own mutation, and again
     * after -- callers already had to read/set the booleans themselves, this only adds the
     * history row and the two denormalized fields on top. A no-op if nothing actually changed. */
    @Transactional
    public void record(User user, MemberStatus previousStatus, MemberStatus newStatus,
                        MemberStatusReason reason, User changedBy, String notes) {
        if (previousStatus == newStatus) return;

        MemberStatusChange change = MemberStatusChange.builder()
                .userId(user.getId())
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .reason(reason)
                .changedByUserId(changedBy != null ? changedBy.getId() : null)
                .notes(notes)
                .build();
        repository.save(change);

        user.setCurrentStatusReason(reason);
        user.setCurrentStatusChangedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Member status change: user={} {} -> {} reason={} by={}",
                user.getEmail(), previousStatus, newStatus, reason,
                changedBy != null ? changedBy.getEmail() : "system");
    }

    /** Email + in-app notice explaining a status change, carrying the exact reason. Only called
     * from the two sites that had no notification of their own before this phase (admin manual
     * toggle, dues reactivation) -- attendance-ceased/dues-nonpayment deactivation and formal
     * reinstatement approval already send their own purpose-built notices, so calling this there
     * too would double-notify the member. */
    public void notifyStatusChange(User user, MemberStatus newStatus, MemberStatusReason reason, String notes) {
        boolean reactivated = newStatus == MemberStatus.ACTIVE;
        String subject = reactivated
                ? "Your Ushirika Membership is Active Again"
                : "Your Ushirika Membership Status Has Changed";
        String reasonLabel = humanReason(reason);
        String html = """
                <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
                  <h2 style="color:%s">%s</h2>
                  <p>Hi %s,</p>
                  <p>Your Ushirika Welfare Organization membership status is now <strong>%s</strong>.</p>
                  <p><strong>Reason:</strong> %s%s</p>
                  <p>
                    <a href="%s/portal"
                       style="display:inline-block;background:#007834;color:#fff;padding:10px 22px;
                              border-radius:999px;text-decoration:none;font-weight:600">
                      View My Portal &rarr;
                    </a>
                  </p>
                  <p style="color:#888;font-size:12px">
                    Questions? Contact <a href="mailto:admin@ushirikawelfare.org">admin@ushirikawelfare.org</a>
                  </p>
                </div>
                """.formatted(
                        reactivated ? "#007834" : "#B91C1C",
                        subject,
                        user.getFirstName(),
                        newStatus.name(),
                        reasonLabel,
                        notes != null && !notes.isBlank() ? " — " + notes : "",
                        siteUrl);

        try {
            emailService.sendPlain(user.getEmail(), user.getFullName(), subject, html);
        } catch (Exception e) {
            log.warn("Membership status change email failed for {}: {}", user.getEmail(), e.getMessage());
        }

        try {
            notificationService.createForUser(
                    user.getId(),
                    InAppNotificationCategory.MEMBERSHIP_STATUS,
                    subject,
                    "Your membership status is now " + newStatus.name() + ". Reason: " + reasonLabel
                            + (notes != null && !notes.isBlank() ? " — " + notes : ""),
                    "/portal"
            );
        } catch (Exception e) {
            log.warn("Membership status change in-app notification failed for {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private static String humanReason(MemberStatusReason reason) {
        return switch (reason) {
            case DUES_NONPAYMENT   -> "Annual dues not paid by the deadline";
            case FINE_NONPAYMENT   -> "A fine went unpaid past its due date";
            case ATTENDANCE_CEASED -> "Two consecutive missed quarterly meetings";
            case ADMIN_MANUAL      -> "Set by an administrator";
            case VOLUNTARY_EXIT    -> "Voluntary exit";
            case TERMINATED        -> "Membership terminated";
            case REINSTATED        -> "Reinstated";
        };
    }
}
