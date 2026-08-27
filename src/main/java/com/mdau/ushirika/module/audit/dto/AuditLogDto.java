package com.mdau.ushirika.module.audit.dto;

import com.mdau.ushirika.common.util.AppClock;
import com.mdau.ushirika.module.audit.entity.AuditLog;
import com.mdau.ushirika.module.audit.enums.LedgerDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
        UUID          id,
        UUID          actorId,
        String        actorName,
        String        actorRole,
        String        actorTitle,
        String        action,
        String        entityType,
        UUID          entityId,
        String        description,
        BigDecimal      amount,
        LedgerDirection direction,
        Instant       createdAt
) {
    public static AuditLogDto from(AuditLog log) {
        return new AuditLogDto(
                log.getId(),
                log.getActorId(),
                log.getActorName(),
                log.getActorRole(),
                log.getActorTitle(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getDescription(),
                log.getAmount(),
                log.getDirection(),
                AppClock.serverInstant(log.getCreatedAt())
        );
    }
}
