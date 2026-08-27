package com.mdau.ushirika.module.constitution.dto;

import com.mdau.ushirika.module.constitution.entity.GoverningDocument;
import com.mdau.ushirika.module.constitution.enums.DocumentStatus;
import com.mdau.ushirika.module.constitution.enums.DocumentType;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GoverningDocumentDto(
        UUID           id,
        String         title,
        DocumentType   documentType,
        String         description,
        String         documentVersion,
        String         fileUrl,
        String         contentText,
        LocalDate      effectiveDate,
        DocumentStatus status,
        Instant        publishedAt,
        int            sortOrder,
        Instant        createdAt,
        Instant        updatedAt
) {
    public static GoverningDocumentDto from(GoverningDocument d) {
        return new GoverningDocumentDto(
                d.getId(), d.getTitle(), d.getDocumentType(),
                d.getDescription(), d.getDocumentVersion(), d.getFileUrl(), d.getContentText(),
                d.getEffectiveDate(), d.getStatus(), AppClock.serverInstant(d.getPublishedAt()),
                d.getSortOrder(), AppClock.serverInstant(d.getCreatedAt()), AppClock.serverInstant(d.getUpdatedAt())
        );
    }
}
