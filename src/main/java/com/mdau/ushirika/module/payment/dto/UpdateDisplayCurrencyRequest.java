package com.mdau.ushirika.module.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateDisplayCurrencyRequest(

        @NotBlank(message = "Currency is required")
        String currency
) {}
