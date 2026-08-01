package com.mdau.ushirika.module.payment.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.payment.enums.PaymentBasketLedger;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
    name = "payment_basket_lines",
    indexes = {
        @Index(name = "idx_basket_line_basket", columnList = "basket_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentBasketLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "basket_id", nullable = false)
    private PaymentBasket basket;

    @Enumerated(EnumType.STRING)
    @Column(name = "ledger", nullable = false, length = 30)
    private PaymentBasketLedger ledger;

    /** Only meaningful for FINE and BENEVOLENCE_REPLENISHMENT, which target a specific row.
     * The other ledgers are resolved per-member at allocation time. */
    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
}
