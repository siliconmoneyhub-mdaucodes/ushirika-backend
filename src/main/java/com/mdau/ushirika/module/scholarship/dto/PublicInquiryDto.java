package com.mdau.ushirika.module.scholarship.dto;

import com.mdau.ushirika.module.scholarship.entity.PublicScholarshipInquiry;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.UUID;

public record PublicInquiryDto(
        UUID id,
        String fullName,
        String email,
        String phone,
        String message,
        String programName,
        Instant createdAt
) {
    public static PublicInquiryDto from(PublicScholarshipInquiry i) {
        return new PublicInquiryDto(
                i.getId(), i.getFullName(), i.getEmail(), i.getPhone(), i.getMessage(),
                i.getProgram() != null ? i.getProgram().getName() : null,
                AppClock.serverInstant(i.getCreatedAt())
        );
    }
}
