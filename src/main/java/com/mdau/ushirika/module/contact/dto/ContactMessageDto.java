package com.mdau.ushirika.module.contact.dto;

import com.mdau.ushirika.module.contact.entity.ContactMessage;
import com.mdau.ushirika.module.contact.enums.ContactMessageStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.UUID;

public record ContactMessageDto(
        UUID                  id,
        String                name,
        String                email,
        String                phone,
        String                subject,
        String                body,
        ContactMessageStatus  status,
        Instant               readAt,
        Instant               repliedAt,
        String                handledBy,
        String                adminNotes,
        Instant               createdAt
) {
    public static ContactMessageDto from(ContactMessage m) {
        return new ContactMessageDto(
                m.getId(), m.getName(), m.getEmail(), m.getPhone(),
                m.getSubject(), m.getBody(), m.getStatus(),
                AppClock.serverInstant(m.getReadAt()), AppClock.serverInstant(m.getRepliedAt()), m.getHandledBy(),
                m.getAdminNotes(), AppClock.serverInstant(m.getCreatedAt())
        );
    }
}
