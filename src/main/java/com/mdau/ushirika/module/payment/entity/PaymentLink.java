package com.mdau.ushirika.module.payment.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.payment.enums.PaymentChannel;
import jakarta.persistence.*;
import lombok.*;

/**
 * Admin-managed payment link for one channel (Zelle, Venmo, CashApp, Stripe).
 * Unique per channel — one active record per payment method.
 */
@Entity
@Table(
    name = "payment_links",
    uniqueConstraints = @UniqueConstraint(name = "uq_pl_channel", columnNames = "channel"),
    indexes = @Index(name = "idx_pl_active", columnList = "active")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLink extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private PaymentChannel channel;

    /**
     * Zelle: phone or email. Venmo: @username. CashApp: $cashtag. Stripe: full payment URL.
     */
    @Column(nullable = false, length = 500)
    private String handle;

    /** Display name shown to the member, e.g. "Ushirika Welfare Organization". */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /** Step-by-step instructions shown to the member before they pay. */
    @Column(columnDefinition = "TEXT")
    private String instructions;

    /**
     * Optional deep-link URL to open the payment app directly (e.g. venmo://...).
     * If null the frontend derives a sensible default from the handle.
     */
    @Column(name = "deep_link_url", length = 500)
    private String deepLinkUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "display_order")
    @Builder.Default
    private int displayOrder = 0;
}
