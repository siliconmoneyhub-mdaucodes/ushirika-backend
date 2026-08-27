package com.mdau.ushirika.module.election.service;

import com.mdau.ushirika.common.util.AppClock;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.election.entity.Election;
import com.mdau.ushirika.module.election.entity.ElectionSeat;
import com.mdau.ushirika.module.election.enums.ElectionStatus;
import com.mdau.ushirika.module.election.repository.ElectionRepository;
import com.mdau.ushirika.module.election.repository.ElectionVoteReceiptRepository;
import com.mdau.ushirika.module.notification.enums.InAppNotificationCategory;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import com.mdau.ushirika.module.notification.service.NotificationCategory;
import com.mdau.ushirika.module.notification.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reminds active members who haven't finished voting in a VOTING_OPEN election, at 1 day before
 * voting closes and again on the closing day itself. There was previously no notification
 * infrastructure at all for elections -- no email, no in-app, nothing -- so this is new, not a
 * WhatsApp channel bolted onto an existing send. Runs daily at 09:00 UTC.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElectionReminderScheduler {

    private static final Set<Long> REMINDER_DAYS = Set.of(1L, 0L);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a");

    private final ElectionRepository            electionRepository;
    private final ElectionVoteReceiptRepository voteReceiptRepository;
    private final UserRepository                userRepository;
    private final EmailService                  emailService;
    private final NotificationDispatcher        notificationDispatcher;
    private final InAppNotificationService      notificationService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    @Scheduled(cron = "0 0 9 * * *", zone = "America/Chicago")
    public void sendVotingReminders() {
        LocalDate today = AppClock.today();
        int sent = 0;

        for (Election election : electionRepository.findAllByOrderByYearDescCreatedAtDesc()) {
            if (election.getStatus() != ElectionStatus.VOTING_OPEN || election.getVotingEnd() == null) continue;

            long daysUntilClose = ChronoUnit.DAYS.between(today, election.getVotingEnd().toLocalDate());
            if (!REMINDER_DAYS.contains(daysUntilClose)) continue;

            List<ElectionSeat> seats = election.getSeats();
            if (seats.isEmpty()) continue;

            List<User> members = userRepository.findAllByRole(UserRole.MEMBER);
            String formattedClose = election.getVotingEnd().format(DATE_FMT);
            String when = daysUntilClose == 0 ? "TODAY" : "TOMORROW";

            for (User member : members) {
                Set<UUID> votedSeatIds = new HashSet<>(
                        voteReceiptRepository.findVotedSeatIdsByElectionAndVoter(election.getId(), member.getId()));
                boolean hasUnvotedSeat = seats.stream().anyMatch(s -> !votedSeatIds.contains(s.getId()));
                if (!hasUnvotedSeat) continue; // already voted for every seat

                sendReminder(member, election, formattedClose, when);
                sent++;
            }
        }
        if (sent > 0) log.info("ElectionReminderScheduler: sent {} reminder(s)", sent);
    }

    private void sendReminder(User member, Election election, String formattedClose, String when) {
        String subject = "Voting closes " + when + " — " + election.getTitle();
        String portalUrl = siteUrl + "/portal/elections";
        String body = String.format(
                "Hi %s, voting for %s closes %s (%s) and you haven't finished casting your vote for " +
                "every seat. Vote now: %s",
                member.getFullName(), election.getTitle(), when.toLowerCase(), formattedClose, portalUrl);

        try {
            emailService.sendPlain(member.getEmail(), member.getFullName(), subject, toHtml(election, formattedClose, when, portalUrl));
        } catch (Exception e) {
            log.warn("Election reminder email failed for {}: {}", member.getEmail(), e.getMessage());
        }

        notificationDispatcher.dispatchWhatsApp(NotificationCategory.ELECTION_REMINDER,
                member.getPhone(), member.getFullName(), List.of(
                        member.getFullName(),
                        election.getTitle(),
                        when.toLowerCase() + " (" + formattedClose + ")",
                        portalUrl
                ));

        try {
            notificationService.createForUser(
                    member.getId(),
                    InAppNotificationCategory.ELECTION,
                    subject,
                    body,
                    "/portal/elections"
            );
        } catch (Exception e) {
            log.warn("Election reminder in-app notification failed for {}: {}", member.getEmail(), e.getMessage());
        }
    }

    private static String toHtml(Election election, String formattedClose, String when, String portalUrl) {
        return """
            <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
              <h2 style="color:#007834">Voting Closes %s</h2>
              <p><strong>%s</strong> — voting closes on <strong>%s</strong>.</p>
              <p>You haven't finished casting your vote for every seat yet. Every member's vote
                 matters for a fair result.</p>
              <p><a href="%s" style="display:inline-block;background:#007834;color:#fff;
                 padding:10px 22px;border-radius:999px;text-decoration:none;font-weight:600">
                 Vote Now
              </a></p>
              <p>— Ushirika Welfare Organization Team</p>
            </div>
            """.formatted(when, election.getTitle(), formattedClose, portalUrl);
    }
}
