package com.mdau.ushirika.module.report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FineReceiptDto(
        UUID receiptId,
        String receiptNumber,
        String memberName,
        String memberId,
        String email,
        String reason,
        BigDecimal amount,
        LocalDate dueDate,
        Instant paidAt,
        String meetingTitle
) {}
