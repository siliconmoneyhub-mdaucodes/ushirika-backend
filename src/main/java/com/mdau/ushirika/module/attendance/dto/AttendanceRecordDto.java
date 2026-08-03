package com.mdau.ushirika.module.attendance.dto;

import com.mdau.ushirika.module.attendance.entity.AttendanceRecord;

import java.time.LocalDateTime;
import java.util.UUID;

public record AttendanceRecordDto(
        UUID id,
        UUID userId,
        String memberName,
        String email,
        String memberId,
        String status,
        LocalDateTime checkedInAt,
        String notes,
        UUID fineId
) {
    public static AttendanceRecordDto from(AttendanceRecord ar, String memberId) {
        return new AttendanceRecordDto(
                ar.getId(),
                ar.getUser().getId(),
                ar.getUser().getFullName(),
                ar.getUser().getEmail(),
                memberId,
                ar.getStatus().name(),
                ar.getCheckedInAt(),
                ar.getNotes(),
                ar.getFine() != null ? ar.getFine().getId() : null
        );
    }
}
