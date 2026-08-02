package com.mdau.ushirika.module.member.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

/** benevolenceAmount/benevolenceApplicationId are both null/omitted to defer Benevolence entirely. */
public record OnboardingCheckoutRequest(
        BigDecimal benevolenceAmount,
        UUID benevolenceApplicationId,
        @NotBlank String successUrl,
        @NotBlank String cancelUrl
) {}
