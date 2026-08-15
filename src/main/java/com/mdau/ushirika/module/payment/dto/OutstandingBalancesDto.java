package com.mdau.ushirika.module.payment.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OutstandingBalancesDto(
        BigDecimal duesBalance,
        DuesGuidance duesGuidance,
        BenevolenceBalance benevolence,
        BigDecimal mgrBalance,
        List<ReplenishmentItem> replenishments,
        List<FineItem> fines,
        List<ContributionPlanDto> contributionPlans
) {
    /** Recommended monthly pace toward duesBalance -- informational only, the total owed is
     * always the full annual fee. Null when nothing is owed. */
    public record DuesGuidance(int remainingMonths, BigDecimal recommendedMonthlyAmount) {}
    public record BenevolenceBalance(BigDecimal balance, String status) {}
    public record ReplenishmentItem(UUID id, BigDecimal amountDue) {}
    /** mandatory is always true today — fines can never be excluded from a "pay my balances" checkout. */
    public record FineItem(UUID id, BigDecimal amount, String reason, boolean mandatory) {}
}
