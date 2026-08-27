package com.mdau.ushirika.module.dues.dto;

import com.mdau.ushirika.module.dues.entity.DuesPayment;
import com.mdau.ushirika.common.util.AppClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DuesPaymentDto(
        UUID id,
        UUID duesId,
        int duesYear,
        UUID memberId,
        String memberName,
        String email,
        BigDecimal amount,
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
    public static DuesPaymentDto from(DuesPayment p) {
        return new DuesPaymentDto(
                p.getId(),
                p.getDues().getId(),
                p.getDues().getYear(),
                p.getMember().getId(),
                p.getMember().getFullName(),
                p.getMember().getEmail(),
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

    public static DuesPaymentDto memberView(DuesPayment p) {
        return new DuesPaymentDto(
                p.getId(),
                p.getDues().getId(),
                p.getDues().getYear(),
                p.getMember().getId(),
                p.getMember().getFullName(),
                p.getMember().getEmail(),
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
