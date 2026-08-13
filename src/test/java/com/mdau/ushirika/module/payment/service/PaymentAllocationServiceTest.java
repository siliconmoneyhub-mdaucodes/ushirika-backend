package com.mdau.ushirika.module.payment.service;

import com.mdau.ushirika.module.attendance.dto.FineDto;
import com.mdau.ushirika.module.attendance.service.FineService;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.benevolence.entity.ReplenishmentPayment;
import com.mdau.ushirika.module.benevolence.service.BenevolenceClaimService;
import com.mdau.ushirika.module.benevolence.service.BenevolenceEnrollmentService;
import com.mdau.ushirika.module.dues.service.MembershipDuesService;
import com.mdau.ushirika.module.mgr.service.MgrService;
import com.mdau.ushirika.module.payment.dto.MemberBalanceDto;
import com.mdau.ushirika.module.payment.entity.MemberCreditBalance;
import com.mdau.ushirika.module.payment.repository.MemberCreditBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the priority-order settlement (fines -> dues -> MGR -> replenishment -> Benevolence
 * enrollment), capping at real outstanding balances, and that leftover always becomes credit
 * rather than being silently dropped -- the exact bug this engine replaces in MgrService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentAllocationServiceTest {

    @Mock private MemberCreditBalanceRepository creditBalanceRepository;
    @Mock private FineService fineService;
    @Mock private MembershipDuesService membershipDuesService;
    @Mock private MgrService mgrService;
    @Mock private BenevolenceClaimService benevolenceClaimService;
    @Mock private BenevolenceEnrollmentService benevolenceEnrollmentService;
    @Mock private AuditLogService auditLogService;

    private PaymentAllocationService service;
    private User member;

    @BeforeEach
    void setUp() {
        service = new PaymentAllocationService(
                creditBalanceRepository, fineService, membershipDuesService, mgrService,
                benevolenceClaimService, benevolenceEnrollmentService, auditLogService);

        member = User.builder().email("member@test.ushirika.org").role(UserRole.MEMBER).build();
        member.setId(UUID.randomUUID());

        when(creditBalanceRepository.findByUser(member)).thenReturn(Optional.empty());
        when(creditBalanceRepository.save(any(MemberCreditBalance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fineService.getFinesForMember(member.getId())).thenReturn(List.of());
        when(membershipDuesService.outstandingBalance(member)).thenReturn(BigDecimal.ZERO);
        when(mgrService.outstandingContributionBalance(member)).thenReturn(BigDecimal.ZERO);
        when(benevolenceClaimService.getPendingReplenishmentsForMember(member)).thenReturn(List.of());
        when(benevolenceEnrollmentService.outstandingBalance(member)).thenReturn(null);
    }

    @Test
    void nothingOwed_wholeAmountBecomesCredit() {
        service.applyPayment(member, new BigDecimal("75.00"));

        ArgumentCaptor<MemberCreditBalance> captor = ArgumentCaptor.forClass(MemberCreditBalance.class);
        verify(creditBalanceRepository, atLeastOnce()).save(captor.capture());
        assertEquals(new BigDecimal("75.00"), lastValue(captor).getCreditAmount());
    }

    @Test
    void finesSettledBeforeDues_whenNotEnoughForBoth() {
        when(fineService.getFinesForMember(member.getId())).thenReturn(List.of(
                pendingFine(new BigDecimal("30.00"), LocalDate.now().minusDays(5))));
        when(membershipDuesService.outstandingBalance(member)).thenReturn(new BigDecimal("100.00"));

        // Only enough to cover the fine, nothing left for dues.
        service.applyPayment(member, new BigDecimal("30.00"));

        verify(fineService).markPaid(any());
        verify(membershipDuesService, never()).applyExternalPayment(any(), any());
    }

    @Test
    void oldestFineSettledFirst() {
        UUID olderFineId = UUID.randomUUID();
        UUID newerFineId = UUID.randomUUID();
        when(fineService.getFinesForMember(member.getId())).thenReturn(List.of(
                pendingFineWithId(newerFineId, new BigDecimal("20.00"), LocalDate.now().minusDays(1)),
                pendingFineWithId(olderFineId, new BigDecimal("20.00"), LocalDate.now().minusDays(10))));

        // Enough for exactly one fine.
        service.applyPayment(member, new BigDecimal("20.00"));

        verify(fineService).markPaid(olderFineId);
        verify(fineService, never()).markPaid(newerFineId);
    }

    @Test
    void duesCappedAtOutstanding_remainderBecomesCredit() {
        when(membershipDuesService.outstandingBalance(member)).thenReturn(new BigDecimal("40.00"));

        service.applyPayment(member, new BigDecimal("100.00"));

        verify(membershipDuesService).applyExternalPayment(member, new BigDecimal("40.00"));
        ArgumentCaptor<MemberCreditBalance> captor = ArgumentCaptor.forClass(MemberCreditBalance.class);
        verify(creditBalanceRepository, atLeastOnce()).save(captor.capture());
        assertEquals(new BigDecimal("60.00"), lastValue(captor).getCreditAmount());
    }

    @Test
    void mgrUnconsumedRemainderReturnsToCredit_notLost() {
        // Outstanding total is $150 across pending months, but applyContribution can only
        // consume whole months and reports $50 left unconsumed (a partial month) -- this must
        // come back as credit, not vanish, which is the exact bug this engine fixes.
        when(mgrService.outstandingContributionBalance(member)).thenReturn(new BigDecimal("150.00"));
        when(mgrService.applyContribution(member, new BigDecimal("150.00"))).thenReturn(new BigDecimal("50.00"));

        service.applyPayment(member, new BigDecimal("150.00"));

        ArgumentCaptor<MemberCreditBalance> captor = ArgumentCaptor.forClass(MemberCreditBalance.class);
        verify(creditBalanceRepository, atLeastOnce()).save(captor.capture());
        assertEquals(new BigDecimal("50.00"), lastValue(captor).getCreditAmount(),
                "unconsumed MGR remainder must be preserved as credit, not dropped");
    }

    @Test
    void existingCreditIsPooledWithNewPayment() {
        MemberCreditBalance existing = MemberCreditBalance.builder().user(member).creditAmount(new BigDecimal("20.00")).build();
        when(creditBalanceRepository.findByUser(member)).thenReturn(Optional.of(existing));
        when(membershipDuesService.outstandingBalance(member)).thenReturn(new BigDecimal("25.00"));

        service.applyPayment(member, new BigDecimal("10.00"));

        // 20 existing + 10 new = 30 pooled, 25 pays dues in full, 5 left as credit.
        verify(membershipDuesService).applyExternalPayment(member, new BigDecimal("25.00"));
        assertEquals(new BigDecimal("5.00"), existing.getCreditAmount());
    }

    @Test
    void replenishmentRequiresExactMatch_partialAmountSkipped() {
        ReplenishmentPayment rp = ReplenishmentPayment.builder().amountDue(new BigDecimal("50.00")).build();
        rp.setId(UUID.randomUUID());
        when(benevolenceClaimService.getPendingReplenishmentsForMember(member)).thenReturn(List.of(rp));

        // Not enough to cover it in full — stays pending, amount stays as credit.
        service.applyPayment(member, new BigDecimal("30.00"));

        verify(benevolenceClaimService, never()).applyReplenishmentPayment(any(), any());
        ArgumentCaptor<MemberCreditBalance> captor = ArgumentCaptor.forClass(MemberCreditBalance.class);
        verify(creditBalanceRepository, atLeastOnce()).save(captor.capture());
        assertEquals(new BigDecimal("30.00"), lastValue(captor).getCreditAmount());
    }

    @Test
    void getBalance_netsCreditAgainstOutstanding() {
        MemberCreditBalance existing = MemberCreditBalance.builder().user(member).creditAmount(new BigDecimal("50.00")).build();
        when(creditBalanceRepository.findByUser(member)).thenReturn(Optional.of(existing));
        when(membershipDuesService.outstandingBalance(member)).thenReturn(new BigDecimal("80.00"));

        MemberBalanceDto dto = service.getBalance(member);

        assertEquals(new BigDecimal("50.00"), dto.creditAmount());
        assertEquals(new BigDecimal("80.00"), dto.totalOutstanding());
        assertEquals(new BigDecimal("-30.00"), dto.netBalance(), "still owes $30 net even after applying credit");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private FineDto pendingFine(BigDecimal amount, LocalDate dueDate) {
        return pendingFineWithId(UUID.randomUUID(), amount, dueDate);
    }

    private FineDto pendingFineWithId(UUID id, BigDecimal amount, LocalDate dueDate) {
        return new FineDto(id, member.getId(), member.getEmail(), member.getEmail(), "UW-1",
                null, null, "Late attendance", amount, dueDate, "PENDING", null, null, null);
    }

    @SuppressWarnings("unchecked")
    private MemberCreditBalance lastValue(ArgumentCaptor<MemberCreditBalance> captor) {
        List<MemberCreditBalance> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }
}
