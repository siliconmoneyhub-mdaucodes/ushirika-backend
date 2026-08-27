package com.mdau.ushirika.module.dashboard.dto;

import java.time.Instant;
import java.util.List;

/** Compliance-tier dashboard — governing documents, reinstatement petitions, and the audit trail. */
public record ComplianceDashboardDto(
        boolean constitutionPublished,
        Instant constitutionPublishedAt,
        boolean bylawsPublished,
        Instant bylawsPublishedAt,
        long pendingReinstatements,
        List<AuditEntrySummary> recentAuditActivity
) {
    public record AuditEntrySummary(
            String actorName,
            String action,
            String description,
            Instant createdAt
    ) {
    }
}
