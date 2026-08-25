package com.mdau.ushirika.module.benevolence.dto;

import com.mdau.ushirika.module.benevolence.entity.BenevolenceEnrollment;
import com.mdau.ushirika.module.benevolence.enums.EnrollmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BenevolenceEnrollmentDto(
        UUID id,
        UUID userId,
        String memberName,
        String email,
        String memberId,
        LocalDateTime enrolledAt,
        BigDecimal totalPaid,
        BigDecimal remainingBalance,
        LocalDateTime completedAt,
        LocalDate probationEndsAt,
        EnrollmentStatus status,
        boolean beneficiariesLocked,
        int beneficiaryCount,
        List<EnrollmentPaymentDto> payments,
        List<BenevolenceBeneficiaryDto> beneficiaries
) {
    private static final BigDecimal ENROLLMENT_FEE = new BigDecimal("600.00");

    public static BenevolenceEnrollmentDto from(BenevolenceEnrollment e,
                                                 String memberId,
                                                 List<EnrollmentPaymentDto> payments,
                                                 List<BenevolenceBeneficiaryDto> beneficiaries) {
        BigDecimal remaining = ENROLLMENT_FEE.subtract(e.getTotalPaid()).max(BigDecimal.ZERO);
        String fullName = e.getUser().getFullName();

        return new BenevolenceEnrollmentDto(
                e.getId(), e.getUser().getId(), fullName, e.getUser().getEmail(), memberId,
                e.getEnrolledAt(), e.getTotalPaid(), remaining,
                e.getCompletedAt(), e.getProbationEndsAt(), e.getStatus(),
                e.isBeneficiariesLocked(), beneficiaries.size(), payments, beneficiaries
        );
    }

    public static BenevolenceEnrollmentDto summary(BenevolenceEnrollment e,
                                                    String memberId,
                                                    int beneficiaryCount) {
        BigDecimal remaining = ENROLLMENT_FEE.subtract(e.getTotalPaid()).max(BigDecimal.ZERO);
        String fullName = e.getUser().getFullName();

        return new BenevolenceEnrollmentDto(
                e.getId(), e.getUser().getId(), fullName, e.getUser().getEmail(), memberId,
                e.getEnrolledAt(), e.getTotalPaid(), remaining,
                e.getCompletedAt(), e.getProbationEndsAt(), e.getStatus(),
                e.isBeneficiariesLocked(), beneficiaryCount, null, null
        );
    }
}
