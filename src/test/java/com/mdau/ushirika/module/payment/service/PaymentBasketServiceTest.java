package com.mdau.ushirika.module.payment.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.attendance.dto.FineDto;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.benevolence.dto.ReplenishmentPaymentDto;
import com.mdau.ushirika.module.benevolence.enums.ReplenishmentPaymentStatus;
import com.mdau.ushirika.module.benevolence.service.BenevolenceClaimService;
import com.mdau.ushirika.module.benevolence.service.BenevolenceEnrollmentService;
import com.mdau.ushirika.module.dues.service.MembershipDuesService;
import com.mdau.ushirika.module.mgr.service.MgrService;
import com.mdau.ushirika.module.attendance.service.FineService;
import com.mdau.ushirika.module.payment.dto.BasketLineDto;
import com.mdau.ushirika.module.payment.dto.PayBalancesLineDto;
import com.mdau.ushirika.module.payment.entity.PaymentBasket;
import com.mdau.ushirika.module.payment.entity.PaymentBasketLine;
import com.mdau.ushirika.module.payment.enums.PaymentBasketLedger;
import com.mdau.ushirika.module.payment.enums.PaymentStatus;
import com.mdau.ushirika.module.payment.repository.PaymentBasketRepository;
import com.mdau.ushirika.module.program.service.ProgramApplicationService;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Simulates every payment-status scenario (success, duplicate webhook delivery, partial
 * ledger failure, validation rejection) WITHOUT touching real Stripe — this is the
 * "stubbed/mocked" money-testing pass. Each test stands in for a real user journey through
 * the checkout endpoints and the webhook that would normally follow.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentBasketServiceTest {

    @Mock private PaymentBasketRepository basketRepository;
    @Mock private StripeService stripeService;
    @Mock private UserRepository userRepository;
    @Mock private MembershipDuesService membershipDuesService;
    @Mock private BenevolenceEnrollmentService benevolenceEnrollmentService;
    @Mock private MgrService mgrService;
    @Mock private FineService fineService;
    @Mock private BenevolenceClaimService benevolenceClaimService;
    @Mock private ProgramApplicationService programApplicationService;
    @Mock private ContributionService contributionService;
    @Mock private PlatformSettingsService platformSettingsService;
    @Mock private PaymentAllocationService paymentAllocationService;
    @Mock private com.mdau.ushirika.module.audit.service.AuditLogService auditLogService;
    @Mock private com.mdau.ushirika.module.notification.service.InAppNotificationService notificationService;

    private PaymentBasketService service;
    private User member;

    @BeforeEach
    void setUp() {
        service = new PaymentBasketService(
                basketRepository, stripeService, userRepository,
                membershipDuesService, benevolenceEnrollmentService, mgrService,
                fineService, benevolenceClaimService, programApplicationService, contributionService,
                platformSettingsService, paymentAllocationService, auditLogService, notificationService);

        when(platformSettingsService.getRegistrationFeeAmount()).thenReturn(new BigDecimal("100.00"));

        member = User.builder().email("member@test.ushirika.org").role(UserRole.MEMBER).build();
        member.setId(UUID.randomUUID());
        when(userRepository.findByEmail("member@test.ushirika.org")).thenReturn(Optional.of(member));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member.getEmail(), null, List.of()));

        when(stripeService.createCheckoutSession(anyString(), anyList(), anyString(), anyString(), anyMap()))
                .thenReturn(new StripeService.StripeCheckoutResult("cs_test_fake", "https://checkout.stripe.com/fake"));
        when(basketRepository.save(any(PaymentBasket.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Journey: onboarding checkout ────────────────────────────────────────

    @Test
    void onboarding_registrationOnly_buildsSingleLine() {
        service.startOnboardingCheckout(null, null, "https://x/success", "https://x/cancel", null);

        ArgumentCaptor<List<StripeService.LineItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(stripeService).createCheckoutSession(anyString(), captor.capture(), anyString(), anyString(), anyMap());
        assertEquals(1, captor.getValue().size());
        assertEquals(new BigDecimal("100.00"), captor.getValue().get(0).amountUsd());
        verifyNoInteractions(programApplicationService);
    }

    @Test
    void onboarding_withBenevolencePrepay_addsSecondLineAfterValidation() {
        UUID appId = UUID.randomUUID();
        doNothing().when(programApplicationService).validatePrepayable(eq(appId), eq(member), eq(new BigDecimal("250")));

        service.startOnboardingCheckout(new BigDecimal("250"), appId, "https://x/success", "https://x/cancel", null);

        verify(programApplicationService).validatePrepayable(appId, member, new BigDecimal("250"));
        ArgumentCaptor<List<StripeService.LineItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(stripeService).createCheckoutSession(anyString(), captor.capture(), anyString(), anyString(), anyMap());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void onboarding_benevolenceAmountWithoutApplicationId_throws() {
        assertThrows(BadRequestException.class, () ->
                service.startOnboardingCheckout(new BigDecimal("100"), null, "https://x/success", "https://x/cancel", null));
        verifyNoInteractions(stripeService);
    }

    @Test
    void onboarding_prepayRejectedByValidation_bubblesUp() {
        UUID appId = UUID.randomUUID();
        doThrow(new BadRequestException("That would exceed the $600 Benevolence enrollment total."))
                .when(programApplicationService).validatePrepayable(eq(appId), eq(member), any());

        assertThrows(BadRequestException.class, () ->
                service.startOnboardingCheckout(new BigDecimal("700"), appId, "https://x/success", "https://x/cancel", null));
        verifyNoInteractions(stripeService);
    }

    // ── Journey: pay my balances — validation against real balances ────────

    @Test
    void balances_capsRequestedAmountAtRealDuesBalance() {
        when(membershipDuesService.outstandingBalance(member)).thenReturn(new BigDecimal("40.00"));
        when(fineService.getMyFines()).thenReturn(List.of());
        when(benevolenceClaimService.getMyReplenishments()).thenThrow(new ResourceNotFoundException("not enrolled"));

        service.startBalancesCheckout(
                List.of(new PayBalancesLineDto(PaymentBasketLedger.DUES, null, new BigDecimal("999.00"))),
                "https://x/success", "https://x/cancel", null);

        ArgumentCaptor<List<StripeService.LineItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(stripeService).createCheckoutSession(anyString(), captor.capture(), anyString(), anyString(), anyMap());
        assertEquals(new BigDecimal("40.00"), captor.getValue().get(0).amountUsd());
    }

    @Test
    void balances_zeroBalanceSelection_rejected() {
        when(membershipDuesService.outstandingBalance(member)).thenReturn(BigDecimal.ZERO);

        assertThrows(BadRequestException.class, () -> service.startBalancesCheckout(
                List.of(new PayBalancesLineDto(PaymentBasketLedger.DUES, null, new BigDecimal("50.00"))),
                "https://x/success", "https://x/cancel", null));
    }

    @Test
    void balances_finesForceIncludedEvenWhenNotSelected() {
        FineDto pendingFine = fineDto("PENDING", new BigDecimal("25.00"));
        when(fineService.getMyFines()).thenReturn(List.of(pendingFine));
        when(benevolenceClaimService.getMyReplenishments()).thenThrow(new ResourceNotFoundException("not enrolled"));

        // Empty request — member selected nothing themselves.
        service.startBalancesCheckout(List.of(), "https://x/success", "https://x/cancel", null);

        ArgumentCaptor<List<StripeService.LineItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(stripeService).createCheckoutSession(anyString(), captor.capture(), anyString(), anyString(), anyMap());
        assertEquals(1, captor.getValue().size());
        assertEquals(new BigDecimal("25.00"), captor.getValue().get(0).amountUsd());
    }

    @Test
    void balances_clientSuppliedFineLineIsIgnored_realPendingFinesUsedInstead() {
        FineDto realFine = fineDto("PENDING", new BigDecimal("25.00"));
        when(fineService.getMyFines()).thenReturn(List.of(realFine));
        when(benevolenceClaimService.getMyReplenishments()).thenThrow(new ResourceNotFoundException("not enrolled"));

        // Member (or a tampered request) tries to sneak in a fake $1 fine line.
        service.startBalancesCheckout(
                List.of(new PayBalancesLineDto(PaymentBasketLedger.FINE, UUID.randomUUID(), new BigDecimal("1.00"))),
                "https://x/success", "https://x/cancel", null);

        ArgumentCaptor<List<StripeService.LineItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(stripeService).createCheckoutSession(anyString(), captor.capture(), anyString(), anyString(), anyMap());
        assertEquals(1, captor.getValue().size());
        assertEquals(new BigDecimal("25.00"), captor.getValue().get(0).amountUsd(), "must use the real fine amount, not the client-supplied one");
    }

    @Test
    void balances_onboardingOnlyLedgers_rejected() {
        assertThrows(BadRequestException.class, () -> service.startBalancesCheckout(
                List.of(new PayBalancesLineDto(PaymentBasketLedger.REGISTRATION_FEE, null, new BigDecimal("100"))),
                "https://x/success", "https://x/cancel", null));
        assertThrows(BadRequestException.class, () -> service.startBalancesCheckout(
                List.of(new PayBalancesLineDto(PaymentBasketLedger.PROGRAM_APPLICATION_PREPAY, UUID.randomUUID(), new BigDecimal("100"))),
                "https://x/success", "https://x/cancel", null));
    }

    @Test
    void balances_replenishmentMustMatchExactAmountDue() {
        UUID paymentId = UUID.randomUUID();
        ReplenishmentPaymentDto pending = new ReplenishmentPaymentDto(
                paymentId, UUID.randomUUID(), UUID.randomUUID(), "Member", "UW-1",
                new BigDecimal("50.00"), BigDecimal.ZERO, null, null, null, ReplenishmentPaymentStatus.PENDING);
        when(benevolenceClaimService.getMyReplenishments()).thenReturn(List.of(pending));
        when(fineService.getMyFines()).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> service.startBalancesCheckout(
                List.of(new PayBalancesLineDto(PaymentBasketLedger.BENEVOLENCE_REPLENISHMENT, paymentId, new BigDecimal("30.00"))),
                "https://x/success", "https://x/cancel", null));
    }

    @Test
    void balances_emptySelectionAndNoFines_rejected() {
        when(fineService.getMyFines()).thenReturn(List.of());
        when(benevolenceClaimService.getMyReplenishments()).thenThrow(new ResourceNotFoundException("not enrolled"));

        assertThrows(BadRequestException.class, () ->
                service.startBalancesCheckout(List.of(), "https://x/success", "https://x/cancel", null));
    }

    // ── Journey: webhook delivers "payment succeeded" ───────────────────────

    @Test
    void webhook_unknownSession_noOp() {
        when(basketRepository.findBySessionId("cs_ghost")).thenReturn(Optional.empty());
        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_ghost");

        assertDoesNotThrow(() -> service.handleSessionCompleted(session));
        verifyNoInteractions(membershipDuesService, benevolenceEnrollmentService, mgrService, fineService);
    }

    @Test
    void webhook_duplicateDelivery_isIdempotent() {
        PaymentBasket basket = basketOf(member, PaymentStatus.SUCCESS,
                line(PaymentBasketLedger.DUES, null, new BigDecimal("50.00")));
        when(basketRepository.findBySessionId("cs_dup")).thenReturn(Optional.of(basket));
        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_dup");

        service.handleSessionCompleted(session);

        verifyNoInteractions(membershipDuesService);
        verify(basketRepository, never()).save(any());
    }

    @Test
    void webhook_success_poolsWalletLedgersAndDispatchesTheRest() {
        UUID replenishmentId = UUID.randomUUID();
        UUID fineId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        // Dues/enrollment/MGR/fine/replenishment all represent real obligations and are pooled
        // through PaymentAllocationService; program-prepay and general-contribution are not.
        PaymentBasket basket = basketOf(member, PaymentStatus.PENDING,
                line(PaymentBasketLedger.DUES, null, new BigDecimal("50.00")),
                line(PaymentBasketLedger.BENEVOLENCE_ENROLLMENT, null, new BigDecimal("200.00")),
                line(PaymentBasketLedger.MGR_CONTRIBUTION, null, new BigDecimal("100.00")),
                line(PaymentBasketLedger.FINE, fineId, new BigDecimal("25.00")),
                line(PaymentBasketLedger.BENEVOLENCE_REPLENISHMENT, replenishmentId, new BigDecimal("60.00")),
                line(PaymentBasketLedger.PROGRAM_APPLICATION_PREPAY, applicationId, new BigDecimal("150.00")),
                line(PaymentBasketLedger.GENERAL_CONTRIBUTION, planId, new BigDecimal("20.00")));
        when(basketRepository.findBySessionId("cs_success")).thenReturn(Optional.of(basket));
        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_success");

        service.handleSessionCompleted(session);

        verify(paymentAllocationService).applyPayment(member, new BigDecimal("435.00"));
        verifyNoInteractions(membershipDuesService, benevolenceEnrollmentService, mgrService, fineService, benevolenceClaimService);
        verify(programApplicationService).applyPrepayment(applicationId, member, new BigDecimal("150.00"));
        verify(contributionService).applyBasketContribution(member, new BigDecimal("20.00"), planId);
        assertEquals(PaymentStatus.SUCCESS, basket.getStatus());
        assertNotNull(basket.getPaidAt());
    }

    @Test
    void webhook_pooledAllocationThrows_nonPooledLinesStillCredited() {
        UUID planId = UUID.randomUUID();
        PaymentBasket basket = basketOf(member, PaymentStatus.PENDING,
                line(PaymentBasketLedger.DUES, null, new BigDecimal("50.00")),
                line(PaymentBasketLedger.MGR_CONTRIBUTION, null, new BigDecimal("100.00")),
                line(PaymentBasketLedger.GENERAL_CONTRIBUTION, planId, new BigDecimal("20.00")));
        when(basketRepository.findBySessionId("cs_partial_fail")).thenReturn(Optional.of(basket));
        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_partial_fail");

        doThrow(new RuntimeException("allocation blew up")).when(paymentAllocationService).applyPayment(any(), any());

        assertDoesNotThrow(() -> service.handleSessionCompleted(session));

        // The pooled allocation failing doesn't stop the independent general-contribution line.
        verify(contributionService).applyBasketContribution(member, new BigDecimal("20.00"), planId);
        assertEquals(PaymentStatus.SUCCESS, basket.getStatus(), "basket is still marked paid — Stripe was actually charged");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private FineDto fineDto(String status, BigDecimal amount) {
        return new FineDto(UUID.randomUUID(), member.getId(), member.getEmail(), member.getEmail(), "UW-1",
                null, null, "Late attendance", amount, null, status, null, null, null);
    }

    private PaymentBasketLine line(PaymentBasketLedger ledger, UUID targetId, BigDecimal amount) {
        return PaymentBasketLine.builder().ledger(ledger).targetId(targetId).amount(amount)
                .description(ledger.name()).build();
    }

    private PaymentBasket basketOf(User m, PaymentStatus status, PaymentBasketLine... lines) {
        PaymentBasket basket = PaymentBasket.builder().member(m).sessionId("cs_x").status(status).build();
        for (PaymentBasketLine l : lines) {
            l.setBasket(basket);
            basket.getLines().add(l);
        }
        return basket;
    }
}
