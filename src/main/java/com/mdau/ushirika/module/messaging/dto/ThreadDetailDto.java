package com.mdau.ushirika.module.messaging.dto;

import java.util.List;
import java.util.UUID;

public record ThreadDetailDto(
        UUID id,
        UUID memberId,
        String memberName,
        UUID programId,
        String programName,
        List<MessageDto> messages
) {}
