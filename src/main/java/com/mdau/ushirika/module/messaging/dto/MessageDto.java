package com.mdau.ushirika.module.messaging.dto;

import com.mdau.ushirika.module.messaging.entity.ConversationMessage;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.UUID;

public record MessageDto(
        UUID id,
        UUID senderId,
        String senderName,
        boolean fromMember,
        String body,
        Instant createdAt
) {
    public static MessageDto from(ConversationMessage m) {
        return new MessageDto(
                m.getId(),
                m.getSender().getId(),
                m.getSender().getFullName(),
                m.isFromMember(),
                m.getBody(),
                AppClock.serverInstant(m.getCreatedAt())
        );
    }
}
