package com.mdau.ushirika.module.program.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ApplyToProgramsRequest(

        @NotEmpty(message = "Select at least one program")
        List<UUID> programIds
) {}
