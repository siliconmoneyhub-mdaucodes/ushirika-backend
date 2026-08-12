package com.mdau.ushirika.module.dashboard.dto;

import java.math.BigDecimal;

public record DashboardSummaryDto(
        MemberStats members,
        WelfareStats welfare,
        ScholarshipStats scholarships,
        FinancialStats financial,
        DonationStats donations,
        EventStats events,
        ContentStats content,
        NotificationStats notifications
) {

    public record MemberStats(
            long total,
            long pending,
            long underReview,
            long approved,
            long rejected,
            /** Real, current active membership count -- role MEMBER, active login, not ceased.
             * Distinct from {@code approved}, which is an application-approval count and never
             * decreases even after a member's status later lapses to inactive/ceased. */
            long active
    ) {}

    public record WelfareStats(
            long totalRequests,
            long pending,
            long underReview,
            long approved,
            long disbursed,
            long rejected,
            BigDecimal totalDisbursedAmount
    ) {}

    public record ScholarshipStats(
            long totalApplications,
            long pending,
            long underReview,
            long approved,
            long awarded,
            long rejected,
            BigDecimal totalAwardedAmount
    ) {}

    public record FinancialStats(
            BigDecimal totalContributions,
            long contributionCount,
            BigDecimal totalDonations,
            long donationCount,
            BigDecimal combinedTotal
    ) {}

    public record DonationStats(
            long activeCampaigns,
            long totalCampaigns
    ) {}

    public record EventStats(
            long total,
            long published,
            long ongoing,
            long completed,
            long totalRegistrations
    ) {}

    public record ContentStats(
            long publishedArticles,
            long draftArticles,
            long totalMediaAssets
    ) {}

    public record NotificationStats(
            long emailSentLast7Days,
            long emailFailedLast7Days,
            long smsSentLast7Days,
            long smsFailedLast7Days
    ) {}
}
