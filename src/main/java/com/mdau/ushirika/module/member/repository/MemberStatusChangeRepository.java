package com.mdau.ushirika.module.member.repository;

import com.mdau.ushirika.module.member.entity.MemberStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberStatusChangeRepository extends JpaRepository<MemberStatusChange, UUID> {

    List<MemberStatusChange> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<MemberStatusChange> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
}
