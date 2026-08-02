package com.mdau.ushirika.module.payment.dto;

import com.mdau.ushirika.module.payment.entity.PaymentBasket;
import com.mdau.ushirika.module.payment.entity.PaymentBasketLine;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PaymentBasketSummaryDto(
        UUID id,
        String sessionId,
        String memberEmail,
        String memberName,
        String status,
        BigDecimal total,
        LocalDateTime createdAt,
        List<String> lineDescriptions
) {
    public static PaymentBasketSummaryDto from(PaymentBasket b) {
        BigDecimal total = b.getLines().stream().map(PaymentBasketLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> lines = b.getLines().stream()
                .map(l -> l.getDescription() + " — $" + l.getAmount())
                .toList();
        return new PaymentBasketSummaryDto(
                b.getId(), b.getSessionId(), b.getMember().getEmail(), b.getMember().getFullName(),
                b.getStatus().name(), total, b.getCreatedAt(), lines);
    }
}
