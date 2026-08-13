package com.mdau.ushirika.module.reconciliation.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReconciliationSummaryDto(
        ScopeSummary orgWide,
        List<ScopeSummary> byProgram
) {
    /** scope=null for the org-wide row. currentExpected is always live (recomputed from the
     *  ledger right now); lastRecorded* reflects the most recent snapshot for this scope, if any. */
    public record ScopeSummary(
            String scope,
            BigDecimal currentExpected,
            BankReconciliationDto lastRecorded
    ) {}
}
