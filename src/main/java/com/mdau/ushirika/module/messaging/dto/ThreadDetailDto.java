package com.mdau.ushirika.module.messaging.dto;

import com.mdau.ushirika.module.messaging.enums.ThreadPriority;
import com.mdau.ushirika.module.messaging.enums.ThreadStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ThreadDetailDto(
        UUID id,
        String referenceNumber,
        UUID memberId,
        String memberName,
        String memberCode,
        UUID programId,
        String programName,
        ThreadPriority priority,
        ThreadStatus status,
        Instant closedAt,
        String closedByName,
        List<MessageDto> messages,
        boolean hasMore,
        Instant oldestLoadedAt
) {}
