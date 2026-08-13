package com.mdau.ushirika.module.reconciliation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** scope=null (or omitted) records an org-wide check; otherwise a ledger entityType such as
 *  "MGR_CONTRIBUTION" -- see GET /admin/reconciliation/summary for the valid set. */
public record RecordReconciliationRequest(

        String scope,

        @NotNull(message = "Physical balance is required")
        BigDecimal physicalBalance,

        @Size(max = 1000)
        String note
) {}
