package com.mdau.ushirika.module.payment.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One row per member — unapplied money sitting on their account after every known
 * obligation (fines, dues, MGR, benevolence) has been settled. Positive only; the
 * member's true net position (this minus what they still owe) is a display-time
 * calculation, not stored here — see PaymentAllocationService.getBalance().
 */
@Entity
@Table(name = "member_credit_balances")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberCreditBalance extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "credit_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal creditAmount = BigDecimal.ZERO;
}
