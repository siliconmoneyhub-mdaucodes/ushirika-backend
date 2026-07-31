package com.mdau.ushirika.module.program.dto;

import com.mdau.ushirika.module.program.enums.ProgramType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProgramRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 150)
        String name,

        @NotBlank(message = "Short description is required")
        @Size(max = 500)
        String shortDescription,

        @NotNull(message = "Program type is required")
        ProgramType type
) {}
