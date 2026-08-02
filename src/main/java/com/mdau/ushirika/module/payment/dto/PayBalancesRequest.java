package com.mdau.ushirika.module.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** lines may be empty if the member only wants to pay an outstanding fine — those are
 * force-included server-side regardless of what's selected here. */
public record PayBalancesRequest(
        @Valid List<PayBalancesLineDto> lines,
        @NotBlank String successUrl,
        @NotBlank String cancelUrl
) {}
