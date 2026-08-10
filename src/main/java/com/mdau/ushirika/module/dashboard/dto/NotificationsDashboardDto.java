package com.mdau.ushirika.module.dashboard.dto;

/** Notifications-capability dashboard — delivery activity over the last 7 days. */
public record NotificationsDashboardDto(
        long emailSentLast7Days,
        long emailFailedLast7Days,
        long smsSentLast7Days,
        long smsFailedLast7Days
) {
}
