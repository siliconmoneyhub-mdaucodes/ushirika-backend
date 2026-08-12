package com.mdau.ushirika.module.benevolence.dto;

import com.mdau.ushirika.module.benevolence.entity.BenevolenceJoinRequest;
import com.mdau.ushirika.module.benevolence.enums.BenevolenceJoinRequestStatus;

import java.time.LocalDateTime;
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
        LocalDateTime formSentAt,
        String formSentByName,
        LocalDateTime respondedAt,
        String respondedByName,
        LocalDateTime createdAt
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
                r.getFormSentAt(),
                r.getFormSentBy() != null ? r.getFormSentBy().getFullName() : null,
                r.getRespondedAt(),
                r.getRespondedBy() != null ? r.getRespondedBy().getFullName() : null,
                r.getCreatedAt()
        );
    }
}
