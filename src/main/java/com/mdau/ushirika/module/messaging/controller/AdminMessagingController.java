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

/** General member <-> admin inquiries — path falls under SecurityConfig's /admin/** ADMIN/SUPERADMIN gate. */
@RestController
@RequestMapping("/admin/messages")
@RequiredArgsConstructor
public class AdminMessagingController {

    private final MessagingService messagingService;

    @GetMapping("/threads")
    public ResponseEntity<ApiResponse<List<ThreadSummaryDto>>> threads() {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.listGeneralThreads()));
    }

    @GetMapping("/threads/{id}")
    public ResponseEntity<ApiResponse<ThreadDetailDto>> getThread(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.getGeneralThread(id)));
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
}
