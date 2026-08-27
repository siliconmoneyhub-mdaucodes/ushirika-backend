package com.mdau.ushirika.module.benevolence.dto;

import com.mdau.ushirika.module.benevolence.entity.ReplenishmentPayment;
import com.mdau.ushirika.module.benevolence.enums.ReplenishmentPaymentStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReplenishmentPaymentDto(
        UUID id,
        UUID replenishmentId,
        UUID enrollmentId,
        String memberName,
        String memberId,
        BigDecimal amountDue,
        BigDecimal amountPaid,
        Instant paidAt,
        String paymentMethod,
        String paymentReference,
        ReplenishmentPaymentStatus status
) {
    public static ReplenishmentPaymentDto from(ReplenishmentPayment p, String memberId) {
        String fullName = p.getEnrollment().getUser().getFullName();

        return new ReplenishmentPaymentDto(
                p.getId(), p.getReplenishment().getId(), p.getEnrollment().getId(),
                fullName, memberId, p.getAmountDue(), p.getAmountPaid(),
                AppClock.serverInstant(p.getPaidAt()), p.getPaymentMethod(), p.getPaymentReference(), p.getStatus()
        );
    }
}
