package com.mdau.ushirika.module.payment.dto;

import com.mdau.ushirika.module.payment.entity.MemberContribution;
import com.mdau.ushirika.common.util.AppClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Admin-facing view of a contribution — includes member identity. */
public record AdminMemberContributionDto(
        UUID id,
        UUID memberId,
        String memberName,
        String memberEmail,
        String source,
        String planName,
        BigDecimal amount,
        String currency,
        String period,
        String notes,
        Instant createdAt
) {
    public static AdminMemberContributionDto from(MemberContribution c) {
        return new AdminMemberContributionDto(
                c.getId(),
                c.getMember().getId(),
                c.getMember().getFullName(),
                c.getMember().getEmail(),
                c.getSource().name(),
                c.getPlan() != null ? c.getPlan().getName() : "Custom",
                c.getAmount(),
                c.getCurrency() != null ? c.getCurrency() : "USD",
                c.getPeriod(),
                c.getNotes(),
                AppClock.serverInstant(c.getCreatedAt())
        );
    }
}
