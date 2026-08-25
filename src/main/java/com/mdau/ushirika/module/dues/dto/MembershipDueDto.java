package com.mdau.ushirika.module.dues.dto;

import com.mdau.ushirika.module.dues.entity.MembershipDue;

import java.math.BigDecimal;

public record MembershipDueDto(
        String id,
        String userId,
        String memberName,
        String email,
        String memberId,
        int year,
        BigDecimal amount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        String dueDate,
        String paidAt,
        String status,
        String paymentMethod,
        String paymentReference,
        String notes,
        String createdAt,
        int remainingMonths,
        BigDecimal recommendedMonthlyAmount,
        boolean registrationFeeWaived
) {
    public static MembershipDueDto from(MembershipDue d, String memberId) {
        return from(d, memberId, false);
    }

    /** registrationFeeWaived surfaces whether this member was onboarded as an existing/legacy
     *  member (fee waived at approval) -- the dues admin UI uses it to suggest a permanent
     *  waive as the default for a SUPERADMIN acting on this due, since that member never went
     *  through a payment checkout on this platform in the first place. */
    public static MembershipDueDto from(MembershipDue d, String memberId, boolean registrationFeeWaived) {
        BigDecimal paid = d.getPaidAmount() != null ? d.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal remaining = d.getAmount().subtract(paid).max(BigDecimal.ZERO);

        return new MembershipDueDto(
                d.getId().toString(),
                d.getUser().getId().toString(),
                d.getUser().getFullName(),
                d.getUser().getEmail(),
                memberId,
                d.getYear(),
                d.getAmount(),
                paid,
                remaining,
                d.getDueDate() != null ? d.getDueDate().toString() : null,
                d.getPaidAt() != null ? d.getPaidAt().toString() : null,
                d.getStatus().name(),
                d.getPaymentMethod(),
                d.getPaymentReference(),
                d.getNotes(),
                d.getCreatedAt() != null ? d.getCreatedAt().toString() : null,
                d.remainingMonths(),
                d.recommendedMonthlyAmount(),
                registrationFeeWaived
        );
    }
}
