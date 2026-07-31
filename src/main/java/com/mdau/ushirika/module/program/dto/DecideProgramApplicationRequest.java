package com.mdau.ushirika.module.program.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecideProgramApplicationRequest(

        @NotNull(message = "Decision is required")
        Decision decision,

        @Size(max = 500)
        String reason

) {
    public enum Decision { APPROVE, REJECT }
}
