package com.mdau.ushirika.module.dashboard.dto;

/** Secretary/Records-tier dashboard — membership record-keeping and meeting scheduling. */
public record RecordsDashboardDto(
        long totalMembers,
        long pendingApplications,
        long onboardingInProgress,
        MeetingSummaryDto nextMeeting,
        MeetingSummaryDto lastMeeting,
        Double lastMeetingAttendanceRatePercent
) {
}
