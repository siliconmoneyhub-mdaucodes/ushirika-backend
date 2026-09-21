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

/** Program coordinator inbox; self-service, no ADMIN role required (see ProgramAdminController). */
@RestController
@RequestMapping("/programs/mine/{programId}/messages")
@RequiredArgsConstructor
public class ProgramMessagingController {

    private final MessagingService messagingService;

    @GetMapping("/threads")
    public ResponseEntity<ApiResponse<List<ThreadSummaryDto>>> threads(
            @PathVariable UUID programId,
            @RequestParam(required = false) ThreadStatus status,
            @RequestParam(required = false) ThreadPriority priority,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.listProgramThreads(programId, status, priority, q)));
    }

    @PostMapping("/threads")
    public ResponseEntity<ApiResponse<ThreadDetailDto>> startThread(
            @PathVariable UUID programId, @Valid @RequestBody StartStaffThreadRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Message sent",
                messagingService.startProgramThreadWithMember(programId, req.memberId(), req.body(), req.priority())));
    }

    @GetMapping("/threads/{id}")
    public ResponseEntity<ApiResponse<ThreadDetailDto>> getThread(
            @PathVariable UUID programId, @PathVariable UUID id,
            @RequestParam(required = false) Instant before,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.getProgramThread(programId, id, before, limit)));
    }

    @PostMapping("/threads/{id}/messages")
    public ResponseEntity<ApiResponse<MessageDto>> reply(
            @PathVariable UUID programId, @PathVariable UUID id, @Valid @RequestBody SendMessageRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.sendProgramReply(programId, id, req)));
    }

    @PostMapping("/threads/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable UUID programId, @PathVariable UUID id) {
        messagingService.markProgramThreadRead(programId, id);
        return ResponseEntity.ok(ApiResponse.ok("Marked read"));
    }

    @PatchMapping("/threads/{id}/priority")
    public ResponseEntity<ApiResponse<ThreadSummaryDto>> setPriority(
            @PathVariable UUID programId, @PathVariable UUID id, @Valid @RequestBody SetPriorityRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.setProgramThreadPriority(programId, id, req.priority())));
    }

    @PostMapping("/threads/{id}/close")
    public ResponseEntity<ApiResponse<ThreadSummaryDto>> close(
            @PathVariable UUID programId, @PathVariable UUID id,
            @Valid @RequestBody(required = false) CloseThreadRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Conversation closed",
                messagingService.closeProgramThread(programId, id, req != null ? req.note() : null)));
    }

    @PostMapping("/threads/{id}/reopen")
    public ResponseEntity<ApiResponse<ThreadSummaryDto>> reopen(
            @PathVariable UUID programId, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Conversation reopened", messagingService.reopenProgramThread(programId, id)));
    }
}
