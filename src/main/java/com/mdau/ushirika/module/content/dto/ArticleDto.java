package com.mdau.ushirika.module.content.dto;

import com.mdau.ushirika.module.content.entity.Article;
import com.mdau.ushirika.module.content.enums.ArticleStatus;
import com.mdau.ushirika.module.content.enums.ArticleType;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ArticleDto(
        UUID id,
        String title,
        String slug,
        String excerpt,
        ArticleType type,
        ArticleStatus status,
        String coverImageUrl,
        List<Map<String, Object>> content,
        List<String> tags,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static ArticleDto from(Article a) {
        return new ArticleDto(
                a.getId(), a.getTitle(), a.getSlug(), a.getExcerpt(),
                a.getType(), a.getStatus(), a.getCoverImageUrl(),
                a.getContent(), a.getTags(),
                AppClock.serverInstant(a.getPublishedAt()), AppClock.serverInstant(a.getCreatedAt()), AppClock.serverInstant(a.getUpdatedAt())
        );
    }
}
