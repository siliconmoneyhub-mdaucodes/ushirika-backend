package com.mdau.ushirika.module.messaging.dto;

import com.mdau.ushirika.module.messaging.enums.ThreadPriority;
import com.mdau.ushirika.module.messaging.enums.ThreadStatus;

import java.time.Instant;
import java.util.UUID;

public record ThreadSummaryDto(
        UUID id,
        String referenceNumber,
        UUID memberId,
        String memberName,
        String memberCode,
        UUID programId,
        String programName,
        ThreadPriority priority,
        ThreadStatus status,
        String lastMessagePreview,
        Instant lastMessageAt,
        boolean lastMessageFromMember,
        boolean unread,
        int unreadCount,
        Instant createdAt
) {}
