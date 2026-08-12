package com.mdau.ushirika.module.benevolence.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.benevolence.enums.BenevolenceJoinRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A member's request to join the Benevolence program -- its own dedicated flow, mirroring
 * MgrJoinRequest, rather than the generic ProgramApplication system (which explicitly excludes
 * Benevolence/MGR since each needs its own rules). Flow: member requests -> coordinator sends
 * the form (this also opens a real BenevolenceEnrollment so the member can submit beneficiaries
 * and pay through the existing, already-working enrollment machinery) -> coordinator approves
 * or rejects once beneficiaries/payment look right.
 */
@Entity
@Table(
    name = "benevolence_join_requests",
    indexes = {
        @Index(name = "idx_bjr_user",   columnList = "user_id"),
        @Index(name = "idx_bjr_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenevolenceJoinRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_bjr_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private BenevolenceJoinRequestStatus status = BenevolenceJoinRequestStatus.PENDING;

    @Column(name = "member_notes", length = 500)
    private String memberNotes;

    @Column(name = "admin_notes", length = 500)
    private String adminNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_sent_by_id",
                foreignKey = @ForeignKey(name = "fk_bjr_form_sent_by"))
    private User formSentBy;

    @Column(name = "form_sent_at")
    private LocalDateTime formSentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by_id",
                foreignKey = @ForeignKey(name = "fk_bjr_responded_by"))
    private User respondedBy;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
}
