package com.mdau.ushirika.module.benevolence.dto;

import com.mdau.ushirika.module.benevolence.entity.BenevolenceJoinRequest;
import com.mdau.ushirika.module.benevolence.enums.BenevolenceJoinRequestStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.UUID;

public record BenevolenceJoinRequestDto(
        UUID id,
        UUID userId,
        String memberName,
        String memberEmail,
        String memberId,
        BenevolenceJoinRequestStatus status,
        String memberNotes,
        String adminNotes,
        Instant formSentAt,
        String formSentByName,
        Instant respondedAt,
        String respondedByName,
        Instant createdAt
) {
    public static BenevolenceJoinRequestDto from(BenevolenceJoinRequest r, String memberId) {
        return new BenevolenceJoinRequestDto(
                r.getId(),
                r.getUser().getId(),
                r.getUser().getFullName(),
                r.getUser().getEmail(),
                memberId,
                r.getStatus(),
                r.getMemberNotes(),
                r.getAdminNotes(),
                AppClock.serverInstant(r.getFormSentAt()),
                r.getFormSentBy() != null ? r.getFormSentBy().getFullName() : null,
                AppClock.serverInstant(r.getRespondedAt()),
                r.getRespondedBy() != null ? r.getRespondedBy().getFullName() : null,
                AppClock.serverInstant(r.getCreatedAt())
        );
    }
}
