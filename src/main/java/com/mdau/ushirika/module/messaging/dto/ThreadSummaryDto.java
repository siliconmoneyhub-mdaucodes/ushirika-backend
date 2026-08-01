package com.mdau.ushirika.module.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ThreadSummaryDto(
        UUID id,
        UUID memberId,
        String memberName,
        UUID programId,
        String programName,
        String lastMessagePreview,
        LocalDateTime lastMessageAt,
        boolean unread
) {}
