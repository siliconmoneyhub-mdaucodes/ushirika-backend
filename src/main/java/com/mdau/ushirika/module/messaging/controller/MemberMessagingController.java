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

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MemberMessagingController {

    private final MessagingService messagingService;

    @GetMapping("/threads")
    public ResponseEntity<ApiResponse<List<ThreadSummaryDto>>> myThreads() {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.listMyThreads()));
    }

    @PostMapping("/threads")
    public ResponseEntity<ApiResponse<ThreadDetailDto>> startThread(@RequestBody StartThreadRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.startOrGetMyThread(req)));
    }

    @GetMapping("/threads/{id}")
    public ResponseEntity<ApiResponse<ThreadDetailDto>> getThread(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.getMyThread(id)));
    }

    @PostMapping("/threads/{id}/messages")
    public ResponseEntity<ApiResponse<MessageDto>> send(
            @PathVariable UUID id, @Valid @RequestBody SendMessageRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(messagingService.sendMyMessage(id, req)));
    }

    @PostMapping("/threads/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID id) {
        messagingService.markMyThreadRead(id);
        return ResponseEntity.ok(ApiResponse.ok("Marked read"));
    }
}
