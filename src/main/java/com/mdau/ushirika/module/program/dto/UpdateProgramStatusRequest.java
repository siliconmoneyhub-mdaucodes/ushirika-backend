package com.mdau.ushirika.module.program.dto;

import com.mdau.ushirika.module.program.enums.ProgramStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateProgramStatusRequest(

        @NotNull(message = "Status is required")
        ProgramStatus status
) {}
