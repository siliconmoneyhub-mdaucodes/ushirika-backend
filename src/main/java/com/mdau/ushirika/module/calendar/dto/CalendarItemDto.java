package com.mdau.ushirika.module.calendar.dto;

import com.mdau.ushirika.module.calendar.enums.CalendarItemType;

import java.time.Instant;
import java.util.UUID;

public record CalendarItemDto(
        CalendarItemType type,
        UUID id,
        String title,
        String description,
        Instant start,
        Instant end,
        String location,
        String actionUrl
) {}
