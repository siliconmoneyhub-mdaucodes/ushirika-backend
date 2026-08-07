package com.mdau.ushirika.module.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An admin entering a member's card details directly (relayed by phone or in person) via
 * Stripe Elements — the raw card number never reaches our backend, only the tokenized
 * paymentMethodId Stripe.js produces client-side. The member's card pays, not the admin's.
 */
public record AdminCardEntryRequest(
        @NotNull UUID memberId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank String paymentMethodId
) {}
