package com.mdau.ushirika.module.content.dto;

import com.mdau.ushirika.module.content.entity.Article;
import com.mdau.ushirika.module.content.enums.ArticleStatus;
import com.mdau.ushirika.module.content.enums.ArticleType;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lightweight view for listing pages — omits the full content blocks. */
public record ArticleSummaryDto(
        UUID id,
        String title,
        String slug,
        String excerpt,
        ArticleType type,
        ArticleStatus status,
        String coverImageUrl,
        List<String> tags,
        Instant publishedAt
) {
    public static ArticleSummaryDto from(Article a) {
        return new ArticleSummaryDto(
                a.getId(), a.getTitle(), a.getSlug(), a.getExcerpt(),
                a.getType(), a.getStatus(), a.getCoverImageUrl(),
                a.getTags(), AppClock.serverInstant(a.getPublishedAt())
        );
    }
}
