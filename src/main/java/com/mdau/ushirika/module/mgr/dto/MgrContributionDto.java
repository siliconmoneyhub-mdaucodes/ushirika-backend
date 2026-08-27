package com.mdau.ushirika.module.mgr.dto;

import com.mdau.ushirika.module.mgr.entity.MgrContribution;
import com.mdau.ushirika.module.mgr.enums.ContributionStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MgrContributionDto(
        UUID id,
        UUID slotId,
        UUID cycleId,
        int contributionMonth,
        BigDecimal amount,
        ContributionStatus status,
        String paymentMethod,
        String paymentReference,
        Instant paidAt,
        String notes
) {
    public static MgrContributionDto from(MgrContribution c) {
        return new MgrContributionDto(
                c.getId(), c.getSlot().getId(), c.getCycle().getId(),
                c.getContributionMonth(), c.getAmount(), c.getStatus(),
                c.getPaymentMethod(), c.getPaymentReference(), AppClock.serverInstant(c.getPaidAt()), c.getNotes()
        );
    }
}
