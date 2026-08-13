package com.mdau.ushirika.module.payment.service;

import com.mdau.ushirika.module.attendance.dto.FineDto;
import com.mdau.ushirika.module.attendance.service.FineService;
import com.mdau.ushirika.module.audit.enums.LedgerDirection;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.benevolence.entity.ReplenishmentPayment;
import com.mdau.ushirika.module.benevolence.service.BenevolenceClaimService;
import com.mdau.ushirika.module.benevolence.service.BenevolenceEnrollmentService;
import com.mdau.ushirika.module.dues.service.MembershipDuesService;
import com.mdau.ushirika.module.mgr.service.MgrService;
import com.mdau.ushirika.module.payment.dto.MemberBalanceDto;
import com.mdau.ushirika.module.payment.entity.MemberCreditBalance;
import com.mdau.ushirika.module.payment.repository.MemberCreditBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;

/**
 * Single settlement point for every dollar that lands on a member's account, regardless of
 * source (self-checkout or an admin processing a cash payment on their behalf). Money is never
 * earmarked at payment time -- it's pooled with whatever credit the member already has, then
 * applied in a fixed priority order (fines, then dues, then MGR, then Benevolence replenishment,
 * then Benevolence enrollment), oldest obligation first within each type. Anything left over
 * stays as credit for whatever comes due next; general contributions are logged separately
 * and never consume or receive credit, since they're voluntary rather than owed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAllocationService {

    private final MemberCreditBalanceRepository creditBalanceRepository;
    private final FineService fineService;
    private final MembershipDuesService membershipDuesService;
    private final MgrService mgrService;
    private final BenevolenceClaimService benevolenceClaimService;
    private final BenevolenceEnrollmentService benevolenceEnrollmentService;
    private final AuditLogService auditLogService;

    @Transactional
    public void applyPayment(User member, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return;

        MemberCreditBalance balance = getOrCreateBalance(member);
        BigDecimal pool = balance.getCreditAmount().add(amount);
        BigDecimal before = pool;

        pool = settleFines(member, pool);
        pool = settleDues(member, pool);
        pool = settleMgr(member, pool);
        pool = settleReplenishments(member, pool);
        pool = settleBenevolenceEnrollment(member, pool);

        balance.setCreditAmount(pool);
        creditBalanceRepository.save(balance);

        log.info("Settled ${} for {} — ${} applied to outstanding obligations, ${} left as credit",
                amount, member.getEmail(), before.subtract(pool), pool);
    }

    @Transactional(readOnly = true)
    public MemberBalanceDto getBalance(User member) {
        BigDecimal credit = creditBalanceRepository.findByUser(member)
                .map(MemberCreditBalance::getCreditAmount)
                .orElse(BigDecimal.ZERO);

        BigDecimal outstanding = BigDecimal.ZERO;
        outstanding = outstanding.add(sumPendingFines(member));
        outstanding = outstanding.add(membershipDuesService.outstandingBalance(member));
        outstanding = outstanding.add(mgrService.outstandingContributionBalance(member));
        outstanding = outstanding.add(sumPendingReplenishments(member));
        var enrollmentBalance = benevolenceEnrollmentService.outstandingBalance(member);
        if (enrollmentBalance != null) outstanding = outstanding.add(enrollmentBalance.balance());

        return MemberBalanceDto.of(credit, outstanding);
    }

    // ── Settlement steps, in priority order ─────────────────────────────────────

    private BigDecimal settleFines(User member, BigDecimal pool) {
        if (pool.signum() <= 0) return pool;
        var pendingFines = fineService.getFinesForMember(member.getId()).stream()
                .filter(f -> "PENDING".equals(f.status()))
                .sorted(Comparator.comparing(FineDto::dueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (FineDto fine : pendingFines) {
            if (pool.compareTo(fine.amount()) < 0) break;
            fineService.markPaid(fine.id());
            pool = pool.subtract(fine.amount());
            logApplied(member, "FINE", fine.amount(), "Fine paid: " + fine.reason());
        }
        return pool;
    }

    private BigDecimal settleDues(User member, BigDecimal pool) {
        if (pool.signum() <= 0) return pool;
        BigDecimal owed = membershipDuesService.outstandingBalance(member);
        if (owed.signum() <= 0) return pool;
        BigDecimal toApply = pool.min(owed);
        membershipDuesService.applyExternalPayment(member, toApply);
        logApplied(member, "DUES", toApply, "Membership dues paid");
        return pool.subtract(toApply);
    }

    private BigDecimal settleMgr(User member, BigDecimal pool) {
        if (pool.signum() <= 0) return pool;
        BigDecimal owed = mgrService.outstandingContributionBalance(member);
        if (owed.signum() <= 0) return pool;
        BigDecimal toApply = pool.min(owed);
        // toApply never exceeds the real outstanding total, so applyContribution can always
        // consume it in whole months with nothing left over -- but capture the return anyway
        // in case of a mid-flight race (a contribution got waived between the two reads).
        BigDecimal unconsumed = mgrService.applyContribution(member, toApply);
        BigDecimal actuallyApplied = toApply.subtract(unconsumed);
        if (actuallyApplied.signum() > 0) {
            logApplied(member, "MGR_CONTRIBUTION", actuallyApplied, "MGR contribution paid");
        }
        return pool.subtract(toApply).add(unconsumed);
    }

    private BigDecimal settleReplenishments(User member, BigDecimal pool) {
        if (pool.signum() <= 0) return pool;
        for (ReplenishmentPayment rp : benevolenceClaimService.getPendingReplenishmentsForMember(member)) {
            if (pool.compareTo(rp.getAmountDue()) < 0) break;
            benevolenceClaimService.applyReplenishmentPayment(rp.getId(), rp.getAmountDue());
            pool = pool.subtract(rp.getAmountDue());
            logApplied(member, "BENEVOLENCE_REPLENISHMENT", rp.getAmountDue(), "Benevolence replenishment paid");
        }
        return pool;
    }

    private BigDecimal settleBenevolenceEnrollment(User member, BigDecimal pool) {
        if (pool.signum() <= 0) return pool;
        var balance = benevolenceEnrollmentService.outstandingBalance(member);
        if (balance == null || balance.balance().signum() <= 0) return pool;
        BigDecimal toApply = pool.min(balance.balance());
        benevolenceEnrollmentService.applyPayment(member, toApply);
        logApplied(member, "BENEVOLENCE_ENROLLMENT", toApply, "Benevolence enrollment fee paid");
        return pool.subtract(toApply);
    }

    /** One ledger entry per obligation actually settled -- the real amount that landed on that
     * specific program after pooled cross-obligation redistribution, which is what Money In & Out
     * and per-program totals (Finance Visibility plan, Phases 2/4) need. entityType matches the
     * same PaymentBasketLedger-name convention used in PaymentBasketService's own (non-pooled)
     * ledger entries, so both sources group together cleanly. */
    private void logApplied(User member, String entityType, BigDecimal amount, String description) {
        auditLogService.log(member, "PAYMENT_APPLIED", entityType, null,
                description + " — $" + amount + " for " + member.getFullName(),
                amount, LedgerDirection.IN);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private BigDecimal sumPendingFines(User member) {
        return fineService.getFinesForMember(member.getId()).stream()
                .filter(f -> "PENDING".equals(f.status()))
                .map(FineDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumPendingReplenishments(User member) {
        return benevolenceClaimService.getPendingReplenishmentsForMember(member).stream()
                .map(ReplenishmentPayment::getAmountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private MemberCreditBalance getOrCreateBalance(User member) {
        return creditBalanceRepository.findByUser(member)
                .orElseGet(() -> creditBalanceRepository.save(MemberCreditBalance.builder()
                        .user(member)
                        .creditAmount(BigDecimal.ZERO)
                        .build()));
    }
}
