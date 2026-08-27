package com.mdau.ushirika.module.attendance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttendanceSummaryDto(
        int totalMeetings,
        int attended,
        int absent,
        int excused,
        int consecutiveAbsences,
        boolean atRisk,
        boolean membershipCeased,
        List<MeetingItem> history
) {
    public record MeetingItem(
            UUID meetingId,
            String title,
            Instant meetingDate,
            String type,
            String attendanceStatus
    ) {}
}
