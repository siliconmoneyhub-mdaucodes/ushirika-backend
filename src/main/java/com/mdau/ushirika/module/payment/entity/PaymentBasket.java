package com.mdau.ushirika.module.payment.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A single Stripe Checkout Session that may cover several unrelated ledgers at once
 * (e.g. registration fee + Benevolence enrollment, or dues + a fine + an MGR contribution).
 * Created PENDING before redirecting to Stripe; the webhook looks it up by sessionId and
 * allocates each line to its target ledger on completion. See PaymentBasketService.
 */
@Entity
@Table(
    name = "payment_baskets",
    indexes = {
        @Index(name = "idx_basket_member",     columnList = "member_id"),
        @Index(name = "idx_basket_session",    columnList = "session_id", unique = true)
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentBasket extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private User member;

    @Column(name = "session_id", nullable = false, unique = true, length = 100)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @OneToMany(mappedBy = "basket", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PaymentBasketLine> lines = new ArrayList<>();
}
