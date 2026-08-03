package com.mdau.ushirika.module.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemberCheckInRequest(
        @NotBlank(message = "Scan code is required") String code,
        @NotNull(message = "Location is required") Double lat,
        @NotNull(message = "Location is required") Double lng
) {}
