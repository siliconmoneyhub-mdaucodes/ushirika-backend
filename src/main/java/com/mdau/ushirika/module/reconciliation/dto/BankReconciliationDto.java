package com.mdau.ushirika.module.reconciliation.dto;

import com.mdau.ushirika.module.reconciliation.entity.BankReconciliation;
import com.mdau.ushirika.common.util.AppClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BankReconciliationDto(
        UUID id,
        String scope,
        BigDecimal physicalBalance,
        BigDecimal expectedBalance,
        BigDecimal variance,
        String note,
        String recordedByName,
        String recordedByTitle,
        Instant recordedAt
) {
    public static BankReconciliationDto from(BankReconciliation r) {
        return new BankReconciliationDto(
                r.getId(), r.getScope(), r.getPhysicalBalance(), r.getExpectedBalance(), r.getVariance(),
                r.getNote(), r.getRecordedByName(), r.getRecordedByTitle(), AppClock.serverInstant(r.getRecordedAt())
        );
    }
}
