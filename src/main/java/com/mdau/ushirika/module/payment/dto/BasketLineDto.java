package com.mdau.ushirika.module.payment.dto;

import com.mdau.ushirika.module.payment.enums.PaymentBasketLedger;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record BasketLineDto(
        @NotNull PaymentBasketLedger ledger,
        UUID targetId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {}
