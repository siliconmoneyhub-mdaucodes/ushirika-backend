package com.mdau.ushirika.module.reinstatement.repository;

import com.mdau.ushirika.module.reinstatement.entity.ReinstatementRequest;
import com.mdau.ushirika.module.reinstatement.enums.ReinstatementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReinstatementRequestRepository extends JpaRepository<ReinstatementRequest, UUID> {

    List<ReinstatementRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ReinstatementRequest> findByUserIdAndStatus(UUID userId, ReinstatementStatus status);

    boolean existsByUserIdAndStatus(UUID userId, ReinstatementStatus status);

    Page<ReinstatementRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ReinstatementRequest> findAllByStatusOrderByCreatedAtDesc(ReinstatementStatus status, Pageable pageable);

    long countByStatus(ReinstatementStatus status);
}
