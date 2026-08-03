package com.mdau.ushirika.module.attendance.dto;

import java.time.LocalDateTime;

public record CheckInResultDto(
        String status,
        String message,
        LocalDateTime checkedInAt
) {}
