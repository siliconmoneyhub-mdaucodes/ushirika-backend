package com.mdau.ushirika.module.dashboard.dto;

/** Content-capability dashboard — events, news, gallery/media across the public-facing site. */
public record ContentDashboardDto(
        long publishedArticles,
        long draftArticles,
        long totalMediaAssets,
        long totalEvents,
        long publishedEvents,
        long ongoingEvents
) {
}
