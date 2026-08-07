package com.mdau.ushirika.module.mgr.repository;

import com.mdau.ushirika.module.mgr.entity.MgrContribution;
import com.mdau.ushirika.module.mgr.entity.MgrCycle;
import com.mdau.ushirika.module.mgr.entity.MgrSlot;
import com.mdau.ushirika.module.mgr.enums.ContributionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MgrContributionRepository extends JpaRepository<MgrContribution, UUID> {
    List<MgrContribution> findBySlotOrderByContributionMonth(MgrSlot slot);
    List<MgrContribution> findByCycleAndContributionMonthOrderBySlotSlotNumber(MgrCycle cycle, int month);
    Optional<MgrContribution> findBySlotAndContributionMonth(MgrSlot slot, int month);
    long countByCycleAndStatus(MgrCycle cycle, ContributionStatus status);
    long countByCycleAndContributionMonthAndStatus(MgrCycle cycle, int month, ContributionStatus status);

    @Query("SELECT c FROM MgrContribution c WHERE c.cycle = :cycle AND c.status = 'PENDING' ORDER BY c.contributionMonth, c.slot.slotNumber")
    List<MgrContribution> findPendingByCycle(MgrCycle cycle);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM MgrContribution c WHERE c.status = 'PAID'")
    BigDecimal sumPaid();

    /** Monthly paid totals for the last N months (by paidAt). Returns rows of [year, month, total, count]. */
    @Query(value = """
        SELECT EXTRACT(YEAR  FROM paid_at)::int AS yr,
               EXTRACT(MONTH FROM paid_at)::int AS mo,
               SUM(amount)                      AS total,
               COUNT(*)                         AS cnt
        FROM mgr_contributions
        WHERE status = 'PAID' AND paid_at >= :from
        GROUP BY yr, mo
        ORDER BY yr, mo
        """, nativeQuery = true)
    List<Object[]> monthlyPaidTotals(@Param("from") LocalDateTime from);
}
