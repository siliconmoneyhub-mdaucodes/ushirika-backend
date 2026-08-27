package com.mdau.ushirika.module.event.dto;

import com.mdau.ushirika.module.event.entity.EventRegistration;
import com.mdau.ushirika.module.event.enums.RegistrationStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.UUID;

public record RegistrationDto(
        UUID id,
        UUID eventId,
        String eventTitle,
        String referenceCode,
        String displayName,
        String displayEmail,
        boolean memberRegistration,
        RegistrationStatus status,
        Instant registeredAt,
        Instant attendanceMarkedAt
) {
    public static RegistrationDto from(EventRegistration r) {
        return new RegistrationDto(
                r.getId(),
                r.getEvent().getId(),
                r.getEvent().getTitle(),
                r.getReferenceCode(),
                r.getDisplayName(),
                r.getDisplayEmail(),
                r.isMemberRegistration(),
                r.getStatus(),
                AppClock.serverInstant(r.getRegisteredAt()),
                AppClock.serverInstant(r.getAttendanceMarkedAt())
        );
    }
}
