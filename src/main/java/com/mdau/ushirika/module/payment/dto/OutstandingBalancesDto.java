package com.mdau.ushirika.module.payment.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OutstandingBalancesDto(
        BigDecimal duesBalance,
        BenevolenceBalance benevolence,
        BigDecimal mgrBalance,
        List<ReplenishmentItem> replenishments,
        List<FineItem> fines,
        List<ContributionPlanDto> contributionPlans
) {
    public record BenevolenceBalance(BigDecimal balance, String status) {}
    public record ReplenishmentItem(UUID id, BigDecimal amountDue) {}
    /** mandatory is always true today — fines can never be excluded from a "pay my balances" checkout. */
    public record FineItem(UUID id, BigDecimal amount, String reason, boolean mandatory) {}
}
