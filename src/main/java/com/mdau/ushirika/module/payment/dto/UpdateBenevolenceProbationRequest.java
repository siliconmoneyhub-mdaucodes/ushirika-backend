package com.mdau.ushirika.module.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateBenevolenceProbationRequest(

        @NotNull(message = "Months is required")
        @Min(value = 1, message = "Probation period must be at least 1 month")
        Integer months
) {}
