package com.mdau.ushirika.module.event.service;

import com.mdau.ushirika.common.util.AppClock;
import com.mdau.ushirika.module.event.entity.Event;
import com.mdau.ushirika.module.event.entity.EventRegistration;
import com.mdau.ushirika.module.event.enums.EventStatus;
import com.mdau.ushirika.module.event.enums.RegistrationStatus;
import com.mdau.ushirika.module.event.repository.EventRegistrationRepository;
import com.mdau.ushirika.module.event.repository.EventRepository;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Fires two precise reminders per published event -- once its start enters the 24-hour window
 * and again once it enters the 6-hour window -- to everyone whose registration is still
 * REGISTERED (members and guests). Runs every 15 minutes; each window is guarded by its own
 * "sent" flag on the Event so a delayed or overlapping run can never double-send.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventUpcomingReminderScheduler {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a");

    private final EventRepository             eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final EmailService                emailService;
    private final SmsService                  smsService;
    private final NotificationDispatcher      notificationDispatcher;
    private final InAppNotificationService    notificationService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    @Scheduled(cron = "0 */15 * * * *")
    public void sendUpcomingReminders() {
        LocalDateTime now = AppClock.now();

        List<Event> due24h = eventRepository.findByStatusInAndReminder24hSentFalseAndStartDateTimeBetween(
                List.of(EventStatus.PUBLISHED), now, now.plusHours(24));
        for (Event event : due24h) {
            notify(event);
            event.setReminder24hSent(true);
            eventRepository.save(event);
        }

        List<Event> due6h = eventRepository.findByStatusInAndReminder6hSentFalseAndStartDateTimeBetween(
                List.of(EventStatus.PUBLISHED), now, now.plusHours(6));
        for (Event event : due6h) {
            notify(event);
            event.setReminder6hSent(true);
            eventRepository.save(event);
        }

        int sent = due24h.size() + due6h.size();
        if (sent > 0) log.info("EventUpcomingReminderScheduler: sent {} reminder(s)", sent);
    }

    private void notify(Event event) {
        List<EventRegistration> registrations =
                registrationRepository.findAllByEventAndStatus(event, RegistrationStatus.REGISTERED);
        if (registrations.isEmpty()) return;

        String formattedDate = event.getStartDateTime().format(DATE_FMT);
        String subject = "Ushirika Event Coming Up — " + event.getTitle();
        String where = event.getVenue() != null ? " at " + event.getVenue()
                : event.getOnlineLink() != null ? " (online)" : "";

        for (EventRegistration registration : registrations) {
            String name = registration.getDisplayName();
            String email = registration.getDisplayEmail();
            if (email == null) continue;

            String body = String.format(
                    "Hi %s, this is a reminder that %s is coming up on %s%s. " +
                    "See event details: https://ushirikacommunity.site/portal/events",
                    name, event.getTitle(), formattedDate, where);

            try {
                emailService.sendPlain(email, name, subject, toHtml(event, formattedDate, where));
            } catch (Exception e) {
                log.warn("Event upcoming-reminder email failed for {}: {}", email, e.getMessage());
            }

            String phone = registration.isMemberRegistration()
                    ? registration.getUser().getPhone() : registration.getGuestPhone();
            if (phone != null) {
                try { smsService.send(phone, name, subject + "\n" + body); }
                catch (Exception e) { log.warn("Event upcoming-reminder SMS failed for {}: {}", phone, e.getMessage()); }
            }

            notificationDispatcher.dispatchWhatsApp(NotificationCategory.MEETING_EVENT_REMINDER,
                    phone, name, List.of(
                            name,
                            event.getTitle(),
                            formattedDate,
                            siteUrl + "/portal/events"
                    ));

            if (registration.isMemberRegistration()) {
                notificationService.createForUser(
                        registration.getUser().getId(),
                        InAppNotificationCategory.EVENT_REMINDER,
                        subject,
                        body,
                        "/portal/events"
                );
            }
        }
    }

    private static String toHtml(Event event, String formattedDate, String where) {
        return """
            <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
              <h2 style="color:#007834">Event Coming Up</h2>
              <p><strong>%s</strong> is coming up.</p>
              <p><strong>Date:</strong> %s%s</p>
              <p><a href="https://ushirikacommunity.site/portal/events">View event details</a></p>
            </div>
            """.formatted(event.getTitle(), formattedDate, where);
    }
}
