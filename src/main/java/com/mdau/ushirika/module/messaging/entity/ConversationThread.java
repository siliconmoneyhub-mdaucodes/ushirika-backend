package com.mdau.ushirika.module.messaging.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.messaging.enums.ThreadPriority;
import com.mdau.ushirika.module.messaging.enums.ThreadStatus;
import com.mdau.ushirika.module.program.entity.Program;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One conversation between a member and either general admin staff (program == null)
 * or a specific program's coordinators (program != null). Read state is tracked per
 * side rather than per-message — simpler unread logic, matches the two-party nature
 * of every thread (the member vs. whichever staff group owns it).
 */
@Entity
@Table(
    name = "conversation_threads",
    indexes = {
        @Index(name = "idx_thread_member", columnList = "member_id"),
        @Index(name = "idx_thread_program", columnList = "program_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_thread_member_program", columnNames = {"member_id", "program_id"})
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationThread extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private User member;

    /** Null = general inquiry to admin staff. Non-null = inquiry to that program's coordinators. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Program program;

    @Column(name = "member_last_read_at")
    private LocalDateTime memberLastReadAt;

    @Column(name = "staff_last_read_at")
    private LocalDateTime staffLastReadAt;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    /** Human-readable id, "MSG-000123", drawn from conversation_thread_ref_seq. Uniqueness is enforced by
     *  the uq_thread_reference index created in DataInitializer (after the backfill), not by a JPA
     *  constraint, so Hibernate never tries to add a second overlapping unique constraint. */
    @Column(name = "reference_number", length = 20)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    @Builder.Default
    private ThreadPriority priority = ThreadPriority.NORMAL;

    @Column(name = "priority_set_by_id")
    private UUID prioritySetById;

    @Column(name = "priority_set_at")
    private LocalDateTime prioritySetAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private ThreadStatus status = ThreadStatus.OPEN;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by_id")
    private UUID closedById;

    @Column(name = "closed_by_name", length = 200)
    private String closedByName;

    /** When the member was last emailed about a staff reply on this thread (alert throttling). */
    @Column(name = "member_alert_sent_at")
    private LocalDateTime memberAlertSentAt;

    /** When staff were last emailed about a member message on this thread (alert throttling). */
    @Column(name = "staff_alert_sent_at")
    private LocalDateTime staffAlertSentAt;
}
