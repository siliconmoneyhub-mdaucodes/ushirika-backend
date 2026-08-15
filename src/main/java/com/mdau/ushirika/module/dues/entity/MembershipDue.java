package com.mdau.ushirika.module.dues.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.dues.enums.DuesStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static java.math.BigDecimal.ZERO;

@Entity
@Table(
    name = "membership_dues",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_dues_user_year",
        columnNames = {"user_id", "year"}
    ),
    indexes = {
        @Index(name = "idx_dues_user_id",  columnList = "user_id"),
        @Index(name = "idx_dues_status",   columnList = "status"),
        @Index(name = "idx_dues_year",     columnList = "year"),
        @Index(name = "idx_dues_due_date", columnList = "due_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipDue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_dues_user"))
    private User user;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Running total of all VERIFIED installment payments toward this annual due. */
    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = ZERO;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DuesStatus status;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_reference", length = 255)
    private String paymentReference;

    @Column(length = 500)
    private String notes;

    /**
     * Number of months from when this due was created through the community's October cutoff
     * (inclusive), minimum 1. The total owed is always the full {@code amount} regardless of
     * when a member joins -- this only spreads it thinner the later in the year they join, so
     * {@link #recommendedMonthlyAmount()} doesn't read as one big lump-sum ask right after
     * registration. Confirmed 2026-08-15: "they should contribute 100 usd no matter when they
     * join thus it is the 100 divided by the number of months remaining to get to October."
     */
    public int remainingMonths() {
        LocalDate start = getCreatedAt() != null ? getCreatedAt().toLocalDate() : LocalDate.of(year, 1, 1);
        LocalDate cutoff = LocalDate.of(year, 10, 1);
        return Math.max(1, (cutoff.getYear() - start.getYear()) * 12 + (cutoff.getMonthValue() - start.getMonthValue()) + 1);
    }

    public BigDecimal recommendedMonthlyAmount() {
        return amount.divide(BigDecimal.valueOf(remainingMonths()), 2, RoundingMode.HALF_UP);
    }
}
