package com.mdau.ushirika.module.messaging.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.messaging.dto.*;
import com.mdau.ushirika.module.messaging.enums.ThreadPriority;
import com.mdau.ushirika.module.messaging.enums.ThreadStatus;
import com.mdau.ushirika.module.messaging.service.MessagingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** General member <-> admin inquiries; path falls under SecurityConfig's /admin/** ADMIN/SUPERADMIN gate. */
@RestController
@RequestMapping("/admin/messages")
@RequiredArgsConstructor
public class AdminMessagingController {

    private final MessagingService messagingService;

    /** Bare array (capped at 200), urgent first then most recent. All filters optional. */
    @GetMapping("/threads")
    public ResponseEntity<ApiResponse<List<ThreadSummaryDto>>> threads(
            @RequestParam(required = false) ThreadStatus status,
            @RequestParam(required = false) ThreadPriority priority,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.listGeneralThreads(status, priority, q)));
    }

    @PostMapping("/threads")
    public ResponseEntity<ApiResponse<ThreadDetailDto>> startThread(@Valid @RequestBody StartStaffThreadRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Message sent",
                messagingService.startGeneralThreadWithMember(req.memberId(), req.body(), req.priority())));
    }

    @GetMapping("/threads/{id}")
    public ResponseEntity<ApiResponse<ThreadDetailDto>> getThread(
            @PathVariable UUID id,
            @RequestParam(required = false) Instant before,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.getGeneralThread(id, before, limit)));
    }

    @PostMapping("/threads/{id}/messages")
    public ResponseEntity<ApiResponse<MessageDto>> reply(
            @PathVariable UUID id, @Valid @RequestBody SendMessageRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.sendGeneralReply(id, req)));
    }

    @PostMapping("/threads/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID id) {
        messagingService.markGeneralThreadRead(id);
        return ResponseEntity.ok(ApiResponse.ok("Marked read"));
    }

    @PatchMapping("/threads/{id}/priority")
    public ResponseEntity<ApiResponse<ThreadSummaryDto>> setPriority(
            @PathVariable UUID id, @Valid @RequestBody SetPriorityRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.setGeneralThreadPriority(id, req.priority())));
    }

    @PostMapping("/threads/{id}/close")
    public ResponseEntity<ApiResponse<ThreadSummaryDto>> close(
            @PathVariable UUID id, @Valid @RequestBody(required = false) CloseThreadRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Conversation closed",
                messagingService.closeGeneralThread(id, req != null ? req.note() : null)));
    }

    @PostMapping("/threads/{id}/reopen")
    public ResponseEntity<ApiResponse<ThreadSummaryDto>> reopen(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Conversation reopened", messagingService.reopenGeneralThread(id)));
    }
}
