package com.mdau.ushirika.module.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitExcuseRequest(
        @NotBlank(message = "Please explain what happened") @Size(max = 2000) String reason,
        String evidenceUrl
) {}
