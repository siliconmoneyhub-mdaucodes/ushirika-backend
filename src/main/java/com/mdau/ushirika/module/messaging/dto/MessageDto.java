package com.mdau.ushirika.module.messaging.dto;

import com.mdau.ushirika.module.messaging.entity.ConversationMessage;

import java.time.LocalDateTime;
import java.util.UUID;

public record MessageDto(
        UUID id,
        UUID senderId,
        String senderName,
        boolean fromMember,
        String body,
        LocalDateTime createdAt
) {
    public static MessageDto from(ConversationMessage m) {
        return new MessageDto(
                m.getId(),
                m.getSender().getId(),
                m.getSender().getFullName(),
                m.isFromMember(),
                m.getBody(),
                m.getCreatedAt()
        );
    }
}
