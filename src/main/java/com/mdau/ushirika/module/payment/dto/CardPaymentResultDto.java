package com.mdau.ushirika.module.payment.dto;

import java.math.BigDecimal;

/**
 * Returned after an admin-entered card charge is attempted. If the card requires additional
 * authentication (3D Secure), status is "requires_action" and clientSecret must be passed to
 * stripe.confirmCardPayment() client-side to complete it. If status is "succeeded", the member
 * is credited once the payment_intent.succeeded webhook lands (same as every other payment).
 */
public record CardPaymentResultDto(
        String paymentIntentId,
        String status,
        String clientSecret,
        BigDecimal amount
) {}
