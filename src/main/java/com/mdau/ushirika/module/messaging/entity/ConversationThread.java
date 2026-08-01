package com.mdau.ushirika.module.messaging.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.program.entity.Program;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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
}
