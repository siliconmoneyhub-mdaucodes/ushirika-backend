package com.mdau.ushirika.module.payment.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.attendance.service.FineService;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.benevolence.service.BenevolenceClaimService;
import com.mdau.ushirika.module.benevolence.service.BenevolenceEnrollmentService;
import com.mdau.ushirika.module.dues.service.MembershipDuesService;
import com.mdau.ushirika.module.attendance.enums.FineStatus;
import com.mdau.ushirika.module.benevolence.enums.ReplenishmentPaymentStatus;
import com.mdau.ushirika.module.mgr.service.MgrService;
import com.mdau.ushirika.module.attendance.dto.FineDto;
import com.mdau.ushirika.module.notification.dto.BroadcastRequest;
import com.mdau.ushirika.module.notification.enums.InAppNotificationCategory;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import com.mdau.ushirika.module.payment.dto.AdminCashPaymentRequest;
import com.mdau.ushirika.module.payment.dto.BasketLineDto;
import com.mdau.ushirika.module.payment.dto.MemberBalanceDto;
import com.mdau.ushirika.module.payment.dto.OutstandingBalancesDto;
import com.mdau.ushirika.module.payment.dto.PayBalancesLineDto;
import com.mdau.ushirika.module.payment.dto.PaymentBasketSummaryDto;
import com.mdau.ushirika.module.payment.dto.PaymentInitDto;
import com.mdau.ushirika.module.payment.entity.PaymentBasket;
import com.mdau.ushirika.module.payment.entity.PaymentBasketLine;
import com.mdau.ushirika.module.payment.enums.PaymentBasketLedger;
import com.mdau.ushirika.module.payment.enums.PaymentStatus;
import com.mdau.ushirika.module.payment.repository.PaymentBasketRepository;
import com.mdau.ushirika.module.program.service.ProgramApplicationService;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds and settles multi-line-item Stripe Checkout Sessions that can cover several
 * unrelated ledgers (dues, Benevolence, MGR, fines, replenishments) in one card charge.
 * Callers (onboarding checkout, "pay my balances") are responsible for validating each
 * line's amount against the member's real outstanding balance before calling startCheckout —
 * this service builds the session and allocates the webhook confirmation, it does not
 * itself decide what a member is allowed to pay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentBasketService {

    private final PaymentBasketRepository basketRepository;
    private final StripeService stripeService;
    private final UserRepository userRepository;
    private final MembershipDuesService membershipDuesService;
    private final BenevolenceEnrollmentService benevolenceEnrollmentService;
    private final MgrService mgrService;
    private final FineService fineService;
    private final BenevolenceClaimService benevolenceClaimService;
    private final ProgramApplicationService programApplicationService;
    private final ContributionService contributionService;
    private final PlatformSettingsService platformSettingsService;
    private final PaymentAllocationService paymentAllocationService;
    private final AuditLogService auditLogService;
    private final InAppNotificationService notificationService;

    @Transactional(readOnly = true)
    public OutstandingBalancesDto myOutstandingBalances() {
        User member = currentUser();

        BigDecimal duesBalance = membershipDuesService.outstandingBalance(member);

        BenevolenceEnrollmentService.EnrollmentBalance benBalance = benevolenceEnrollmentService.outstandingBalance(member);
        OutstandingBalancesDto.BenevolenceBalance benevolence = benBalance != null
                ? new OutstandingBalancesDto.BenevolenceBalance(benBalance.balance(), benBalance.status())
                : null;

        BigDecimal mgrBalance = mgrService.outstandingContributionBalance(member);

        // getMyReplenishments() throws ResourceNotFoundException when not enrolled, and it's
        // itself @Transactional — letting that propagate (even caught here) marks this method's
        // shared physical transaction rollback-only, surfacing as UnexpectedRollbackException at
        // commit. benBalance != null already tells us enrollment status, so skip the call
        // entirely rather than relying on a same-transaction catch to "recover" from it.
        List<OutstandingBalancesDto.ReplenishmentItem> replenishments = benBalance != null
                ? benevolenceClaimService.getMyReplenishments().stream()
                        .filter(r -> r.status() == ReplenishmentPaymentStatus.PENDING)
                        .map(r -> new OutstandingBalancesDto.ReplenishmentItem(r.id(), r.amountDue()))
                        .toList()
                : List.of();

        List<OutstandingBalancesDto.FineItem> fines = fineService.getMyFines().stream()
                .filter(f -> FineStatus.PENDING.name().equals(f.status()))
                .map(f -> new OutstandingBalancesDto.FineItem(f.id(), f.amount(), f.reason(), true))
                .toList();

        return new OutstandingBalancesDto(duesBalance, benevolence, mgrBalance, replenishments, fines,
                contributionService.listActivePlans());
    }

    /**
     * Builds a validated "pay my balances" basket. Every requested line is re-checked against
     * the member's real outstanding balance — nothing from the request is trusted as-is:
     *   - DUES / BENEVOLENCE_ENROLLMENT / MGR_CONTRIBUTION: amount capped at the real balance.
     *   - FINE: ignored entirely — the member's actual PENDING fines are force-included below,
     *     regardless of what was (or wasn't) requested, per the mandatory-fine requirement.
     *   - BENEVOLENCE_REPLENISHMENT: must belong to the member, PENDING, amount must match exactly.
     *   - GENERAL_CONTRIBUTION: no balance to cap against — any positive amount is accepted.
     *   - REGISTRATION_FEE / PROGRAM_APPLICATION_PREPAY: onboarding-only, rejected here.
     */
    @Transactional
    public PaymentInitDto startBalancesCheckout(List<PayBalancesLineDto> requested, String successUrl, String cancelUrl) {
        User member = currentUser();
        List<BasketLineDto> lines = new ArrayList<>();

        for (PayBalancesLineDto req : requested != null ? requested : List.<PayBalancesLineDto>of()) {
            switch (req.ledger()) {
                case DUES -> {
                    BigDecimal balance = membershipDuesService.outstandingBalance(member);
                    lines.add(new BasketLineDto(PaymentBasketLedger.DUES, null, capped(req.amount(), balance, "dues")));
                }
                case BENEVOLENCE_ENROLLMENT -> {
                    BenevolenceEnrollmentService.EnrollmentBalance bal = benevolenceEnrollmentService.outstandingBalance(member);
                    BigDecimal balance = bal != null ? bal.balance() : BigDecimal.ZERO;
                    lines.add(new BasketLineDto(PaymentBasketLedger.BENEVOLENCE_ENROLLMENT, null, capped(req.amount(), balance, "Benevolence enrollment")));
                }
                case MGR_CONTRIBUTION -> {
                    BigDecimal balance = mgrService.outstandingContributionBalance(member);
                    lines.add(new BasketLineDto(PaymentBasketLedger.MGR_CONTRIBUTION, null, capped(req.amount(), balance, "MGR contribution")));
                }
                case BENEVOLENCE_REPLENISHMENT -> {
                    if (req.targetId() == null) {
                        throw new BadRequestException("A replenishment obligation must be specified.");
                    }
                    var match = benevolenceClaimService.getMyReplenishments().stream()
                            .filter(r -> r.id().equals(req.targetId()) && r.status() == ReplenishmentPaymentStatus.PENDING)
                            .findFirst()
                            .orElseThrow(() -> new BadRequestException("That replenishment obligation is not outstanding."));
                    if (req.amount().compareTo(match.amountDue()) != 0) {
                        throw new BadRequestException("Replenishment payments must be paid in full — expected $" + match.amountDue());
                    }
                    lines.add(new BasketLineDto(PaymentBasketLedger.BENEVOLENCE_REPLENISHMENT, req.targetId(), req.amount()));
                }
                case GENERAL_CONTRIBUTION -> lines.add(new BasketLineDto(PaymentBasketLedger.GENERAL_CONTRIBUTION, req.targetId(), req.amount()));
                case FINE -> { /* fines are never client-selected — force-included below */ }
                case REGISTRATION_FEE, PROGRAM_APPLICATION_PREPAY ->
                        throw new BadRequestException("That item can only be paid during onboarding.");
                case CASH_PAYMENT ->
                        throw new BadRequestException("Cash payments are processed by an admin, not selected here.");
            }
        }

        // Fines are mandatory whenever a balances checkout is started — force-included, not optional.
        for (FineDto fine : fineService.getMyFines()) {
            if (FineStatus.PENDING.name().equals(fine.status())) {
                lines.add(new BasketLineDto(PaymentBasketLedger.FINE, fine.id(), fine.amount()));
            }
        }

        if (lines.isEmpty()) {
            throw new BadRequestException("Nothing to pay — no balances selected and no outstanding fines.");
        }

        return startCheckout(lines, successUrl, cancelUrl);
    }

    private BigDecimal capped(BigDecimal requested, BigDecimal balance, String label) {
        if (balance.signum() <= 0) {
            throw new BadRequestException("You have no outstanding " + label + " balance.");
        }
        return requested.compareTo(balance) > 0 ? balance : requested;
    }

    /** Onboarding "Programs" + "Registration Fee" steps combined: a fixed $100 registration
     * fee plus an optional amount toward a Benevolence application the applicant already
     * created — full $600, a partial installment, or omitted entirely to defer. */
    @Transactional
    public PaymentInitDto startOnboardingCheckout(BigDecimal benevolenceAmount, UUID benevolenceApplicationId,
                                                    String successUrl, String cancelUrl) {
        User applicant = currentUser();
        List<BasketLineDto> lines = new ArrayList<>();
        lines.add(new BasketLineDto(PaymentBasketLedger.REGISTRATION_FEE, null, platformSettingsService.getRegistrationFeeAmount()));

        if (benevolenceAmount != null && benevolenceAmount.signum() > 0) {
            if (benevolenceApplicationId == null) {
                throw new BadRequestException("Select which program application this payment is for.");
            }
            programApplicationService.validatePrepayable(benevolenceApplicationId, applicant, benevolenceAmount);
            lines.add(new BasketLineDto(PaymentBasketLedger.PROGRAM_APPLICATION_PREPAY, benevolenceApplicationId, benevolenceAmount));
        }

        return startCheckout(lines, successUrl, cancelUrl);
    }

    @Transactional(readOnly = true)
    public MemberBalanceDto myBalance() {
        return paymentAllocationService.getBalance(currentUser());
    }

    /**
     * An admin processing cash they physically received: the admin pays this exact amount
     * themselves via the resulting Stripe Checkout session (their own card, not the member's —
     * nobody can type someone else's card into a form on their behalf) and the member is only
     * credited once that real charge succeeds. Reuses the same webhook/allocation path as every
     * other payment; the only difference from a self-checkout is who the Stripe payer is.
     */
    @Transactional
    public PaymentInitDto startAdminCashCheckout(AdminCashPaymentRequest req) {
        User admin = currentUser();
        User targetMember = userRepository.findById(req.memberId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + req.memberId()));

        List<StripeService.LineItem> stripeLines = List.of(
                new StripeService.LineItem("Ushirika Welfare Organization — Cash Payment for " + targetMember.getFullName(), req.amount()));

        Map<String, String> metadata = Map.of(
                "purpose", "BASKET",
                "memberId", targetMember.getId().toString()
        );

        // The admin's email is the Stripe customer/payer of record — the basket's "member" is
        // still the person being credited, same as every other basket.
        StripeService.StripeCheckoutResult result = stripeService.createCheckoutSession(
                admin.getEmail(), stripeLines, req.successUrl(), req.cancelUrl(), metadata);

        PaymentBasket basket = PaymentBasket.builder()
                .member(targetMember)
                .sessionId(result.sessionId())
                .build();
        basket.getLines().add(PaymentBasketLine.builder()
                .basket(basket)
                .ledger(PaymentBasketLedger.CASH_PAYMENT)
                // Piggybacks targetId to record who processed it — there's no other obligation
                // this line targets, and completeBasket() needs this admin's identity at webhook
                // time, when there's no authenticated request to read it from.
                .targetId(admin.getId())
                .description("Cash payment processed by " + admin.getFullName())
                .amount(req.amount())
                .build());
        basketRepository.save(basket);

        log.info("Cash payment checkout created: sessionId={} member={} admin={} amount={} USD",
                result.sessionId(), targetMember.getEmail(), admin.getEmail(), req.amount());

        return new PaymentInitDto(result.sessionId(), result.checkoutUrl(), req.amount(), "USD");
    }

    @Transactional
    public PaymentInitDto startCheckout(List<BasketLineDto> lines, String successUrl, String cancelUrl) {
        User member = currentUser();
        if (lines == null || lines.isEmpty()) {
            throw new BadRequestException("Select at least one item to pay.");
        }

        List<StripeService.LineItem> stripeLines = lines.stream()
                .map(l -> new StripeService.LineItem(describe(l.ledger()), l.amount()))
                .toList();

        Map<String, String> metadata = Map.of(
                "purpose", "BASKET",
                "memberId", member.getId().toString()
        );

        StripeService.StripeCheckoutResult result = stripeService.createCheckoutSession(
                member.getEmail(), stripeLines, successUrl, cancelUrl, metadata);

        PaymentBasket basket = PaymentBasket.builder()
                .member(member)
                .sessionId(result.sessionId())
                .build();
        for (BasketLineDto l : lines) {
            basket.getLines().add(PaymentBasketLine.builder()
                    .basket(basket)
                    .ledger(l.ledger())
                    .targetId(l.targetId())
                    .description(describe(l.ledger()))
                    .amount(l.amount())
                    .build());
        }
        basketRepository.save(basket);

        BigDecimal total = lines.stream().map(BasketLineDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("Payment basket checkout created: sessionId={} member={} lines={} total={} USD",
                result.sessionId(), member.getEmail(), lines.size(), total);

        return new PaymentInitDto(result.sessionId(), result.checkoutUrl(), total, "USD");
    }

    /** Called by StripeWebhookController after a checkout.session.completed event with purpose=BASKET. */
    @Transactional
    public void handleSessionCompleted(Session session) {
        String sessionId = session.getId();
        PaymentBasket basket = basketRepository.findBySessionId(sessionId).orElse(null);
        if (basket == null) {
            log.warn("Webhook received for unknown payment basket session={} — skipping", sessionId);
            return;
        }
        completeBasket(basket, "Stripe webhook");
    }

    /**
     * SUPERADMIN-only test tool (see AdminPaymentSimulationController) — completes a PENDING
     * basket exactly as the real Stripe webhook would, without any real payment. Exists so the
     * card-only checkout flows can be tested end-to-end (including the confirmation emails and
     * downstream allocation) without needing a real card or a completed Stripe Checkout session.
     */
    @Transactional
    public void simulateSuccess(UUID basketId) {
        PaymentBasket basket = basketRepository.findById(basketId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment basket not found: " + basketId));
        completeBasket(basket, "SIMULATED (test tool, no real payment)");
    }

    @Transactional(readOnly = true)
    public List<PaymentBasketSummaryDto> listPendingBaskets() {
        return basketRepository.findAllByStatusOrderByCreatedAtDesc(PaymentStatus.PENDING).stream()
                .map(PaymentBasketSummaryDto::from)
                .toList();
    }

    private void completeBasket(PaymentBasket basket, String source) {
        if (basket.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Duplicate completion for payment basket id={} sessionId={} — skipped", basket.getId(), basket.getSessionId());
            return;
        }

        basket.setStatus(PaymentStatus.SUCCESS);
        basket.setPaidAt(LocalDateTime.now());
        basketRepository.save(basket);

        // Dues/MGR/fines/replenishment/Benevolence-enrollment all represent real obligations, so
        // their amounts are pooled and settled together through PaymentAllocationService in a
        // fixed priority order (fines, dues, MGR, replenishment, enrollment) rather than each
        // being credited to the exact line it arrived on — this is what lets one payment settle
        // whatever's oldest/most pressing regardless of which balance the payer thought they were
        // paying, and what lets any leftover become credit instead of being silently dropped.
        BigDecimal walletEligibleTotal = BigDecimal.ZERO;
        for (PaymentBasketLine line : basket.getLines()) {
            try {
                if (isWalletEligible(line.getLedger())) {
                    walletEligibleTotal = walletEligibleTotal.add(line.getAmount());
                } else {
                    allocate(basket.getMember(), line);
                }
            } catch (Exception e) {
                log.error("Failed to allocate payment basket line ledger={} targetId={} basket={}",
                        line.getLedger(), line.getTargetId(), basket.getId(), e);
            }
        }
        if (walletEligibleTotal.signum() > 0) {
            try {
                paymentAllocationService.applyPayment(basket.getMember(), walletEligibleTotal);
            } catch (Exception e) {
                log.error("Failed to settle pooled payment amount={} basket={}", walletEligibleTotal, basket.getId(), e);
            }
        }

        basket.getLines().stream()
                .filter(l -> l.getLedger() == PaymentBasketLedger.CASH_PAYMENT)
                .findFirst()
                .ifPresent(cashLine -> notifyCashPaymentProcessed(basket.getMember(), cashLine));

        log.info("Payment basket confirmed [{}]: id={} sessionId={} member={} lines={}",
                source, basket.getId(), basket.getSessionId(), basket.getMember().getEmail(), basket.getLines().size());
    }

    /** Cash-payment-specific side effects once the pooled allocation above has already run:
     * confirm to the member (in-app + email) and record who processed it for accounting. */
    private void notifyCashPaymentProcessed(User member, PaymentBasketLine cashLine) {
        try {
            notificationService.notifyMember(member.getId(), new BroadcastRequest(
                    InAppNotificationCategory.GENERAL,
                    "Cash Payment Confirmed",
                    "Your cash payment of $" + cashLine.getAmount() + " has been processed and applied to your account.",
                    null,
                    Set.of("EMAIL")
            ));
        } catch (Exception e) {
            log.warn("Cash payment confirmation notification failed for {}: {}", member.getEmail(), e.getMessage());
        }

        User admin = cashLine.getTargetId() != null ? userRepository.findById(cashLine.getTargetId()).orElse(null) : null;
        if (admin != null) {
            auditLogService.log(admin, "CASH_PAYMENT_PROCESSED", "User", member.getId(),
                    "Cash payment of $" + cashLine.getAmount() + " processed for " + member.getFullName()
                            + " by " + admin.getFullName());
        }
    }

    /** Obligations settled through the pooled PaymentAllocationService rather than credited to
     * the specific line they arrived on. Registration fee, program prepayment, and general
     * contributions stay outside the pool — the first two are one-time onboarding gates, and
     * contributions are voluntary with no fixed amount owed, so they're never redirected to pay
     * down something else. */
    private boolean isWalletEligible(PaymentBasketLedger ledger) {
        return switch (ledger) {
            case DUES, MGR_CONTRIBUTION, FINE, BENEVOLENCE_REPLENISHMENT, BENEVOLENCE_ENROLLMENT, CASH_PAYMENT -> true;
            case REGISTRATION_FEE, PROGRAM_APPLICATION_PREPAY, GENERAL_CONTRIBUTION -> false;
        };
    }

    private void allocate(User member, PaymentBasketLine line) {
        switch (line.getLedger()) {
            case REGISTRATION_FEE -> {
                // Nothing further to credit — OnboardingService checks for a SUCCESS basket
                // containing a REGISTRATION_FEE line directly (see Phase 1).
            }
            case PROGRAM_APPLICATION_PREPAY -> programApplicationService.applyPrepayment(line.getTargetId(), member, line.getAmount());
            case GENERAL_CONTRIBUTION -> contributionService.applyBasketContribution(member, line.getAmount(), line.getTargetId());
            default -> throw new IllegalStateException("Ledger " + line.getLedger() + " should have been routed through the pooled allocator");
        }
    }

    private String describe(PaymentBasketLedger ledger) {
        return switch (ledger) {
            case REGISTRATION_FEE -> "Ushirika Welfare Organization — Registration Fee";
            case DUES -> "Ushirika Welfare Organization — Annual Membership Dues";
            case BENEVOLENCE_ENROLLMENT -> "Ushirika Welfare Organization — Benevolence Enrollment";
            case MGR_CONTRIBUTION -> "Ushirika Welfare Organization — MGR Contribution";
            case FINE -> "Ushirika Welfare Organization — Fine";
            case BENEVOLENCE_REPLENISHMENT -> "Ushirika Welfare Organization — Benevolence Replenishment";
            case PROGRAM_APPLICATION_PREPAY -> "Ushirika Welfare Organization — Benevolence Enrollment (prepayment)";
            case GENERAL_CONTRIBUTION -> "Ushirika Welfare Organization — Contribution";
            case CASH_PAYMENT -> "Ushirika Welfare Organization — Cash Payment";
        };
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
