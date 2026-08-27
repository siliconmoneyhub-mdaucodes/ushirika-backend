package com.mdau.ushirika.module.content.dto;

import com.mdau.ushirika.module.content.entity.CommunityAlbum;
import com.mdau.ushirika.module.content.enums.AlbumStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CommunityAlbumDto(
        UUID            id,
        String          title,
        String          description,
        String          coverImageUrl,
        LocalDate       eventDate,
        String          location,
        AlbumStatus     status,
        Instant         publishedAt,
        int             mediaCount,
        List<AlbumMediaDto> media,
        Instant         createdAt,
        Instant         updatedAt
) {
    public static CommunityAlbumDto from(CommunityAlbum a) {
        List<AlbumMediaDto> mediaDtos = a.getMedia().stream()
                .map(AlbumMediaDto::from)
                .toList();
        return new CommunityAlbumDto(
                a.getId(), a.getTitle(), a.getDescription(), a.getCoverImageUrl(),
                a.getEventDate(), a.getLocation(), a.getStatus(), AppClock.serverInstant(a.getPublishedAt()),
                mediaDtos.size(), mediaDtos, AppClock.serverInstant(a.getCreatedAt()), AppClock.serverInstant(a.getUpdatedAt())
        );
    }
}
