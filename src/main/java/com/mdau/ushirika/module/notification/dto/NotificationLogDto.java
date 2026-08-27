package com.mdau.ushirika.module.notification.dto;

import com.mdau.ushirika.module.notification.entity.NotificationLog;
import com.mdau.ushirika.module.notification.enums.NotificationChannel;
import com.mdau.ushirika.module.notification.enums.NotificationStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.UUID;

public record NotificationLogDto(
        UUID id,
        NotificationChannel channel,
        String recipient,
        String recipientName,
        String subject,
        String body,
        NotificationStatus status,
        String errorMessage,
        Instant createdAt
) {
    public static NotificationLogDto from(NotificationLog l) {
        return new NotificationLogDto(
                l.getId(), l.getChannel(), l.getRecipient(), l.getRecipientName(),
                l.getSubject(), l.getBody(), l.getStatus(), l.getErrorMessage(),
                AppClock.serverInstant(l.getCreatedAt())
        );
    }
}
