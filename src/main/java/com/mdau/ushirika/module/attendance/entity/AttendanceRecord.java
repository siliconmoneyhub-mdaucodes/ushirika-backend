package com.mdau.ushirika.module.attendance.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.attendance.enums.AttendanceStatus;
import com.mdau.ushirika.module.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "attendance_records",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_attendance_meeting_user",
        columnNames = {"meeting_id", "user_id"}
    ),
    indexes = {
        @Index(name = "idx_ar_user",    columnList = "user_id"),
        @Index(name = "idx_ar_meeting", columnList = "meeting_id"),
        @Index(name = "idx_ar_status",  columnList = "status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_ar_meeting"))
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_ar_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(length = 500)
    private String notes;

    /** The auto-created late/absence fine tied to this record, if any — lets an approved apology find and waive it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fine_id", foreignKey = @ForeignKey(name = "fk_ar_fine"))
    private Fine fine;

    @Column(name = "check_in_latitude")
    private Double checkInLatitude;

    @Column(name = "check_in_longitude")
    private Double checkInLongitude;
}
