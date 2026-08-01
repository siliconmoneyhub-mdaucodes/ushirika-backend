package com.mdau.ushirika.module.messaging.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "conversation_messages",
    indexes = {
        @Index(name = "idx_message_thread", columnList = "thread_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private ConversationThread thread;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /** True when the member sent it, false when a staff member (admin/coordinator) sent it —
     * stored to render/sort threads without joining sender.role on every list query. */
    @Column(name = "from_member", nullable = false)
    private boolean fromMember;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;
}
