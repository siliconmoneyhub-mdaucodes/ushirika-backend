package com.mdau.ushirika.module.loan.dto;

import com.mdau.ushirika.module.loan.entity.LoanInstallment;
import com.mdau.ushirika.module.loan.enums.InstallmentStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LoanInstallmentDto(
        UUID id,
        int installmentNumber,
        LocalDate dueDate,
        BigDecimal principal,
        BigDecimal interest,
        BigDecimal totalDue,
        BigDecimal amountPaid,
        BigDecimal balance,
        InstallmentStatus status,
        Instant paidAt,
        String paymentMethod,
        String paymentReference,
        String notes
) {
    public static LoanInstallmentDto from(LoanInstallment i) {
        return new LoanInstallmentDto(
                i.getId(),
                i.getInstallmentNumber(),
                i.getDueDate(),
                i.getPrincipal(),
                i.getInterest(),
                i.getTotalDue(),
                i.getAmountPaid(),
                i.getTotalDue().subtract(i.getAmountPaid()),
                i.getStatus(),
                AppClock.serverInstant(i.getPaidAt()),
                i.getPaymentMethod(),
                i.getPaymentReference(),
                i.getNotes()
        );
    }
}
