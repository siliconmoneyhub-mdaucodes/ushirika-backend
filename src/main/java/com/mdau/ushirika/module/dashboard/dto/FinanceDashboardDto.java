package com.mdau.ushirika.module.dashboard.dto;

import com.mdau.ushirika.module.dashboard.dto.MonthlySeriesDto.MonthlyPoint;

import java.math.BigDecimal;
import java.util.List;

/** Cross-department finance health: the four modules with no portfolio-wide aggregates elsewhere. */
public record FinanceDashboardDto(
        DuesHealth dues,
        BenevolenceHealth benevolence,
        LoanPortfolio loans,
        MgrHealth mgr
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
}
