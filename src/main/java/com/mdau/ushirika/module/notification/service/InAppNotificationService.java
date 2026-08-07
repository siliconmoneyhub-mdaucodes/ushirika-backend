package com.mdau.ushirika.module.notification.service;

import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.notification.dto.BroadcastRequest;
import com.mdau.ushirika.module.notification.dto.InAppNotificationDto;
import com.mdau.ushirika.module.notification.entity.InAppNotification;
import com.mdau.ushirika.module.notification.enums.InAppNotificationCategory;
import com.mdau.ushirika.module.notification.repository.InAppNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InAppNotificationService {

    private final InAppNotificationRepository inAppRepo;
    private final UserRepository              userRepository;
    private final MemberProfileRepository     memberProfileRepository;
    private final EmailService                emailService;
    private final SmsService                  smsService;

    // ── Member-facing ─────────────────────────────────────────────────────────

    public PagedResponse<InAppNotificationDto> getMyNotifications(UUID userId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PagedResponse.of(
                inAppRepo.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
                         .map(InAppNotificationDto::from)
        );
    }

    public long getUnreadCount(UUID userId) {
        return inAppRepo.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        InAppNotification n = inAppRepo.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!n.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Notification not found");
        }
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(LocalDateTime.now());
            inAppRepo.save(n);
        }
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return inAppRepo.markAllReadByUserId(userId);
    }

    // ── Internal helpers (called from other services) ─────────────────────────

    public void createForUser(UUID userId,
                               InAppNotificationCategory category,
                               String title,
                               String body,
                               String actionUrl) {
        inAppRepo.save(InAppNotification.builder()
                .userId(userId)
                .category(category)
                .title(title)
                .body(body)
                .actionUrl(actionUrl)
                .build());
    }

    // ── Admin broadcast (all members) ─────────────────────────────────────────

    @Async
    @Transactional
    public void broadcast(BroadcastRequest req) {
        List<User> members = memberProfileRepository.findAllMemberUsers();
        notifyUsers(members, req);
        log.info("Broadcast '{}' sent to {} members", req.title(), members.size());
    }

    // ── Targeted notifications (one member, or a pre-resolved roster e.g. a program's members) ──

    @Async
    @Transactional
    public void notifyMember(UUID memberId, BroadcastRequest req) {
        User target = userRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
        notifyUsers(List.of(target), req);
        log.info("Direct notification '{}' sent to {}", req.title(), target.getEmail());
    }

    @Async
    @Transactional
    public void notifyUsers(List<User> recipients, BroadcastRequest req) {
        boolean sendEmail = req.channels() != null && req.channels().contains("EMAIL");
        boolean sendSms   = req.channels() != null && req.channels().contains("SMS");

        for (var user : recipients) {
            // Always create in-app notification
            inAppRepo.save(InAppNotification.builder()
                    .userId(user.getId())
                    .category(req.category())
                    .title(req.title())
                    .body(req.body())
                    .actionUrl(req.actionUrl())
                    .build());

            if (sendEmail) {
                try {
                    emailService.sendPlain(user.getEmail(), user.getFullName(), req.title(), toHtml(req.body()));
                } catch (Exception e) {
                    log.warn("Notification email failed for {}: {}", user.getEmail(), e.getMessage());
                }
            }

            if (sendSms && user.getPhone() != null) {
                try {
                    smsService.send(user.getPhone(), user.getFullName(), req.title() + "\n" + req.body());
                } catch (Exception e) {
                    log.warn("Notification SMS failed for {}: {}", user.getPhone(), e.getMessage());
                }
            }
        }
    }

    private static String toHtml(String text) {
        return "<p>" + text.replace("\n", "<br/>") + "</p>";
    }
}
