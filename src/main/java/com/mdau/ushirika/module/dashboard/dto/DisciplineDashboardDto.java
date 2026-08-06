package com.mdau.ushirika.module.dashboard.dto;

import java.math.BigDecimal;

/** Chief Whip/Discipline-tier dashboard — attendance, fines, and excuse requests. */
public record DisciplineDashboardDto(
        long pendingExcuses,
        long pendingFinesCount,
        BigDecimal pendingFinesTotal,
        long waivedFinesCount,
        MeetingSummaryDto lastMeeting,
        Double lastMeetingAttendanceRatePercent
) {
}
