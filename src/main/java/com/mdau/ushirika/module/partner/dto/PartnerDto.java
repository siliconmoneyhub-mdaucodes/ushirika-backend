package com.mdau.ushirika.module.partner.dto;

import com.mdau.ushirika.module.partner.entity.Partner;

import java.util.UUID;

public record PartnerDto(
        UUID id,
        String name,
        String description,
        String websiteUrl,
        String logoUrl,
        boolean active,
        int sortOrder
) {
    public static PartnerDto from(Partner p) {
        return new PartnerDto(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getWebsiteUrl(),
                p.getLogoUrl(),
                p.isActive(),
                p.getSortOrder()
        );
    }
}
