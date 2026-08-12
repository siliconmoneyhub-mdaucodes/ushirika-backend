package com.mdau.ushirika.module.mgr.dto;

import com.mdau.ushirika.module.mgr.entity.MgrCycle;
import com.mdau.ushirika.module.mgr.enums.CycleStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record MgrCycleDto(
        UUID id,
        String name,
        int year,
        LocalDate startDate,
        LocalDate endDate,
        int totalSlots,
        BigDecimal monthlyContribution,
        int payoutsPerMonth,
        BigDecimal payoutAmountPerSlot,
        BigDecimal reservePercentage,
        int benefitPayoutDay,
        String notes,
        CycleStatus status,
        LocalDateTime activatedAt,
        LocalDateTime completedAt,
        int assignedSlots,
        long paidPayouts,
        long pendingContributions,
        List<MgrSlotDto> slots
) {
    public static MgrCycleDto from(MgrCycle c, int assignedSlots, long paidPayouts,
                                    long pendingContributions, List<MgrSlotDto> slots) {
        return new MgrCycleDto(
                c.getId(), c.getName(), c.getYear(), c.getStartDate(), c.getEndDate(),
                c.getTotalSlots(), c.getMonthlyContribution(), c.getPayoutsPerMonth(),
                c.getPayoutAmountPerSlot(), c.getReservePercentage(), c.getBenefitPayoutDay(),
                c.getNotes(), c.getStatus(),
                c.getActivatedAt(), c.getCompletedAt(),
                assignedSlots, paidPayouts, pendingContributions, slots
        );
    }

    public static MgrCycleDto summary(MgrCycle c, int assignedSlots, long paidPayouts) {
        return new MgrCycleDto(
                c.getId(), c.getName(), c.getYear(), c.getStartDate(), c.getEndDate(),
                c.getTotalSlots(), c.getMonthlyContribution(), c.getPayoutsPerMonth(),
                c.getPayoutAmountPerSlot(), c.getReservePercentage(), c.getBenefitPayoutDay(),
                c.getNotes(), c.getStatus(),
                c.getActivatedAt(), c.getCompletedAt(),
                assignedSlots, paidPayouts, 0, null
        );
    }
}
