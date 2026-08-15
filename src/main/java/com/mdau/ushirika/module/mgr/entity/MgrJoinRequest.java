package com.mdau.ushirika.module.mgr.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.mgr.enums.JoinRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A member's request to join MGR -- decoupled from any specific cycle. Applications are accepted
 * any time, not gated by a cycle being DRAFT or open. Flow: member applies (PENDING) -> coordinator
 * approves in principle (WAITLISTED) -> automatically swept into whichever cycle activates next,
 * first-come-first-served by application date (ADMITTED) -- or coordinator rejects (REJECTED).
 * {@code cycle} is therefore only ever set once ADMITTED; it records which cycle they landed in,
 * not which cycle they applied against.
 */
@Entity
@Table(
    name = "mgr_join_requests",
    indexes = {
        @Index(name = "idx_mgr_jr_cycle",  columnList = "cycle_id"),
        @Index(name = "idx_mgr_jr_user",   columnList = "user_id"),
        @Index(name = "idx_mgr_jr_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MgrJoinRequest extends BaseEntity {

    /** The cycle they were ultimately admitted into. Null until status becomes ADMITTED. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id",
                foreignKey = @ForeignKey(name = "fk_mgr_jr_cycle"))
    private MgrCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_mgr_jr_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    @Column(name = "member_notes", length = 500)
    private String memberNotes;

    @Column(name = "admin_notes", length = 500)
    private String adminNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by_id",
                foreignKey = @ForeignKey(name = "fk_mgr_jr_responded_by"))
    private User respondedBy;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    /** When this request was swept into a cycle at that cycle's activation. */
    @Column(name = "admitted_at")
    private LocalDateTime admittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_sent_by_id",
                foreignKey = @ForeignKey(name = "fk_mgr_jr_form_sent_by"))
    private User formSentBy;

    @Column(name = "form_sent_at")
    private LocalDateTime formSentAt;

    /** The cycle this WAITLISTED member is currently being asked about -- set fresh whenever a
     * new cycle is created, overwriting any prior invite. Null until their first invite. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_cycle_id",
                foreignKey = @ForeignKey(name = "fk_mgr_jr_invited_cycle"))
    private MgrCycle invitedCycle;

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    /** Null = no response yet to the current invite. TRUE = wants to join invitedCycle. FALSE =
     * wants to keep waiting. No response by the time invitedCycle activates defaults to "keep
     * waiting" -- they simply aren't swept in and stay WAITLISTED for the next invite. */
    @Column(name = "cycle_opt_in")
    private Boolean cycleOptIn;

    @Column(name = "cycle_responded_at")
    private LocalDateTime cycleRespondedAt;
}
