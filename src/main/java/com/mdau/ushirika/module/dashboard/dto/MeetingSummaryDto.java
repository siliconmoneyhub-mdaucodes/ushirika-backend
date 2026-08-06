package com.mdau.ushirika.module.dashboard.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MeetingSummaryDto(
        UUID id,
        String title,
        LocalDateTime meetingDate
) {
}
