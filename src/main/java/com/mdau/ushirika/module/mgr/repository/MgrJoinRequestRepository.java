package com.mdau.ushirika.module.mgr.repository;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.mgr.entity.MgrJoinRequest;
import com.mdau.ushirika.module.mgr.enums.JoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MgrJoinRequestRepository extends JpaRepository<MgrJoinRequest, UUID> {

    List<MgrJoinRequest> findByUserOrderByCreatedAtDesc(User user);

    boolean existsByUserAndStatusIn(User user, Collection<JoinRequestStatus> statuses);

    List<MgrJoinRequest> findByStatusOrderByCreatedAtDesc(JoinRequestStatus status);

    List<MgrJoinRequest> findAllByOrderByCreatedAtDesc();

    /** FCFS admission queue -- oldest application first. */
    List<MgrJoinRequest> findByStatusOrderByCreatedAtAsc(JoinRequestStatus status);
}
