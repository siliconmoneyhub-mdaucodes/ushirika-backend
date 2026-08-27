package com.mdau.ushirika.module.attendance.dto;

import com.mdau.ushirika.module.attendance.entity.AttendanceExcuse;
import com.mdau.ushirika.module.attendance.entity.AttendanceRecord;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.UUID;

public record AttendanceExcuseDto(
        UUID id,
        UUID meetingId,
        String meetingTitle,
        UUID userId,
        String memberName,
        String email,
        String memberId,
        String attendanceStatus,
        String reason,
        String evidenceUrl,
        String status,
        String adminNotes,
        String decidedByName,
        Instant decidedAt,
        Instant createdAt
) {
    public static AttendanceExcuseDto from(AttendanceExcuse e, String memberId) {
        AttendanceRecord r = e.getAttendanceRecord();
        return new AttendanceExcuseDto(
                e.getId(),
                r.getMeeting().getId(),
                r.getMeeting().getTitle(),
                r.getUser().getId(),
                r.getUser().getFullName(),
                r.getUser().getEmail(),
                memberId,
                r.getStatus().name(),
                e.getReason(),
                e.getEvidenceUrl(),
                e.getStatus().name(),
                e.getAdminNotes(),
                e.getDecidedBy() != null ? e.getDecidedBy().getFullName() : null,
                AppClock.serverInstant(e.getDecidedAt()),
                AppClock.serverInstant(e.getCreatedAt())
        );
    }
}
