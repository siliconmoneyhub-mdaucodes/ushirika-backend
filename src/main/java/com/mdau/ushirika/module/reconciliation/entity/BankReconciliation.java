package com.mdau.ushirika.module.reconciliation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A point-in-time record of a physical bank balance an admin observed (e.g. from a statement),
 * checked against the ledger-derived expected balance at that same moment. {@code scope} is null
 * for an org-wide check, or a ledger entityType (e.g. "MGR_CONTRIBUTION") for a per-program check
 * -- the same keys FinanceDashboardDto.Balances#byProgram uses, so the two line up directly.
 */
@Entity
@Table(name = "bank_reconciliations", indexes = {
        @Index(name = "idx_reconciliation_scope",      columnList = "scope"),
        @Index(name = "idx_reconciliation_recorded_at", columnList = "recorded_at"),
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankReconciliation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scope", length = 50)
    private String scope;

    @Column(name = "physical_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal physicalBalance;

    @Column(name = "expected_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedBalance;

    @Column(name = "variance", nullable = false, precision = 12, scale = 2)
    private BigDecimal variance;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "recorded_by_id", nullable = false)
    private UUID recordedById;

    @Column(name = "recorded_by_name", nullable = false, length = 200)
    private String recordedByName;

    @Column(name = "recorded_by_title", length = 30)
    private String recordedByTitle;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        this.recordedAt = LocalDateTime.now();
    }
}
