package com.mdau.ushirika.module.actionitems.dto;

public record ActionItemDto(
        String id,
        String type,       // "APPLICATION" | "MESSAGE"
        String title,
        String subtitle,
        String link,
        String occurredAt  // ISO datetime, used for sort order
) {}
