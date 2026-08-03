package com.mdau.ushirika.module.attendance.dto;

import java.util.UUID;

/** The QR payload is "{meetingId}:{code}" — the member scanner extracts both without needing a separate meeting lookup. */
public record CheckInCodeDto(
        UUID meetingId,
        String code,
        long expiresAtEpochMillis,
        int refreshIntervalSeconds
) {}
