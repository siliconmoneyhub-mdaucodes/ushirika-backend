package com.mdau.ushirika.module.attendance.dto;

import com.mdau.ushirika.common.util.AppClock;
import com.mdau.ushirika.module.attendance.entity.Meeting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record MeetingDto(
        UUID id,
        String title,
        String description,
        Instant meetingDate,
        String location,
        String type,
        String status,
        String notes,
        LocalDateTime createdAt,

        Instant checkInOpensAt,
        Instant seatedByAt,
        BigDecimal lateFineAmount,
        BigDecimal absentFineAmount,
        Double venueLatitude,
        Double venueLongitude,
        Integer checkInRadiusMeters,
        boolean qrCheckInConfigured
) {
    public static MeetingDto from(Meeting m) {
        return new MeetingDto(
                m.getId(), m.getTitle(), m.getDescription(),
                AppClock.toInstant(m.getMeetingDate()), m.getLocation(),
                m.getType().name(), m.getStatus().name(),
                m.getNotes(), m.getCreatedAt(),
                AppClock.toInstant(m.getCheckInOpensAt()), AppClock.toInstant(m.getSeatedByAt()),
                m.getLateFineAmount(), m.getAbsentFineAmount(),
                m.getVenueLatitude(), m.getVenueLongitude(), m.getCheckInRadiusMeters(),
                m.getCheckInOpensAt() != null && m.getVenueLatitude() != null && m.getVenueLongitude() != null
        );
    }
}
