package com.mdau.ushirika.module.attendance.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.attendance.enums.MeetingStatus;
import com.mdau.ushirika.module.attendance.enums.MeetingType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "meetings",
    indexes = {
        @Index(name = "idx_meetings_date",   columnList = "meeting_date"),
        @Index(name = "idx_meetings_status", columnList = "status"),
        @Index(name = "idx_meetings_type",   columnList = "type")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Meeting extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "meeting_date", nullable = false)
    private LocalDateTime meetingDate;

    @Column(length = 300)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @Column(length = 500)
    private String notes;

    // ── QR check-in (Attendance Phase A) ──────────────────────────────────────

    /** When members may start scanning in. Null means QR check-in isn't configured for this meeting. */
    @Column(name = "check_in_opens_at")
    private LocalDateTime checkInOpensAt;

    /** On-time cutoff — check-ins after this are marked LATE instead of PRESENT. */
    @Column(name = "seated_by_at")
    private LocalDateTime seatedByAt;

    @Column(name = "late_fine_amount", precision = 10, scale = 2)
    private BigDecimal lateFineAmount;

    @Column(name = "absent_fine_amount", precision = 10, scale = 2)
    private BigDecimal absentFineAmount;

    /** Server-side only — never exposed via DTO. Seeds the rotating HMAC check-in code. */
    @Column(name = "check_in_secret", length = 100)
    private String checkInSecret;

    @Column(name = "venue_latitude")
    private Double venueLatitude;

    @Column(name = "venue_longitude")
    private Double venueLongitude;

    @Column(name = "check_in_radius_meters", nullable = false)
    @Builder.Default
    private Integer checkInRadiusMeters = 150;
}
