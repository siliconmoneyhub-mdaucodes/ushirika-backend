package com.mdau.ushirika.module.attendance.dto;

import com.mdau.ushirika.module.attendance.entity.FinePayment;
import com.mdau.ushirika.common.util.AppClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FinePaymentDto(
        UUID id,
        UUID fineId,
        UUID memberId,
        String memberName,
        String email,
        String fineReason,
        BigDecimal fineAmount,
        BigDecimal submittedAmount,
        String paymentMode,
        String memberTxReference,
        String adminTxReference,
        String status,
        String rejectionReason,
        String verifiedByName,
        Instant verifiedAt,
        String notes,
        Instant createdAt
) {
    public static FinePaymentDto from(FinePayment p) {
        return new FinePaymentDto(
                p.getId(),
                p.getFine().getId(),
                p.getMember().getId(),
                p.getMember().getFullName(),
                p.getMember().getEmail(),
                p.getFine().getReason(),
                p.getFine().getAmount(),
                p.getAmount(),
                p.getPaymentMode().name(),
                p.getMemberTxReference(),
                p.getAdminTxReference(),
                p.getStatus().name(),
                p.getRejectionReason(),
                p.getVerifiedBy() != null ? p.getVerifiedBy().getFullName() : null,
                AppClock.serverInstant(p.getVerifiedAt()),
                p.getNotes(),
                AppClock.serverInstant(p.getCreatedAt())
        );
    }

    public static FinePaymentDto memberView(FinePayment p) {
        return new FinePaymentDto(
                p.getId(),
                p.getFine().getId(),
                p.getMember().getId(),
                p.getMember().getFullName(),
                p.getMember().getEmail(),
                p.getFine().getReason(),
                p.getFine().getAmount(),
                p.getAmount(),
                p.getPaymentMode().name(),
                p.getMemberTxReference(),
                null,
                p.getStatus().name(),
                p.getRejectionReason(),
                null,
                AppClock.serverInstant(p.getVerifiedAt()),
                p.getNotes(),
                AppClock.serverInstant(p.getCreatedAt())
        );
    }
}
