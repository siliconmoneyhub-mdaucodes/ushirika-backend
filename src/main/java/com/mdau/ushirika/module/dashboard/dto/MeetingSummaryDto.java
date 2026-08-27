package com.mdau.ushirika.module.dashboard.dto;

import java.time.Instant;
import java.util.UUID;

public record MeetingSummaryDto(
        UUID id,
        String title,
        Instant meetingDate
) {
}
