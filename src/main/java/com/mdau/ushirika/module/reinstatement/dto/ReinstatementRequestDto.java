package com.mdau.ushirika.module.reinstatement.dto;

import com.mdau.ushirika.module.reinstatement.entity.ReinstatementRequest;
import com.mdau.ushirika.module.reinstatement.enums.ReinstatementStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.UUID;

public record ReinstatementRequestDto(
        UUID id,
        UUID userId,
        String reason,
        ReinstatementStatus status,
        String adminNotes,
        UUID reviewedBy,
        Instant reviewedAt,
        Instant createdAt
) {
    public static ReinstatementRequestDto from(ReinstatementRequest r) {
        return new ReinstatementRequestDto(
                r.getId(),
                r.getUserId(),
                r.getReason(),
                r.getStatus(),
                r.getAdminNotes(),
                r.getReviewedBy(),
                AppClock.serverInstant(r.getReviewedAt()),
                AppClock.serverInstant(r.getCreatedAt())
        );
    }
}
