package com.mdau.ushirika.module.attendance.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.attendance.enums.ExcuseStatus;
import com.mdau.ushirika.module.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A member's apology for a LATE/ABSENT attendance record. Approval marks the record EXCUSED
 * and waives any linked fine — kept separate from ReinstatementRequest, which is the higher-stakes
 * appeal against a full membership cessation rather than a single meeting's fine. */
@Entity
@Table(
    name = "attendance_excuses",
    indexes = {
        @Index(name = "idx_excuse_record", columnList = "attendance_record_id"),
        @Index(name = "idx_excuse_status", columnList = "status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceExcuse extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_record_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_excuse_record"))
    private AttendanceRecord attendanceRecord;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private ExcuseStatus status = ExcuseStatus.PENDING;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id", foreignKey = @ForeignKey(name = "fk_excuse_decided_by"))
    private User decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;
}
