package com.mdau.ushirika.module.donation.dto;

import com.mdau.ushirika.module.payment.enums.PreferredPaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record DonationInitRequest(

        /** Null means a general donation not tied to any campaign. */
        UUID campaignId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.00", message = "Minimum donation is $1.00")
        BigDecimal amount,

        @NotBlank(message = "Success URL is required")
        String successUrl,

        @NotBlank(message = "Cancel URL is required")
        String cancelUrl,

        /** Required for guests; optional for members (defaults to account name). */
        String donorName,

        /** Required for guests; optional for members (defaults to account email). */
        String donorEmail,

        String message,

        /** Null keeps Stripe's combined Card+Cash App page; a specific choice scopes the
         * session to just that one method, matching every other checkout entry point. */
        PreferredPaymentMethod paymentMethod
) {}
