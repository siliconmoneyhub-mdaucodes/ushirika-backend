package com.mdau.ushirika.module.attendance.dto;

import com.mdau.ushirika.module.attendance.enums.MeetingType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdateMeetingRequest(
        String title,
        String description,
        LocalDateTime meetingDate,
        String location,
        MeetingType type,
        String notes,

        LocalDateTime checkInOpensAt,
        LocalDateTime seatedByAt,
        BigDecimal lateFineAmount,
        BigDecimal absentFineAmount,
        Double venueLatitude,
        Double venueLongitude,
        Integer checkInRadiusMeters
) {}
