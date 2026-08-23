package com.mdau.ushirika.module.welfare.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.welfare.enums.DisbursementMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Records the actual payment made to the member after a welfare request is APPROVED.
 * Disbursed amount may differ from the requested amount (partial or adjusted).
 */
@Entity
@Table(
    name = "welfare_disbursements",
    indexes = {
        @Index(name = "idx_wd_disbursed_by",  columnList = "disbursed_by_id"),
        @Index(name = "idx_wd_disbursed_at",  columnList = "disbursed_at"),
        @Index(name = "idx_wd_method",        columnList = "method")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WelfareDisbursement extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "welfare_request_id", nullable = false, unique = true,
                foreignKey = @ForeignKey(name = "fk_wd_request"))
    private WelfareRequest welfareRequest;

    @Column(name = "amount_disbursed", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountDisbursed;

    /** Welfare Fund is USD-only -- see WelfareService, which has no path that lets this be
     * set to anything else. */
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private DisbursementMethod method;

    /**
     * Bank reference, M-Pesa transaction code, cheque number, etc.
     * Optional — not applicable for cash.
     */
    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disbursed_by_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_wd_disbursed_by"))
    private User disbursedBy;

    @Column(name = "disbursed_at", nullable = false)
    private LocalDateTime disbursedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
