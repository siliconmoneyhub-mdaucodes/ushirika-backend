package com.mdau.ushirika.module.content.dto;

import com.mdau.ushirika.module.content.entity.MediaAsset;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.UUID;

public record MediaAssetDto(
        UUID id,
        String publicId,
        String url,
        String folder,
        String originalFilename,
        String format,
        Long sizeBytes,
        Integer width,
        Integer height,
        Instant createdAt
) {
    public static MediaAssetDto from(MediaAsset a) {
        return new MediaAssetDto(
                a.getId(), a.getPublicId(), a.getUrl(), a.getFolder(),
                a.getOriginalFilename(), a.getFormat(), a.getSizeBytes(),
                a.getWidth(), a.getHeight(), AppClock.serverInstant(a.getCreatedAt())
        );
    }
}
