package com.mdau.ushirika.module.dashboard.dto;

import com.mdau.ushirika.module.dashboard.dto.MonthlySeriesDto.MonthlyPoint;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Cross-department finance health: the four modules with no portfolio-wide aggregates elsewhere. */
public record FinanceDashboardDto(
        DuesHealth dues,
        BenevolenceHealth benevolence,
        LoanPortfolio loans,
        MgrHealth mgr,
        Balances balances
) {
    public record DuesHealth(
            int year,
            BigDecimal totalBilled,
            BigDecimal totalCollected,
            double collectionRatePercent
    ) {}

    public record BenevolenceHealth(
            BigDecimal totalPaidOut,
            long pendingClaims,
            List<MonthlyPoint> monthly
    ) {}

    public record LoanPortfolio(
            long activeCount,
            long overdueCount,
            long defaultedCount,
            BigDecimal outstandingPrincipal
    ) {}

    public record MgrHealth(
            BigDecimal totalContributed,
            List<MonthlyPoint> monthly
    ) {}

    /** All-time running balance derived from the money ledger (Finance Visibility plan, Phase 4) --
     *  cumulative IN minus OUT per program (entityType), plus the org-wide net across all programs.
     *  Unlike Money Flow's date-filtered totals, this is never scoped to a range: it's "how much do
     *  we currently hold," not "what moved in a period." */
    public record Balances(
            BigDecimal orgWideNet,
            Map<String, BigDecimal> byProgram
    ) {}
}
