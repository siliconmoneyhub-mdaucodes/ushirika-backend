package com.mdau.ushirika.module.attendance.service;

import com.mdau.ushirika.module.attendance.entity.Meeting;
import com.mdau.ushirika.module.attendance.enums.MeetingStatus;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.notification.enums.InAppNotificationCategory;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import com.mdau.ushirika.module.notification.service.SmsService;
import com.mdau.ushirika.module.attendance.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sends meeting reminders to all active members at 30 / 14 / 7 days before
 * the meeting and on the day of the meeting.
 * Runs daily at 07:00 UTC.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingReminderScheduler {

    private static final int[] REMINDER_DAYS = {30, 14, 7, 0};
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a");

    private final MeetingRepository       meetingRepository;
    private final UserRepository          userRepository;
    private final EmailService            emailService;
    private final SmsService              smsService;
    private final InAppNotificationService notificationService;

    @Scheduled(cron = "0 0 7 * * *")
    public void sendMeetingReminders() {
        LocalDate today = LocalDate.now();
        var members = userRepository.findAllByRole(UserRole.MEMBER);
        if (members.isEmpty()) return;

        int sent = 0;
        for (int days : REMINDER_DAYS) {
            LocalDate target = today.plusDays(days);
            List<Meeting> meetings = meetingRepository.findByStatusAndMeetingDateBetween(
                    MeetingStatus.SCHEDULED,
                    target.atStartOfDay(),
                    target.atTime(23, 59, 59)
            );

            for (Meeting meeting : meetings) {
                String formattedDate = meeting.getMeetingDate().format(DATE_FMT);
                String subject = days == 0
                        ? "Ushirika Meeting TODAY — " + meeting.getTitle()
                        : "Ushirika Meeting in " + days + " day" + (days == 1 ? "" : "s") + " — " + meeting.getTitle();

                for (var member : members) {
                    String body = buildBody(member.getFullName(), meeting, formattedDate, days);

                    try { emailService.sendPlain(member.getEmail(), member.getFullName(), subject, toHtml(meeting, formattedDate, days)); }
                    catch (Exception e) { log.warn("Meeting email failed for {}: {}", member.getEmail(), e.getMessage()); }

                    if (member.getPhone() != null) {
                        try { smsService.send(member.getPhone(), member.getFullName(), subject + "\n" + body); }
                        catch (Exception e) { log.warn("Meeting SMS failed for {}: {}", member.getPhone(), e.getMessage()); }
                    }

                    notificationService.createForUser(
                            member.getId(),
                            InAppNotificationCategory.MEETING_REMINDER,
                            subject,
                            body,
                            "/portal/meetings"
                    );
                    sent++;
                }
            }
        }
        if (sent > 0) log.info("MeetingReminderScheduler: sent {} reminder(s)", sent);
    }

    private static String buildBody(String name, Meeting meeting, String formattedDate, int days) {
        String when = days == 0 ? "TODAY" : "in " + days + " day" + (days == 1 ? "" : "s");
        String location = meeting.getLocation() != null ? " at " + meeting.getLocation() : "";
        return String.format(
            "Hi %s, this is a reminder that the %s is scheduled %s — %s%s. " +
            "Attendance is mandatory for quarterly meetings. View your attendance history: " +
            "https://ushirikacommunity.site/portal/meetings",
            name, meeting.getTitle(), when, formattedDate, location);
    }

    private static String toHtml(Meeting meeting, String formattedDate, int days) {
        String when = days == 0 ? "TODAY" : "in " + days + " day" + (days == 1 ? "" : "s");
        String location = meeting.getLocation() != null
                ? "<p><strong>Location:</strong> " + meeting.getLocation() + "</p>" : "";
        return """
            <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
              <h2 style="color:#007834">Meeting Reminder</h2>
              <p><strong>%s</strong> — %s %s</p>
              <p><strong>Date:</strong> %s</p>
              %s
              %s
              <p>Attendance is <strong>mandatory</strong> for quarterly meetings. Missing two consecutive
              quarterly meetings results in automatic cessation of membership.</p>
              <p><a href="https://ushirikacommunity.site/portal/meetings">View my attendance history</a></p>
            </div>
            """.formatted(
                meeting.getTitle(),
                when.equals("TODAY") ? "is" : "is",
                when,
                formattedDate,
                location,
                meeting.getNotes() != null ? "<p><em>" + meeting.getNotes() + "</em></p>" : ""
        );
    }
}
