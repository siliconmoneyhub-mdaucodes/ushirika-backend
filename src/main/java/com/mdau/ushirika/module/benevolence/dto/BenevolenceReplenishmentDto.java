package com.mdau.ushirika.module.benevolence.dto;

import com.mdau.ushirika.module.benevolence.entity.BenevolenceReplenishment;
import com.mdau.ushirika.module.benevolence.enums.ReplenishmentStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BenevolenceReplenishmentDto(
        UUID id,
        UUID claimId,
        String claimReferenceNumber,
        BigDecimal totalAmount,
        BigDecimal perMemberAmount,
        LocalDate dueDate,
        String notes,
        ReplenishmentStatus status,
        int totalMembers,
        long paidCount,
        List<ReplenishmentPaymentDto> memberPayments,
        Instant createdAt
) {
    public static BenevolenceReplenishmentDto from(BenevolenceReplenishment r,
                                                    List<ReplenishmentPaymentDto> memberPayments,
                                                    long paidCount) {
        String claimRef = r.getClaim() != null ? r.getClaim().getReferenceNumber() : null;
        UUID claimId = r.getClaim() != null ? r.getClaim().getId() : null;

        return new BenevolenceReplenishmentDto(
                r.getId(), claimId, claimRef, r.getTotalAmount(), r.getPerMemberAmount(),
                r.getDueDate(), r.getNotes(), r.getStatus(),
                memberPayments.size(), paidCount, memberPayments, AppClock.serverInstant(r.getCreatedAt())
        );
    }

    public static BenevolenceReplenishmentDto summary(BenevolenceReplenishment r,
                                                       int totalMembers, long paidCount) {
        String claimRef = r.getClaim() != null ? r.getClaim().getReferenceNumber() : null;
        UUID claimId = r.getClaim() != null ? r.getClaim().getId() : null;

        return new BenevolenceReplenishmentDto(
                r.getId(), claimId, claimRef, r.getTotalAmount(), r.getPerMemberAmount(),
                r.getDueDate(), r.getNotes(), r.getStatus(),
                totalMembers, paidCount, null, AppClock.serverInstant(r.getCreatedAt())
        );
    }
}
