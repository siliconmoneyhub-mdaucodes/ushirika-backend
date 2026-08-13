package com.mdau.ushirika.module.reconciliation.dto;

import com.mdau.ushirika.module.reconciliation.entity.BankReconciliation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        LocalDateTime recordedAt
) {
    public static BankReconciliationDto from(BankReconciliation r) {
        return new BankReconciliationDto(
                r.getId(), r.getScope(), r.getPhysicalBalance(), r.getExpectedBalance(), r.getVariance(),
                r.getNote(), r.getRecordedByName(), r.getRecordedByTitle(), r.getRecordedAt()
        );
    }
}
