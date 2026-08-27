package com.mdau.ushirika.module.event.dto;

import com.mdau.ushirika.common.util.AppClock;
import com.mdau.ushirika.module.event.entity.Event;
import com.mdau.ushirika.module.event.enums.EventStatus;
import com.mdau.ushirika.module.event.enums.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventDto(
        UUID id,
        String title,
        String description,
        EventType type,
        EventStatus status,
        String venue,
        String onlineLink,
        Instant startDateTime,
        Instant endDateTime,
        Instant registrationDeadline,
        Integer capacity,
        boolean membersOnly,
        boolean requiresPayment,
        BigDecimal ticketPrice,
        String coverImageUrl,
        List<String> tags,
        long registeredCount
) {
    public static EventDto from(Event e, long registeredCount) {
        return new EventDto(
                e.getId(), e.getTitle(), e.getDescription(),
                e.getType(), e.getStatus(), e.getVenue(), e.getOnlineLink(),
                AppClock.toInstant(e.getStartDateTime()), AppClock.toInstant(e.getEndDateTime()),
                AppClock.toInstant(e.getRegistrationDeadline()),
                e.getCapacity(), e.isMembersOnly(), e.isRequiresPayment(), e.getTicketPrice(),
                e.getCoverImageUrl(), e.getTags(), registeredCount
        );
    }
}
