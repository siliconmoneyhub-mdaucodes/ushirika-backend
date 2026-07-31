package com.mdau.ushirika.module.payment.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.payment.enums.PaymentMode;
import com.mdau.ushirika.module.payment.enums.PeerPaymentPurpose;
import com.mdau.ushirika.module.payment.enums.PeerPaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A Zelle / Venmo / CashApp payment self-reported by the member.
 * Two-sided verification: the member enters the TX reference they sent,
 * the admin enters the reference they received. Both must match for the
 * contribution to be confirmed.
 */
@Entity
@Table(
    name = "peer_payments",
    indexes = {
        @Index(name = "idx_pp_member_id",     columnList = "member_id"),
        @Index(name = "idx_pp_status",        columnList = "status"),
        @Index(name = "idx_pp_member_status", columnList = "member_id, status"),
        @Index(name = "idx_pp_created_at",    columnList = "created_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeerPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_pp_member"))
    private User member;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 10)
    private PaymentMode paymentMode;

    /**
     * TX reference entered by the member immediately after payment.
     * Zelle: Confirmation Number | Venmo: Transaction ID | CashApp: Transaction ID
     */
    @Column(name = "member_tx_reference", nullable = false, length = 100)
    private String memberTxReference;

    /**
     * TX reference entered by the admin from their own app when verifying.
     * Set only when admin clicks "Verify". Compared case-insensitively to memberTxReference.
     */
    @Column(name = "admin_tx_reference", length = 100)
    private String adminTxReference;

    /** Contribution period this covers — e.g. "2025-06", "2025-Q2", "2025". */
    @Column(name = "period", length = 10)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    @Builder.Default
    private PeerPaymentPurpose purpose = PeerPaymentPurpose.DUES;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** Cloudinary URL of a screenshot proving the payment was sent. */
    @Column(name = "proof_image_url", length = 1000)
    private String proofImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private PeerPaymentStatus status = PeerPaymentStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_id",
                foreignKey = @ForeignKey(name = "fk_pp_verified_by"))
    private User verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
}
