package com.mdau.ushirika.module.mgr.dto;

import com.mdau.ushirika.module.mgr.entity.MgrJoinRequest;
import com.mdau.ushirika.module.mgr.enums.JoinRequestStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
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
        Instant respondedAt,
        Instant admittedAt,
        Instant formSentAt,
        UUID invitedCycleId,
        String invitedCycleName,
        Instant invitedAt,
        Boolean cycleOptIn,
        Instant cycleRespondedAt,
        Instant createdAt
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
                AppClock.serverInstant(r.getRespondedAt()),
                AppClock.serverInstant(r.getAdmittedAt()),
                AppClock.serverInstant(r.getFormSentAt()),
                r.getInvitedCycle() != null ? r.getInvitedCycle().getId() : null,
                r.getInvitedCycle() != null ? r.getInvitedCycle().getName() : null,
                AppClock.serverInstant(r.getInvitedAt()),
                r.getCycleOptIn(),
                AppClock.serverInstant(r.getCycleRespondedAt()),
                AppClock.serverInstant(r.getCreatedAt())
        );
    }
}
