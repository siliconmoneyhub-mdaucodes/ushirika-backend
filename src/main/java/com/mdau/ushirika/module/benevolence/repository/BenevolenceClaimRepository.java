package com.mdau.ushirika.module.benevolence.repository;

import com.mdau.ushirika.module.benevolence.entity.BenevolenceClaim;
import com.mdau.ushirika.module.benevolence.entity.BenevolenceEnrollment;
import com.mdau.ushirika.module.benevolence.enums.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BenevolenceClaimRepository extends JpaRepository<BenevolenceClaim, UUID> {
    List<BenevolenceClaim> findByEnrollmentOrderBySubmittedAtDesc(BenevolenceEnrollment enrollment);
    Page<BenevolenceClaim> findAllByOrderBySubmittedAtDesc(Pageable pageable);
    Page<BenevolenceClaim> findAllByStatusOrderBySubmittedAtDesc(ClaimStatus status, Pageable pageable);
    Optional<BenevolenceClaim> findByReferenceNumber(String referenceNumber);

    long countByStatus(ClaimStatus status);

    @Query("SELECT COALESCE(SUM(c.amountApproved), 0) FROM BenevolenceClaim c WHERE c.status = 'DISBURSED'")
    BigDecimal sumDisbursed();

    /** Monthly disbursed totals for the last N months. Returns rows of [year, month, total, count]. */
    @Query(value = """
        SELECT EXTRACT(YEAR  FROM disbursed_at)::int AS yr,
               EXTRACT(MONTH FROM disbursed_at)::int AS mo,
               SUM(amount_approved)                  AS total,
               COUNT(*)                               AS cnt
        FROM benevolence_claims
        WHERE status = 'DISBURSED' AND disbursed_at >= :from
        GROUP BY yr, mo
        ORDER BY yr, mo
        """, nativeQuery = true)
    List<Object[]> monthlyDisbursedTotals(@Param("from") LocalDateTime from);
}
