package com.mdau.ushirika.module.messaging.repository;

import com.mdau.ushirika.module.messaging.entity.ConversationMessage;
import com.mdau.ushirika.module.messaging.entity.ConversationThread;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

    List<ConversationMessage> findAllByThreadOrderByCreatedAtAsc(ConversationThread thread);

    long countByThreadIdAndFromMemberAndCreatedAtAfter(UUID threadId, boolean fromMember, java.time.LocalDateTime after);

    /** Individual unread member messages in a thread, for per-message "needs attention" items --
     * a member sending 3 messages before staff reads any of them should surface as 3 items, not 1. */
    List<ConversationMessage> findAllByThreadAndFromMemberAndCreatedAtAfterOrderByCreatedAtAsc(
            ConversationThread thread, boolean fromMember, java.time.LocalDateTime after);
}
