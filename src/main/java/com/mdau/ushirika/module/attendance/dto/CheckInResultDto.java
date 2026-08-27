package com.mdau.ushirika.module.attendance.dto;

import java.time.Instant;

public record CheckInResultDto(
        String status,
        String message,
        Instant checkedInAt
) {}
