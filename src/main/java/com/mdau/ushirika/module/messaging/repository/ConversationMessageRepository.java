package com.mdau.ushirika.module.messaging.repository;

import com.mdau.ushirika.module.messaging.entity.ConversationMessage;
import com.mdau.ushirika.module.messaging.entity.ConversationThread;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

    List<ConversationMessage> findAllByThreadOrderByCreatedAtAsc(ConversationThread thread);

    long countByThreadIdAndFromMemberAndCreatedAtAfter(UUID threadId, boolean fromMember, java.time.LocalDateTime after);

    /** Individual unread member messages in a thread, for per-message "needs attention" items --
     * a member sending 3 messages before staff reads any of them should surface as 3 items, not 1. */
    List<ConversationMessage> findAllByThreadAndFromMemberAndCreatedAtAfterOrderByCreatedAtAsc(
            ConversationThread thread, boolean fromMember, java.time.LocalDateTime after);

    /**
     * Latest message per thread, one query for the whole list. Postgres DISTINCT ON -- this project is
     * Postgres-only (see config/PostgresDialect). Replaces the per-thread findAllByThreadOrderByCreatedAtAsc()
     * call that loaded every message just to read the last one.
     *
     * Native + Object[] on purpose: native queries are not parsed at application startup (a typo here
     * cannot stop the app booting), and Object[] avoids projection alias-case pitfalls on Postgres.
     * Columns: [0] thread_id (UUID), [1] body (String), [2] created_at (Timestamp/LocalDateTime),
     * [3] from_member (Boolean).
     */
    @Query(value = """
            SELECT DISTINCT ON (m.thread_id)
                   m.thread_id, m.body, m.created_at, m.from_member
              FROM conversation_messages m
             WHERE m.thread_id IN (:threadIds)
             ORDER BY m.thread_id, m.created_at DESC
            """, nativeQuery = true)
    List<Object[]> findLastMessagePerThread(@Param("threadIds") Collection<UUID> threadIds);

    /** Unread member-sent messages per thread, from the STAFF viewpoint. Rows: [thread_id (UUID), count (Number)]. */
    @Query(value = """
            SELECT m.thread_id, COUNT(*)
              FROM conversation_messages m
              JOIN conversation_threads t ON t.id = m.thread_id
             WHERE m.thread_id IN (:threadIds)
               AND m.from_member = TRUE
               AND m.created_at > COALESCE(t.staff_last_read_at, t.created_at)
             GROUP BY m.thread_id
            """, nativeQuery = true)
    List<Object[]> countUnreadForStaff(@Param("threadIds") Collection<UUID> threadIds);

    /** Unread staff-sent messages per thread, from the MEMBER viewpoint. Rows: [thread_id (UUID), count (Number)]. */
    @Query(value = """
            SELECT m.thread_id, COUNT(*)
              FROM conversation_messages m
              JOIN conversation_threads t ON t.id = m.thread_id
             WHERE m.thread_id IN (:threadIds)
               AND m.from_member = FALSE
               AND m.created_at > COALESCE(t.member_last_read_at, t.created_at)
             GROUP BY m.thread_id
            """, nativeQuery = true)
    List<Object[]> countUnreadForMember(@Param("threadIds") Collection<UUID> threadIds);

    /** Windowed message fetch for a thread -- newest messages strictly before the cursor, newest first. */
    List<ConversationMessage> findAllByThreadIdAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID threadId, LocalDateTime before, Pageable pageable);

    long countByThreadId(UUID threadId);
}
