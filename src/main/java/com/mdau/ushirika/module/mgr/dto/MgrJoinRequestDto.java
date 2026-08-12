package com.mdau.ushirika.module.mgr.dto;

import com.mdau.ushirika.module.mgr.entity.MgrJoinRequest;
import com.mdau.ushirika.module.mgr.enums.JoinRequestStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record MgrJoinRequestDto(
        UUID id,
        UUID cycleId,
        String cycleName,
        UUID userId,
        String memberName,
        String email,
        String memberId,
        JoinRequestStatus status,
        String memberNotes,
        String adminNotes,
        String respondedByName,
        LocalDateTime respondedAt,
        LocalDateTime admittedAt,
        LocalDateTime createdAt
) {
    public static MgrJoinRequestDto from(MgrJoinRequest r, String memberId) {
        return new MgrJoinRequestDto(
                r.getId(),
                r.getCycle() != null ? r.getCycle().getId() : null,
                r.getCycle() != null ? r.getCycle().getName() : null,
                r.getUser().getId(),
                r.getUser().getFullName(),
                r.getUser().getEmail(),
                memberId,
                r.getStatus(),
                r.getMemberNotes(),
                r.getAdminNotes(),
                r.getRespondedBy() != null ? r.getRespondedBy().getFullName() : null,
                r.getRespondedAt(),
                r.getAdmittedAt(),
                r.getCreatedAt()
        );
    }
}
