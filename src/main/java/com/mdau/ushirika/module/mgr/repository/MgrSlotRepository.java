package com.mdau.ushirika.module.mgr.repository;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.mgr.entity.MgrCycle;
import com.mdau.ushirika.module.mgr.entity.MgrSlot;
import com.mdau.ushirika.module.mgr.enums.CycleStatus;
import com.mdau.ushirika.module.mgr.enums.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MgrSlotRepository extends JpaRepository<MgrSlot, UUID> {

    List<MgrSlot> findByCycleOrderBySlotNumber(MgrCycle cycle);

    Optional<MgrSlot> findByCycleAndUser(MgrCycle cycle, User user);

    /** A member can hold a slot in more than one cycle over time (past COMPLETED cycles, a new
     * one after re-applying) -- this resolves specifically their slot in whichever cycle is
     * presently ACTIVE, which is the only sense of "my current slot" that's ever meaningful for
     * outstanding-balance / payment-allocation purposes. Plain findByUser() would throw once a
     * repeat participant has more than one MgrSlot row (a real, expected outcome of this flow). */
    Optional<MgrSlot> findByUserAndCycleStatus(User user, CycleStatus status);

    boolean existsByCycleAndUser(MgrCycle cycle, User user);

    boolean existsByUserAndCycleStatus(User user, CycleStatus status);

    int countByCycle(MgrCycle cycle);

    /** Highest slotNumber currently assigned in this cycle -- used to seed the next slot number
     * instead of countByCycle(), which drifts once a mid-cycle slot is removed (count and max
     * diverge, and the next admission can silently reissue an in-use number). */
    @Query("SELECT COALESCE(MAX(s.slotNumber), 0) FROM MgrSlot s WHERE s.cycle = :cycle")
    int findMaxSlotNumberByCycle(@Param("cycle") MgrCycle cycle);

    long countByCycleAndStatus(MgrCycle cycle, SlotStatus status);

    /** Slots drawn for a specific payout month (payoutMonth is now nullable). */
    List<MgrSlot> findByCycleAndPayoutMonth(MgrCycle cycle, Integer payoutMonth);

    /** Calendar query: drawn slots for a user with payout date within [from, to]. */
    List<MgrSlot> findAllByUserAndScheduledPayoutDateBetween(User user, LocalDate from, LocalDate to);

    /** All SCHEDULED slots with no payout month yet — candidates for monthly draw. */
    @Query("SELECT s FROM MgrSlot s WHERE s.cycle = :cycle AND s.status = :status AND s.payoutMonth IS NULL")
    List<MgrSlot> findUndrawnByCycle(@Param("cycle") MgrCycle cycle,
                                     @Param("status") SlotStatus status);
}
