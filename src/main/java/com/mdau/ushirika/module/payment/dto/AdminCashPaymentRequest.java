package com.mdau.ushirika.module.payment.dto;

import com.mdau.ushirika.module.payment.enums.PreferredPaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** An admin processing a cash payment they physically received from a member. The admin pays
 * this exact amount themselves via the resulting Stripe Checkout session — the member is only
 * credited once that succeeds, never on this request alone.
 * paymentMethod is optional — null keeps the previous combined Card+Cash App page with automatic
 * card-only fallback; set it to scope the session to exactly the method the payer picked on our site. */
public record AdminCashPaymentRequest(
        @NotNull UUID memberId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank String successUrl,
        @NotBlank String cancelUrl,
        PreferredPaymentMethod paymentMethod
) {}
