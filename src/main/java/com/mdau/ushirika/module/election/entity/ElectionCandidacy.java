package com.mdau.ushirika.module.election.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.election.enums.CandidacyStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "election_candidacies",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_candidacy_seat_user",
        columnNames = {"seat_id", "user_id"}
    ),
    indexes = {
        @Index(name = "idx_ecan_election", columnList = "election_id"),
        @Index(name = "idx_ecan_seat",     columnList = "seat_id"),
        @Index(name = "idx_ecan_user",     columnList = "user_id"),
        @Index(name = "idx_ecan_status",   columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionCandidacy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_ecan_election"))
    private Election election;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_ecan_seat"))
    private ElectionSeat seat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_ecan_user"))
    private User candidate;

    /** Denormalized for fast read without joining users. */
    @Column(name = "member_name", nullable = false, length = 200)
    private String memberName;

    @Column(name = "member_id", length = 20)
    private String memberId;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(columnDefinition = "TEXT")
    private String statement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CandidacyStatus status = CandidacyStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_by", length = 150)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;
}
