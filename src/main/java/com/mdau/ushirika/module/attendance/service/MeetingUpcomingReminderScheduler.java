package com.mdau.ushirika.module.attendance.service;

import com.mdau.ushirika.module.attendance.entity.Meeting;
import com.mdau.ushirika.module.attendance.enums.MeetingStatus;
import com.mdau.ushirika.module.attendance.repository.MeetingRepository;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.notification.enums.InAppNotificationCategory;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import com.mdau.ushirika.module.notification.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Fires two precise reminders per meeting -- once its date enters the 24-hour window and again
 * once it enters the 6-hour window -- distinct from {@link MeetingReminderScheduler}'s coarser
 * day-offset reminders. Runs every 15 minutes; each window is guarded by its own "sent" flag on
 * the Meeting so a delayed or overlapping run can never double-send.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingUpcomingReminderScheduler {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a");

    private final MeetingRepository        meetingRepository;
    private final UserRepository           userRepository;
    private final EmailService             emailService;
    private final SmsService               smsService;
    private final InAppNotificationService notificationService;

    @Scheduled(cron = "0 */15 * * * *")
    public void sendUpcomingReminders() {
        LocalDateTime now = LocalDateTime.now();

        List<Meeting> due24h = meetingRepository.findByStatusAndReminder24hSentFalseAndMeetingDateBetween(
                MeetingStatus.SCHEDULED, now, now.plusHours(24));
        for (Meeting meeting : due24h) {
            notify(meeting);
            meeting.setReminder24hSent(true);
            meetingRepository.save(meeting);
        }

        List<Meeting> due6h = meetingRepository.findByStatusAndReminder6hSentFalseAndMeetingDateBetween(
                MeetingStatus.SCHEDULED, now, now.plusHours(6));
        for (Meeting meeting : due6h) {
            notify(meeting);
            meeting.setReminder6hSent(true);
            meetingRepository.save(meeting);
        }

        int sent = due24h.size() + due6h.size();
        if (sent > 0) log.info("MeetingUpcomingReminderScheduler: sent {} reminder(s)", sent);
    }

    private void notify(Meeting meeting) {
        var members = userRepository.findAllByRole(UserRole.MEMBER);
        if (members.isEmpty()) return;

        String formattedDate = meeting.getMeetingDate().format(DATE_FMT);
        String subject = "Ushirika Meeting Coming Up — " + meeting.getTitle();
        String location = meeting.getLocation() != null ? " at " + meeting.getLocation() : "";

        for (var member : members) {
            String body = String.format(
                    "Hi %s, this is a reminder that %s is coming up on %s%s. " +
                    "Attendance is mandatory for quarterly meetings. View your attendance history: " +
                    "https://ushirikacommunity.site/portal/meetings",
                    member.getFullName(), meeting.getTitle(), formattedDate, location);

            try {
                emailService.sendPlain(member.getEmail(), member.getFullName(), subject, toHtml(meeting, formattedDate, location));
            } catch (Exception e) {
                log.warn("Meeting upcoming-reminder email failed for {}: {}", member.getEmail(), e.getMessage());
            }

            if (member.getPhone() != null) {
                try { smsService.send(member.getPhone(), member.getFullName(), subject + "\n" + body); }
                catch (Exception e) { log.warn("Meeting upcoming-reminder SMS failed for {}: {}", member.getPhone(), e.getMessage()); }
            }

            notificationService.createForUser(
                    member.getId(),
                    InAppNotificationCategory.MEETING_REMINDER,
                    subject,
                    body,
                    "/portal/meetings"
            );
        }
    }

    private static String toHtml(Meeting meeting, String formattedDate, String location) {
        return """
            <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
              <h2 style="color:#007834">Meeting Coming Up</h2>
              <p><strong>%s</strong> is coming up.</p>
              <p><strong>Date:</strong> %s%s</p>
              <p>Attendance is <strong>mandatory</strong> for quarterly meetings. Missing two consecutive
              quarterly meetings results in automatic cessation of membership.</p>
              <p><a href="https://ushirikacommunity.site/portal/meetings">View my attendance history</a></p>
            </div>
            """.formatted(meeting.getTitle(), formattedDate, location);
    }
}
