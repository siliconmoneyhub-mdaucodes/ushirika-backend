package com.mdau.ushirika.module.notification.dto;

import com.mdau.ushirika.module.notification.entity.InAppNotification;
import com.mdau.ushirika.module.notification.enums.InAppNotificationCategory;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.UUID;

public record InAppNotificationDto(
        UUID id,
        InAppNotificationCategory category,
        String title,
        String body,
        String actionUrl,
        boolean read,
        Instant readAt,
        Instant createdAt
) {
    public static InAppNotificationDto from(InAppNotification n) {
        return new InAppNotificationDto(
                n.getId(), n.getCategory(), n.getTitle(), n.getBody(),
                n.getActionUrl(), n.isRead(), AppClock.serverInstant(n.getReadAt()), AppClock.serverInstant(n.getCreatedAt())
        );
    }
}
