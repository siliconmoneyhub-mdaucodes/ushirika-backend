package com.mdau.ushirika.module.benevolence.repository;

import com.mdau.ushirika.module.benevolence.entity.BenevolenceClaim;
import com.mdau.ushirika.module.benevolence.entity.BenevolenceReplenishment;
import com.mdau.ushirika.module.benevolence.enums.ReplenishmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BenevolenceReplenishmentRepository extends JpaRepository<BenevolenceReplenishment, UUID> {
    Page<BenevolenceReplenishment> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<BenevolenceReplenishment> findAllByStatus(ReplenishmentStatus status);
    boolean existsByClaim(BenevolenceClaim claim);
}
