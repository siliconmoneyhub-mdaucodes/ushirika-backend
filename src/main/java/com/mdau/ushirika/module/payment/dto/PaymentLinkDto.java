package com.mdau.ushirika.module.payment.dto;

import com.mdau.ushirika.module.payment.entity.PaymentLink;
import com.mdau.ushirika.module.payment.enums.PaymentChannel;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.UUID;

public record PaymentLinkDto(
        UUID id,
        PaymentChannel channel,
        String handle,
        String displayName,
        String instructions,
        String deepLinkUrl,
        boolean active,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentLinkDto from(PaymentLink p) {
        return new PaymentLinkDto(
                p.getId(), p.getChannel(), p.getHandle(), p.getDisplayName(),
                p.getInstructions(), p.getDeepLinkUrl(), p.isActive(), p.getDisplayOrder(),
                AppClock.serverInstant(p.getCreatedAt()), AppClock.serverInstant(p.getUpdatedAt())
        );
    }
}
