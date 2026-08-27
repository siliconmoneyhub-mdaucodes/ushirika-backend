package com.mdau.ushirika.module.manualpayment.dto;

import com.mdau.ushirika.module.manualpayment.entity.ManualPayment;
import com.mdau.ushirika.module.manualpayment.enums.ManualPaymentCategory;
import com.mdau.ushirika.module.manualpayment.enums.ManualPaymentStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ManualPaymentDto(
    UUID id,
    ManualPaymentCategory category,
    BigDecimal amount,
    String currency,
    UUID memberId,
    String memberName,
    String memberEmail,
    String payerName,
    String payerEmail,
    LocalDate paymentDate,
    String receiptNumber,
    String period,
    String notes,
    ManualPaymentStatus status,
    String recordedByName,
    String recordedByEmail,
    String approvedByName,
    String approvedByEmail,
    String rejectedByName,
    String rejectedByEmail,
    String rejectionReason,
    Instant createdAt,
    Instant updatedAt
) {
    public static ManualPaymentDto from(ManualPayment p) {
        return new ManualPaymentDto(
            p.getId(),
            p.getCategory(),
            p.getAmount(),
            p.getCurrency(),
            p.getMember() != null ? p.getMember().getId() : null,
            p.getMember() != null ? p.getMember().getFullName() : null,
            p.getMember() != null ? p.getMember().getEmail() : null,
            p.getPayerName(),
            p.getPayerEmail(),
            p.getPaymentDate(),
            p.getReceiptNumber(),
            p.getPeriod(),
            p.getNotes(),
            p.getStatus(),
            p.getRecordedBy().getFullName(),
            p.getRecordedBy().getEmail(),
            p.getApprovedBy() != null ? p.getApprovedBy().getFullName() : null,
            p.getApprovedBy() != null ? p.getApprovedBy().getEmail() : null,
            p.getRejectedBy() != null ? p.getRejectedBy().getFullName() : null,
            p.getRejectedBy() != null ? p.getRejectedBy().getEmail() : null,
            p.getRejectionReason(),
            AppClock.serverInstant(p.getCreatedAt()),
            AppClock.serverInstant(p.getUpdatedAt())
        );
    }
}
