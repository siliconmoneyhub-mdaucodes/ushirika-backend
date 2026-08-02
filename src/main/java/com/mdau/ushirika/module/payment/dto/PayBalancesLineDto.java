package com.mdau.ushirika.module.payment.dto;

import com.mdau.ushirika.module.payment.enums.PaymentBasketLedger;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** What a member selected to pay in "Pay My Balances" — amounts and ownership are re-validated
 * server-side against real balances before a basket is built; nothing here is trusted as-is. */
public record PayBalancesLineDto(
        @NotNull PaymentBasketLedger ledger,
        UUID targetId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {}
