package com.mdau.ushirika.module.notification.controller;

import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.notification.dto.BroadcastRequest;
import com.mdau.ushirika.module.notification.dto.NotificationLogDto;
import com.mdau.ushirika.module.notification.enums.NotificationChannel;
import com.mdau.ushirika.module.notification.enums.NotificationStatus;
import com.mdau.ushirika.module.notification.repository.NotificationLogRepository;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/notifications")
@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Notifications — Admin", description = "View outbound email and SMS delivery logs")
public class AdminNotificationController {

    private final NotificationLogRepository  logRepository;
    private final InAppNotificationService   notificationService;
    private final UserRepository             userRepository;
    private final AuditLogService            auditLogService;

    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','FINANCIAL_ADMIN','FINANCIAL_OFFICIAL')")
    @Operation(summary = "List notification logs, optionally filtered by channel and/or status")
    public ResponseEntity<ApiResponse<PagedResponse<NotificationLogDto>>> logs(
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        var result = (channel != null && status != null)
                ? logRepository.findAllByChannelAndStatusOrderByCreatedAtDesc(channel, status, pageable)
                : (channel != null)
                        ? logRepository.findAllByChannelOrderByCreatedAtDesc(channel, pageable)
                        : (status != null)
                                ? logRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
                                : logRepository.findAllByOrderByCreatedAtDesc(pageable);

        return ResponseEntity.ok(ApiResponse.ok("Logs retrieved",
                PagedResponse.of(result.map(NotificationLogDto::from))));
    }

    @PostMapping("/broadcast")
    @Operation(summary = "Send an announcement to all members (in-app always; email/SMS optional)")
    public ResponseEntity<ApiResponse<Void>> broadcast(
            @Valid @RequestBody BroadcastRequest req, Authentication auth) {
        notificationService.broadcast(req);

        User admin = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
        auditLogService.log(admin, "BROADCAST_SENT", "Notification", null,
                "Broadcast \"" + req.title() + "\" sent to all members by " + admin.getFullName()
                        + (req.channels() != null && !req.channels().isEmpty() ? " via " + req.channels() : ""));

        return ResponseEntity.ok(ApiResponse.ok("Broadcast queued for all members"));
    }

    @GetMapping("/logs/recipient")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','FINANCIAL_ADMIN','FINANCIAL_OFFICIAL')")
    @Operation(summary = "Look up all notifications sent to a specific email or phone")
    public ResponseEntity<ApiResponse<PagedResponse<NotificationLogDto>>> byRecipient(
            @RequestParam String recipient,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.ok("Logs retrieved",
                PagedResponse.of(logRepository
                        .findAllByRecipientOrderByCreatedAtDesc(recipient, pageable)
                        .map(NotificationLogDto::from))));
    }
}
