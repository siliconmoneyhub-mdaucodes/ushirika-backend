package com.mdau.ushirika.module.benevolence.dto;

import com.mdau.ushirika.module.payment.enums.PreferredPaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Starts the one-time paid-application checkout: at least $100 toward the $600 enrollment fee,
 * or the full amount now. paymentMethod is optional -- null keeps the combined Card+Cash App page. */
public record BenevolenceApplicationCheckoutRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank String successUrl,
        @NotBlank String cancelUrl,
        PreferredPaymentMethod paymentMethod
) {}
