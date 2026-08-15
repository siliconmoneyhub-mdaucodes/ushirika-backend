package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.payment.enums.PreferredPaymentMethod;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

/** benevolenceAmount/benevolenceApplicationId are both null/omitted to defer Benevolence entirely.
 * paymentMethod null keeps the old combined Card+Cash App page; CARD or CASH_APP scopes the
 * session to just that one method, matching every other checkout entry point in the app. */
public record OnboardingCheckoutRequest(
        BigDecimal benevolenceAmount,
        UUID benevolenceApplicationId,
        @NotBlank String successUrl,
        @NotBlank String cancelUrl,
        PreferredPaymentMethod paymentMethod
) {}
