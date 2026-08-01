package com.mdau.ushirika.module.messaging.repository;

import com.mdau.ushirika.module.messaging.entity.ConversationThread;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationThreadRepository extends JpaRepository<ConversationThread, UUID> {

    Optional<ConversationThread> findByMemberIdAndProgramIdIsNull(UUID memberId);

    Optional<ConversationThread> findByMemberIdAndProgramId(UUID memberId, UUID programId);

    List<ConversationThread> findAllByMemberIdOrderByLastMessageAtDesc(UUID memberId);

    List<ConversationThread> findAllByProgramIdIsNullOrderByLastMessageAtDesc();

    List<ConversationThread> findAllByProgramIdOrderByLastMessageAtDesc(UUID programId);
}
