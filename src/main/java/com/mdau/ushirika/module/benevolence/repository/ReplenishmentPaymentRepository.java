package com.mdau.ushirika.module.benevolence.repository;

import com.mdau.ushirika.module.benevolence.entity.BenevolenceEnrollment;
import com.mdau.ushirika.module.benevolence.entity.BenevolenceReplenishment;
import com.mdau.ushirika.module.benevolence.entity.ReplenishmentPayment;
import com.mdau.ushirika.module.benevolence.enums.ReplenishmentPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReplenishmentPaymentRepository extends JpaRepository<ReplenishmentPayment, UUID> {
    List<ReplenishmentPayment> findByReplenishment(BenevolenceReplenishment replenishment);
    List<ReplenishmentPayment> findByEnrollmentOrderByCreatedAtDesc(BenevolenceEnrollment enrollment);
    List<ReplenishmentPayment> findByEnrollmentAndStatusOrderByCreatedAtAsc(
            BenevolenceEnrollment enrollment, ReplenishmentPaymentStatus status);
    Optional<ReplenishmentPayment> findByReplenishmentAndEnrollment(
            BenevolenceReplenishment replenishment, BenevolenceEnrollment enrollment);
    long countByReplenishmentAndStatus(BenevolenceReplenishment replenishment, ReplenishmentPaymentStatus status);
}
