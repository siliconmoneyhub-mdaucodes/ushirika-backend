package com.mdau.ushirika.module.payment.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.attendance.service.FineService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.benevolence.service.BenevolenceClaimService;
import com.mdau.ushirika.module.benevolence.service.BenevolenceEnrollmentService;
import com.mdau.ushirika.module.dues.service.MembershipDuesService;
import com.mdau.ushirika.module.attendance.enums.FineStatus;
import com.mdau.ushirika.module.benevolence.enums.ReplenishmentPaymentStatus;
import com.mdau.ushirika.module.mgr.service.MgrService;
import com.mdau.ushirika.module.payment.dto.BasketLineDto;
import com.mdau.ushirika.module.payment.dto.OutstandingBalancesDto;
import com.mdau.ushirika.module.payment.dto.PaymentInitDto;
import com.mdau.ushirika.module.payment.entity.PaymentBasket;
import com.mdau.ushirika.module.payment.entity.PaymentBasketLine;
import com.mdau.ushirika.module.payment.enums.PaymentBasketLedger;
import com.mdau.ushirika.module.payment.enums.PaymentStatus;
import com.mdau.ushirika.module.payment.repository.PaymentBasketRepository;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    @Transactional(readOnly = true)
    public OutstandingBalancesDto myOutstandingBalances() {
        User member = currentUser();

        BigDecimal duesBalance = membershipDuesService.outstandingBalance(member);

        BenevolenceEnrollmentService.EnrollmentBalance benBalance = benevolenceEnrollmentService.outstandingBalance(member);
        OutstandingBalancesDto.BenevolenceBalance benevolence = benBalance != null
                ? new OutstandingBalancesDto.BenevolenceBalance(benBalance.balance(), benBalance.status())
                : null;

        BigDecimal mgrBalance = mgrService.outstandingContributionBalance(member);

        List<OutstandingBalancesDto.ReplenishmentItem> replenishments;
        try {
            replenishments = benevolenceClaimService.getMyReplenishments().stream()
                    .filter(r -> r.status() == ReplenishmentPaymentStatus.PENDING)
                    .map(r -> new OutstandingBalancesDto.ReplenishmentItem(r.id(), r.amountDue()))
                    .toList();
        } catch (ResourceNotFoundException e) {
            replenishments = List.of(); // not enrolled in Benevolence — no replenishment obligations
        }

        List<OutstandingBalancesDto.FineItem> fines = fineService.getMyFines().stream()
                .filter(f -> FineStatus.PENDING.name().equals(f.status()))
                .map(f -> new OutstandingBalancesDto.FineItem(f.id(), f.amount(), f.reason(), true))
                .toList();

        return new OutstandingBalancesDto(duesBalance, benevolence, mgrBalance, replenishments, fines);
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
        if (basket.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Duplicate webhook for payment basket sessionId={} — skipped", sessionId);
            return;
        }

        basket.setStatus(PaymentStatus.SUCCESS);
        basket.setPaidAt(LocalDateTime.now());
        basketRepository.save(basket);

        for (PaymentBasketLine line : basket.getLines()) {
            try {
                allocate(basket.getMember(), line);
            } catch (Exception e) {
                log.error("Failed to allocate payment basket line ledger={} targetId={} basket={}",
                        line.getLedger(), line.getTargetId(), basket.getId(), e);
            }
        }

        log.info("Payment basket confirmed via Stripe: sessionId={} member={} lines={}",
                sessionId, basket.getMember().getEmail(), basket.getLines().size());
    }

    private void allocate(User member, PaymentBasketLine line) {
        switch (line.getLedger()) {
            case REGISTRATION_FEE -> {
                // Nothing further to credit — OnboardingService checks for a SUCCESS basket
                // containing a REGISTRATION_FEE line directly (see Phase 1).
            }
            case DUES -> membershipDuesService.applyExternalPayment(member, line.getAmount());
            case BENEVOLENCE_ENROLLMENT -> benevolenceEnrollmentService.applyPayment(member, line.getAmount());
            case MGR_CONTRIBUTION -> mgrService.applyContribution(member, line.getAmount());
            case FINE -> fineService.markPaid(line.getTargetId());
            case BENEVOLENCE_REPLENISHMENT -> benevolenceClaimService.applyReplenishmentPayment(line.getTargetId(), line.getAmount());
        }
    }

    private String describe(PaymentBasketLedger ledger) {
        return switch (ledger) {
            case REGISTRATION_FEE -> "Ushirika Welfare — Registration Fee";
            case DUES -> "Ushirika Welfare — Annual Membership Dues";
            case BENEVOLENCE_ENROLLMENT -> "Ushirika Welfare — Benevolence Enrollment";
            case MGR_CONTRIBUTION -> "Ushirika Welfare — MGR Contribution";
            case FINE -> "Ushirika Welfare — Fine";
            case BENEVOLENCE_REPLENISHMENT -> "Ushirika Welfare — Benevolence Replenishment";
        };
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
