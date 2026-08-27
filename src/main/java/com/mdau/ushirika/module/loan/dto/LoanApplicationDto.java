package com.mdau.ushirika.module.loan.dto;

import com.mdau.ushirika.module.loan.entity.LoanApplication;
import com.mdau.ushirika.module.loan.enums.LoanStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LoanApplicationDto(
        UUID id,
        String referenceNumber,
        UUID userId,
        String memberName,
        String memberEmail,
        String memberId,
        BigDecimal requestedAmount,
        String purpose,
        int termMonths,
        LoanStatus status,
        BigDecimal approvedAmount,
        BigDecimal interestRate,
        BigDecimal totalRepayable,
        BigDecimal totalPaid,
        BigDecimal balance,
        LocalDate disbursedAt,
        LocalDate dueDate,
        String disbursementMethod,
        String disbursementReference,
        String adminNotes,
        String rejectionReason,
        Instant defaultedAt,
        Instant createdAt,
        List<LoanGuarantorDto> guarantors,
        List<LoanInstallmentDto> installments,
        int installmentCount,
        int paidInstallments
) {
    public static LoanApplicationDto from(
            LoanApplication loan,
            String memberId,
            List<LoanGuarantorDto> guarantors,
            List<LoanInstallmentDto> installments) {

        int paid = (int) installments.stream()
                .filter(i -> i.status().name().equals("PAID") || i.status().name().equals("WAIVED"))
                .count();
        BigDecimal balance = loan.getTotalRepayable() != null
                ? loan.getTotalRepayable().subtract(loan.getTotalPaid())
                : null;

        return new LoanApplicationDto(
                loan.getId(),
                loan.getReferenceNumber(),
                loan.getUser().getId(),
                loan.getUser().getFullName(),
                loan.getUser().getEmail(),
                memberId,
                loan.getRequestedAmount(),
                loan.getPurpose(),
                loan.getTermMonths(),
                loan.getStatus(),
                loan.getApprovedAmount(),
                loan.getInterestRate(),
                loan.getTotalRepayable(),
                loan.getTotalPaid(),
                balance,
                loan.getDisbursedAt(),
                loan.getDueDate(),
                loan.getDisbursementMethod(),
                loan.getDisbursementReference(),
                loan.getAdminNotes(),
                loan.getRejectionReason(),
                AppClock.serverInstant(loan.getDefaultedAt()),
                AppClock.serverInstant(loan.getCreatedAt()),
                guarantors,
                installments,
                installments.size(),
                paid
        );
    }

    public static LoanApplicationDto summary(
            LoanApplication loan,
            String memberId,
            int installmentCount,
            int paidInstallments) {

        BigDecimal balance = loan.getTotalRepayable() != null
                ? loan.getTotalRepayable().subtract(loan.getTotalPaid())
                : null;

        return new LoanApplicationDto(
                loan.getId(),
                loan.getReferenceNumber(),
                loan.getUser().getId(),
                loan.getUser().getFullName(),
                loan.getUser().getEmail(),
                memberId,
                loan.getRequestedAmount(),
                loan.getPurpose(),
                loan.getTermMonths(),
                loan.getStatus(),
                loan.getApprovedAmount(),
                loan.getInterestRate(),
                loan.getTotalRepayable(),
                loan.getTotalPaid(),
                balance,
                loan.getDisbursedAt(),
                loan.getDueDate(),
                loan.getDisbursementMethod(),
                loan.getDisbursementReference(),
                loan.getAdminNotes(),
                loan.getRejectionReason(),
                AppClock.serverInstant(loan.getDefaultedAt()),
                AppClock.serverInstant(loan.getCreatedAt()),
                List.of(),
                List.of(),
                installmentCount,
                paidInstallments
        );
    }
}
