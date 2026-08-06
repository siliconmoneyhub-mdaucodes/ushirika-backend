package com.mdau.ushirika.module.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Compliance-tier dashboard — governing documents, reinstatement petitions, and the audit trail. */
public record ComplianceDashboardDto(
        boolean constitutionPublished,
        LocalDateTime constitutionPublishedAt,
        boolean bylawsPublished,
        LocalDateTime bylawsPublishedAt,
        long pendingReinstatements,
        List<AuditEntrySummary> recentAuditActivity
) {
    public record AuditEntrySummary(
            String actorName,
            String action,
            String description,
            LocalDateTime createdAt
    ) {
    }
}
