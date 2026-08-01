package com.mdau.ushirika.module.messaging.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.messaging.dto.*;
import com.mdau.ushirika.module.messaging.service.MessagingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Program coordinator inbox — self-service, no ADMIN role required (see ProgramAdminController). */
@RestController
@RequestMapping("/programs/mine/{programId}/messages")
@RequiredArgsConstructor
public class ProgramMessagingController {

    private final MessagingService messagingService;

    @GetMapping("/threads")
    public ResponseEntity<ApiResponse<List<ThreadSummaryDto>>> threads(@PathVariable UUID programId) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.listProgramThreads(programId)));
    }

    @GetMapping("/threads/{id}")
    public ResponseEntity<ApiResponse<ThreadDetailDto>> getThread(
            @PathVariable UUID programId, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.getProgramThread(programId, id)));
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
}
