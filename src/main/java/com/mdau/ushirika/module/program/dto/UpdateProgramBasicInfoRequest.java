package com.mdau.ushirika.module.program.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProgramBasicInfoRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 150)
        String name,

        @NotBlank(message = "Short description is required")
        @Size(max = 500)
        String shortDescription
) {}
