package com.mdau.ushirika.module.program.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignProgramAdminRequest(

        @NotNull(message = "User is required")
        UUID userId
) {}
