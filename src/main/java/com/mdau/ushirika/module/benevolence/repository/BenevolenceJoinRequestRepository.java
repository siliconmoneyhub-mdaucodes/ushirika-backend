package com.mdau.ushirika.module.benevolence.repository;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.benevolence.entity.BenevolenceJoinRequest;
import com.mdau.ushirika.module.benevolence.enums.BenevolenceJoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BenevolenceJoinRequestRepository extends JpaRepository<BenevolenceJoinRequest, UUID> {

    List<BenevolenceJoinRequest> findByUserOrderByCreatedAtDesc(User user);

    Optional<BenevolenceJoinRequest> findFirstByUserOrderByCreatedAtDesc(User user);

    boolean existsByUserAndStatusIn(User user, Collection<BenevolenceJoinRequestStatus> statuses);

    List<BenevolenceJoinRequest> findByStatusOrderByCreatedAtDesc(BenevolenceJoinRequestStatus status);

    List<BenevolenceJoinRequest> findAllByOrderByCreatedAtDesc();
}
