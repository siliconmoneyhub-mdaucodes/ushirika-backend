package com.mdau.ushirika.module.content.dto;

import com.mdau.ushirika.module.content.entity.CommunityAlbum;
import com.mdau.ushirika.module.content.enums.AlbumStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AlbumSummaryDto(
        UUID            id,
        String          title,
        String          description,
        String          coverImageUrl,
        LocalDate       eventDate,
        String          location,
        AlbumStatus     status,
        Instant         publishedAt,
        int             mediaCount,
        Instant         createdAt,
        Instant         updatedAt
) {
    public static AlbumSummaryDto from(CommunityAlbum a) {
        return new AlbumSummaryDto(
                a.getId(), a.getTitle(), a.getDescription(), a.getCoverImageUrl(),
                a.getEventDate(), a.getLocation(), a.getStatus(), AppClock.serverInstant(a.getPublishedAt()),
                a.getMedia().size(), AppClock.serverInstant(a.getCreatedAt()), AppClock.serverInstant(a.getUpdatedAt())
        );
    }
}
