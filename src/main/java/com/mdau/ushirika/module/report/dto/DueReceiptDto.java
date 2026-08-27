package com.mdau.ushirika.module.report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DueReceiptDto(
        UUID receiptId,
        String receiptNumber,
        String memberName,
        String memberId,
        String email,
        int year,
        BigDecimal amount,
        LocalDate dueDate,
        Instant paidAt,
        String paymentMethod,
        String paymentReference
) {}
