package com.mdau.ushirika.module.benevolence.dto;

import com.mdau.ushirika.module.benevolence.entity.EnrollmentPayment;
import com.mdau.ushirika.common.util.AppClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EnrollmentPaymentDto(
        UUID id,
        BigDecimal amount,
        String paymentMethod,
        String paymentReference,
        Instant paidAt,
        String notes
) {
    public static EnrollmentPaymentDto from(EnrollmentPayment p) {
        return new EnrollmentPaymentDto(
                p.getId(), p.getAmount(), p.getPaymentMethod(),
                p.getPaymentReference(), AppClock.serverInstant(p.getPaidAt()), p.getNotes()
        );
    }
}
