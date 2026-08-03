package com.mdau.ushirika.module.attendance.dto;

import com.mdau.ushirika.module.attendance.entity.Meeting;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MeetingDto(
        UUID id,
        String title,
        String description,
        LocalDateTime meetingDate,
        String location,
        String type,
        String status,
        String notes,
        LocalDateTime createdAt,

        LocalDateTime checkInOpensAt,
        LocalDateTime seatedByAt,
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
                m.getMeetingDate(), m.getLocation(),
                m.getType().name(), m.getStatus().name(),
                m.getNotes(), m.getCreatedAt(),
                m.getCheckInOpensAt(), m.getSeatedByAt(),
                m.getLateFineAmount(), m.getAbsentFineAmount(),
                m.getVenueLatitude(), m.getVenueLongitude(), m.getCheckInRadiusMeters(),
                m.getCheckInOpensAt() != null && m.getVenueLatitude() != null && m.getVenueLongitude() != null
        );
    }
}
