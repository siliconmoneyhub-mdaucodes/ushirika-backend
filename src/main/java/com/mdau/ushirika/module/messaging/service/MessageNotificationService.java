package com.mdau.ushirika.module.messaging.service;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.messaging.entity.ConversationMessage;
import com.mdau.ushirika.module.messaging.entity.ConversationThread;
import com.mdau.ushirika.module.messaging.enums.ThreadPriority;
import com.mdau.ushirika.module.messaging.repository.ConversationThreadRepository;
import com.mdau.ushirika.module.notification.enums.InAppNotificationCategory;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import com.mdau.ushirika.module.program.entity.ProgramAdminAssignment;
import com.mdau.ushirika.module.program.repository.ProgramAdminAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Email + in-app alerts for member &lt;-&gt; staff conversations.
 *
 * <h3>Who is alerted on the staff side</h3>
 * <ul>
 *   <li><b>General threads (program == null) -&gt; every active ADMIN and SUPERADMIN.</b> Not LEADERSHIP
 *       (read-only observers -- operational pings are noise to them) and not SECRETARY / CHIEF_WHIP /
 *       COMPLIANCE / financial roles: none of them can reply (POST /admin/** is ADMIN/SUPERADMIN only), so
 *       alerting them would alert people who cannot act. The line is: alert exactly the people the API
 *       lets reply.</li>
 *   <li><b>Program threads -&gt; that program's ProgramAdminAssignment holders.</b> If the program has zero
 *       assignments we fall back to ADMIN + SUPERADMIN (and log a warning) so a coordinator-less program is
 *       never a dead letter. ActionItemsService applies the same fallback so the feed and the alerts agree.</li>
 * </ul>
 *
 * <h3>Why the email never contains the message body</h3>
 * <ol>
 *   <li>These threads carry bereavement details, financial hardship, loan requests and next-of-kin
 *       information. Email sits unencrypted in an inbox indefinitely and transits third-party infrastructure.</li>
 *   <li>BrevoEmailService.sendPlain writes a 2000-character copy of every email body into
 *       notification_logs.body, readable on the admin Delivery Logs page by anyone with CAP_NOTIFICATIONS --
 *       including officials with no business reading a member's welfare correspondence. Including the body
 *       would silently create a second, more widely readable copy of every private message.</li>
 *   <li>The in-app thread is the system of record; the email is a doorbell. Reference number + priority give
 *       the recipient enough to triage without leaking content.</li>
 * </ol>
 *
 * <h3>Throttling</h3>
 * Staff: at most one alert per thread per 15 min (3 min for URGENT), skipped entirely if staff read the thread
 * in the last 2 min. Member: at most one alert per 30 min unless the member has read the thread since the last
 * alert, and skipped if they read it in the last 2 min. In-app notifications for the member are NOT throttled.
 * Every public method swallows its own failures -- a notification problem must never roll back a message write.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageNotificationService {

    static final int MEMBER_ALERT_WINDOW_MINUTES = 30;
    static final int STAFF_ALERT_WINDOW_MINUTES = 15;
    static final int URGENT_FLOOR_MINUTES = 3;
    static final int ACTIVELY_READING_MINUTES = 2;

    private final ConversationThreadRepository threadRepository;
    private final UserRepository userRepository;
    private final ProgramAdminAssignmentRepository assignmentRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final EmailService emailService;
    private final InAppNotificationService inAppNotificationService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    // ── Entry points ─────────────────────────────────────────────────────────

    /** Called by MessagingService after a message is saved. Never throws. */
    public void onMessagePosted(ConversationThread thread, ConversationMessage message) {
        try {
            LocalDateTime now = LocalDateTime.now();
            if (message.isFromMember()) {
                alertStaff(thread, now, false);
            } else {
                alertMember(thread, now);
            }
        } catch (Exception e) {
            log.warn("Message notification failed for thread {}: {}", thread.getId(), e.getMessage());
        }
    }

    /** Fires when a member raises a thread to URGENT. Ignores the normal 15-min window but keeps the 3-min floor. */
    public void notifyStaffOfEscalation(ConversationThread thread) {
        try {
            alertStaff(thread, LocalDateTime.now(), true);
        } catch (Exception e) {
            log.warn("Escalation notification failed for thread {}: {}", thread.getId(), e.getMessage());
        }
    }

    // ── Staff alert ──────────────────────────────────────────────────────────

    private void alertStaff(ConversationThread thread, LocalDateTime now, boolean escalation) {
        if (!shouldAlertStaff(thread, now, escalation)) return;

        List<User> recipients = resolveStaffRecipients(thread);
        if (recipients.isEmpty()) {
            log.warn("No staff recipients to alert for thread {}", thread.getReferenceNumber());
            return;
        }
        String memberName = thread.getMember().getFullName();
        String memberCode = memberCode(thread.getMember());
        String subject = escalation
                ? "[URGENT] " + ref(thread) + " escalated by " + memberName
                : priorityTag(thread.getPriority()) + "[" + ref(thread) + "] New message from " + memberName;
        String about = thread.getProgram() != null ? thread.getProgram().getName() : "General enquiry";

        for (User staff : recipients) {
            String html = "<div style=\"font-family:sans-serif;max-width:520px;margin:auto\">"
                    + "<h2 style=\"color:#007834\">" + (escalation ? "Conversation escalated" : "New member message") + "</h2>"
                    + "<p><strong>Reference:</strong> " + esc(ref(thread))
                    + " &middot; <strong>Priority:</strong> " + priorityLabel(thread.getPriority()) + "</p>"
                    + "<p><strong>From:</strong> " + esc(memberName) + (memberCode != null ? " (" + esc(memberCode) + ")" : "") + "<br>"
                    + "<strong>About:</strong> " + esc(about) + "</p>"
                    + button(siteUrl + "/admin/messages", "Open in Admin Panel")
                    + "<p style=\"color:#666;font-size:13px\">The message itself is in the portal — we don't include member "
                    + "correspondence in email.<br>You'll get at most one alert per conversation every "
                    + STAFF_ALERT_WINDOW_MINUTES + " minutes.</p>"
                    + "</div>";
            emailService.sendPlain(staff.getEmail(), staff.getFullName(), subject, html);
        }
        thread.setStaffAlertSentAt(now);
        threadRepository.save(thread);
    }

    /** Pure throttle decision for the staff side (package-private for tests). */
    static boolean shouldAlertStaff(ConversationThread thread, LocalDateTime now, boolean escalation) {
        if (within(thread.getStaffLastReadAt(), now, ACTIVELY_READING_MINUTES) && !escalation) return false;
        int window = (escalation || thread.getPriority() == ThreadPriority.URGENT)
                ? URGENT_FLOOR_MINUTES : STAFF_ALERT_WINDOW_MINUTES;
        return !within(thread.getStaffAlertSentAt(), now, window);
    }

    /** General -> ADMIN+SUPERADMIN; program -> its assignees, falling back to ADMIN+SUPERADMIN when there are none. */
    List<User> resolveStaffRecipients(ConversationThread thread) {
        Map<UUID, User> byId = new LinkedHashMap<>();
        if (thread.getProgram() != null) {
            for (ProgramAdminAssignment a : assignmentRepository.findAllByProgramId(thread.getProgram().getId())) {
                if (a.getUser() != null) byId.put(a.getUser().getId(), a.getUser());
            }
            if (byId.isEmpty()) {
                log.warn("Program '{}' has no coordinators assigned -- falling back to ADMIN/SUPERADMIN for thread {}",
                        thread.getProgram().getName(), thread.getReferenceNumber());
                addAdmins(byId);
            }
        } else {
            addAdmins(byId);
        }
        List<User> result = new ArrayList<>();
        for (User u : byId.values()) {
            if (u.isActive() && u.getEmail() != null && !u.getEmail().isBlank()) result.add(u);
        }
        return result;
    }

    private void addAdmins(Map<UUID, User> byId) {
        for (User u : userRepository.findAllByRoleIn(List.of(UserRole.ADMIN, UserRole.SUPERADMIN))) {
            byId.put(u.getId(), u);
        }
    }

    // ── Member alert ─────────────────────────────────────────────────────────

    private void alertMember(ConversationThread thread, LocalDateTime now) {
        User member = thread.getMember();

        // In-app notification: every staff reply, unthrottled (free and non-intrusive).
        try {
            inAppNotificationService.createForUser(member.getId(), InAppNotificationCategory.GENERAL,
                    "New reply — " + ref(thread),
                    "Ushirika has replied to your message. Open your messages to read it.",
                    "/portal/messages");
        } catch (Exception e) {
            log.warn("In-app notification failed for thread {}: {}", thread.getId(), e.getMessage());
        }

        if (!shouldAlertMember(thread, now)) return;
        if (member.getEmail() == null || member.getEmail().isBlank() || !member.isActive()) return;

        String from = thread.getProgram() != null ? thread.getProgram().getName() + " Coordinators" : "Ushirika Admin Team";
        String subject = "[" + ref(thread) + "] Ushirika has replied to your message";
        String html = "<div style=\"font-family:sans-serif;max-width:520px;margin:auto\">"
                + "<h2 style=\"color:#007834\">You have a reply</h2>"
                + "<p><strong>Reference:</strong> " + esc(ref(thread))
                + " &middot; <strong>Priority:</strong> " + priorityLabel(thread.getPriority()) + "<br>"
                + "<strong>From:</strong> " + esc(from) + "</p>"
                + button(siteUrl + "/portal/messages", "Read the reply")
                + "<p style=\"color:#666;font-size:13px\">For your privacy, we don't include the message itself in email "
                + "— sign in to read it.</p>"
                + "<p style=\"color:#666;font-size:13px\">You're receiving this because you have an active conversation "
                + "on the Ushirika member portal.<br>Ushirika Welfare Organization</p>"
                + "</div>";
        emailService.sendPlain(member.getEmail(), member.getFullName(), subject, html);
        thread.setMemberAlertSentAt(now);
        threadRepository.save(thread);
    }

    /** Pure throttle decision for the member side (package-private for tests). */
    static boolean shouldAlertMember(ConversationThread thread, LocalDateTime now) {
        if (within(thread.getMemberLastReadAt(), now, ACTIVELY_READING_MINUTES)) return false;
        LocalDateTime lastAlert = thread.getMemberAlertSentAt();
        if (lastAlert == null) return true;
        boolean readSinceLastAlert = thread.getMemberLastReadAt() != null && thread.getMemberLastReadAt().isAfter(lastAlert);
        // Inside the window and the member hasn't come back since the last alert -> stay quiet.
        return !within(lastAlert, now, MEMBER_ALERT_WINDOW_MINUTES) || readSinceLastAlert;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** True when {@code stamp} is non-null and no older than {@code minutes} before {@code now}. */
    private static boolean within(LocalDateTime stamp, LocalDateTime now, int minutes) {
        return stamp != null && stamp.isAfter(now.minusMinutes(minutes));
    }

    private String memberCode(User member) {
        try {
            return memberProfileRepository.findByUser(member).map(p -> p.getMemberId()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String ref(ConversationThread t) {
        return t.getReferenceNumber() != null ? t.getReferenceNumber() : "New conversation";
    }

    static String priorityTag(ThreadPriority p) {
        return p == ThreadPriority.URGENT ? "[URGENT] " : p == ThreadPriority.HIGH ? "[HIGH] " : "";
    }

    private static String priorityLabel(ThreadPriority p) {
        ThreadPriority v = p != null ? p : ThreadPriority.NORMAL;
        return v.name().charAt(0) + v.name().substring(1).toLowerCase();
    }

    private static String button(String url, String label) {
        return "<p style=\"margin:24px 0\"><a href=\"" + url + "\" style=\"display:inline-block;background:#007834;color:#fff;"
                + "padding:10px 20px;border-radius:24px;text-decoration:none;font-weight:600\">" + label + "</a></p>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
