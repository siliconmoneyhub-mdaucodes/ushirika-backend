package com.mdau.ushirika.module.messaging.repository;

import com.mdau.ushirika.module.messaging.entity.ConversationThread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationThreadRepository extends JpaRepository<ConversationThread, UUID> {

    Optional<ConversationThread> findByMemberIdAndProgramIdIsNull(UUID memberId);

    Optional<ConversationThread> findByMemberIdAndProgramId(UUID memberId, UUID programId);

    List<ConversationThread> findAllByMemberIdOrderByLastMessageAtDesc(UUID memberId);

    List<ConversationThread> findAllByProgramIdIsNullOrderByLastMessageAtDesc();

    List<ConversationThread> findAllByProgramIdOrderByLastMessageAtDesc(UUID programId);

    /** Next human-readable reference, e.g. "MSG-000123". The sequence is created in DataInitializer. */
    @Query(value = "SELECT 'MSG-' || LPAD(nextval('conversation_thread_ref_seq')::text, 6, '0')", nativeQuery = true)
    String nextReferenceNumber();

    /**
     * Staff list, general threads only: skips threads with no message yet (B-6 -- the portal's "Message Admin"
     * button creates an empty thread), ordered URGENT -> HIGH -> NORMAL then most-recent-first.
     * {@code status} / {@code priority} are enum NAMES or null for "any". The CAST(... AS VARCHAR) keeps
     * Postgres from failing with "could not determine data type of parameter" when a null is bound.
     * Native (not JPQL) so a mistake here can never prevent the application from starting.
     */
    @Query(value = """
            SELECT t.* FROM conversation_threads t
             WHERE t.program_id IS NULL AND t.last_message_at IS NOT NULL
               AND (CAST(:status AS VARCHAR) IS NULL OR t.status = CAST(:status AS VARCHAR))
               AND (CAST(:priority AS VARCHAR) IS NULL OR t.priority = CAST(:priority AS VARCHAR))
             ORDER BY CASE t.priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 ELSE 2 END,
                      t.last_message_at DESC
             LIMIT :lim
            """, nativeQuery = true)
    List<ConversationThread> findGeneralThreadsFiltered(@Param("status") String status,
                                                        @Param("priority") String priority,
                                                        @Param("lim") int limit);

    /** Same as {@link #findGeneralThreadsFiltered} but for one program's threads. */
    @Query(value = """
            SELECT t.* FROM conversation_threads t
             WHERE t.program_id = :programId AND t.last_message_at IS NOT NULL
               AND (CAST(:status AS VARCHAR) IS NULL OR t.status = CAST(:status AS VARCHAR))
               AND (CAST(:priority AS VARCHAR) IS NULL OR t.priority = CAST(:priority AS VARCHAR))
             ORDER BY CASE t.priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 ELSE 2 END,
                      t.last_message_at DESC
             LIMIT :lim
            """, nativeQuery = true)
    List<ConversationThread> findProgramThreadsFiltered(@Param("programId") UUID programId,
                                                        @Param("status") String status,
                                                        @Param("priority") String priority,
                                                        @Param("lim") int limit);
}
