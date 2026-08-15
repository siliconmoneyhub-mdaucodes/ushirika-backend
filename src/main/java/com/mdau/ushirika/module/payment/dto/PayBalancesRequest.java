package com.mdau.ushirika.module.payment.dto;

import com.mdau.ushirika.module.payment.enums.PreferredPaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** lines may be empty if the member only wants to pay an outstanding fine — those are
 * force-included server-side regardless of what's selected here.
 * paymentMethod is optional — null keeps the previous combined Card+Cash App page with automatic
 * card-only fallback; set it to scope the session to exactly the method the member picked. */
public record PayBalancesRequest(
        @Valid List<PayBalancesLineDto> lines,
        @NotBlank String successUrl,
        @NotBlank String cancelUrl,
        PreferredPaymentMethod paymentMethod
) {}
