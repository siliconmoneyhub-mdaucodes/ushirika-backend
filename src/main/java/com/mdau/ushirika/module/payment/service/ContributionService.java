package com.mdau.ushirika.module.payment.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.payment.dto.*;
import com.mdau.ushirika.module.payment.entity.ContributionPlan;
import com.mdau.ushirika.module.payment.entity.MemberContribution;
import com.mdau.ushirika.module.payment.entity.StripePayment;
import com.mdau.ushirika.module.payment.enums.ContributionSource;
import com.mdau.ushirika.module.payment.enums.PaymentPurpose;
import com.mdau.ushirika.module.payment.enums.PaymentStatus;
import com.mdau.ushirika.module.payment.repository.ContributionPlanRepository;
import com.mdau.ushirika.module.payment.repository.MemberContributionRepository;
import com.mdau.ushirika.module.payment.repository.StripePaymentRepository;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionService {

    private final ContributionPlanRepository planRepository;
    private final MemberContributionRepository contributionRepository;
    private final StripePaymentRepository stripePaymentRepository;
    private final UserRepository userRepository;
    private final StripeService stripeService;

    // ----------------------------------------------------------------- Plans

    @Transactional(readOnly = true)
    public List<ContributionPlanDto> listActivePlans() {
        return planRepository.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(ContributionPlanDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ContributionPlanDto> listAllPlans() {
        return planRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(ContributionPlanDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ContributionPlanDto getPlan(UUID id) {
        return ContributionPlanDto.from(planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contribution plan not found: " + id)));
    }

    @Transactional
    public ContributionPlanDto createPlan(ContributionPlanRequest req) {
        ContributionPlan plan = ContributionPlan.builder()
                .name(req.name())
                .description(req.description())
                .amount(req.amount())
                .frequency(req.frequency())
                .features(req.features() != null ? req.features() : List.of())
                .badge(req.badge())
                .displayOrder(req.displayOrder())
                .active(req.active())
                .build();
        return ContributionPlanDto.from(planRepository.save(plan));
    }

    @Transactional
    public ContributionPlanDto updatePlan(UUID id, ContributionPlanRequest req) {
        ContributionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contribution plan not found: " + id));
        plan.setName(req.name());
        plan.setDescription(req.description());
        plan.setAmount(req.amount());
        plan.setFrequency(req.frequency());
        plan.setFeatures(req.features() != null ? req.features() : List.of());
        plan.setBadge(req.badge());
        plan.setDisplayOrder(req.displayOrder());
        plan.setActive(req.active());
        return ContributionPlanDto.from(planRepository.save(plan));
    }

    @Transactional
    public void deletePlan(UUID id) {
        ContributionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contribution plan not found: " + id));
        planRepository.delete(plan);
    }

    // ----------------------------------------------------------------- Payment initiation

    /**
     * Creates a Stripe Checkout Session for a member contribution.
     * Persists a PENDING StripePayment record.
     * MemberContribution is only created after checkout.session.completed webhook.
     */
    @Transactional
    public PaymentInitDto initiateContribution(InitiateContributionRequest req) {
        User member = currentUser();

        BigDecimal amount;
        ContributionPlan plan = null;

        if (req.planId() != null) {
            plan = planRepository.findById(req.planId())
                    .orElseThrow(() -> new ResourceNotFoundException("Contribution plan not found: " + req.planId()));
            if (!plan.isActive()) {
                throw new BadRequestException("This contribution plan is no longer active.");
            }
            amount = plan.getAmount();
        } else {
            if (req.customAmount() == null || req.customAmount().compareTo(BigDecimal.ONE) < 0) {
                throw new BadRequestException("Either a planId or a custom amount of at least 1.00 is required.");
            }
            amount = req.customAmount();
        }

        String planId = plan != null ? plan.getId().toString() : null;
        String productName = plan != null
                ? "Ushirika Welfare — " + plan.getName()
                : "Ushirika Welfare — Contribution (" + req.period() + ")";

        Map<String, String> metadata = new java.util.HashMap<>();
        metadata.put("purpose", PaymentPurpose.CONTRIBUTION.name());
        metadata.put("memberId", member.getId().toString());
        metadata.put("period", req.period());
        metadata.put("planId", planId != null ? planId : "custom");

        StripeService.StripeCheckoutResult result = stripeService.createCheckoutSession(
                member.getEmail(), amount, productName,
                req.successUrl(), req.cancelUrl(), metadata);

        StripePayment payment = StripePayment.builder()
                .sessionId(result.sessionId())
                .email(member.getEmail())
                .amount(amount)
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .purpose(PaymentPurpose.CONTRIBUTION)
                .user(member)
                .purposeEntityId(planId)
                .build();
        stripePaymentRepository.save(payment);

        log.info("Contribution checkout created: sessionId={} member={} amount={} USD",
                result.sessionId(), member.getEmail(), amount);

        return new PaymentInitDto(result.sessionId(), result.checkoutUrl(), amount, "USD");
    }

    // ----------------------------------------------------------------- Webhook handler

    /**
     * Called by StripeWebhookController after a checkout.session.completed event.
     * Idempotent — safe to call twice for the same session.
     */
    @Transactional
    public void handleSessionCompleted(Session session) {
        String sessionId = session.getId();

        if (contributionRepository.existsByPaymentSessionId(sessionId)) {
            log.info("Duplicate webhook for contribution sessionId={} — skipped", sessionId);
            return;
        }

        StripePayment payment = stripePaymentRepository.findBySessionId(sessionId).orElse(null);
        if (payment == null) {
            log.warn("Webhook received for unknown Stripe session={} — skipping", sessionId);
            return;
        }

        if (payment.getPurpose() != PaymentPurpose.CONTRIBUTION) return;

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        if (session.getPaymentIntent() != null) {
            payment.setPaymentIntentId(session.getPaymentIntent());
        }
        stripePaymentRepository.save(payment);

        Map<String, String> meta = session.getMetadata() != null ? session.getMetadata() : Map.of();
        String period  = meta.getOrDefault("period", "");
        String planStr = meta.getOrDefault("planId", "custom");

        ContributionPlan plan = null;
        if (!"custom".equals(planStr) && !planStr.isBlank()) {
            plan = planRepository.findById(UUID.fromString(planStr)).orElse(null);
        }

        // amount_total is in cents
        BigDecimal amountUsd = BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100));

        MemberContribution contribution = MemberContribution.builder()
                .member(payment.getUser())
                .plan(plan)
                .payment(payment)
                .source(ContributionSource.STRIPE)
                .amount(amountUsd)
                .currency("USD")
                .period(period)
                .build();
        contributionRepository.save(contribution);

        log.info("Contribution confirmed via Stripe: sessionId={} member={} amount={} USD period={}",
                sessionId,
                payment.getUser() != null ? payment.getUser().getEmail() : "unknown",
                amountUsd, period);
    }

    /** Credits a confirmed Stripe payment-basket line as a general contribution (folded in from
     * the "pay my balances" checkout) — planId is optional, null for a custom-amount gift. */
    @Transactional
    public void applyBasketContribution(User member, BigDecimal amount, UUID planId) {
        ContributionPlan plan = planId != null ? planRepository.findById(planId).orElse(null) : null;

        MemberContribution contribution = MemberContribution.builder()
                .member(member)
                .plan(plan)
                .source(ContributionSource.STRIPE)
                .amount(amount)
                .currency("USD")
                .build();
        contributionRepository.save(contribution);
    }

    // ----------------------------------------------------------------- Member queries

    @Transactional(readOnly = true)
    public PagedResponse<MemberContributionDto> myContributions(Pageable pageable) {
        User member = currentUser();
        return PagedResponse.of(contributionRepository.findAllByMember(member, pageable)
                .map(MemberContributionDto::from));
    }

    @Transactional(readOnly = true)
    public ContributionSummaryDto mySummary() {
        User member = currentUser();
        BigDecimal total = contributionRepository.sumByMember(member);
        long count = contributionRepository.countByMember(member);
        return new ContributionSummaryDto(total, "USD", count);
    }

    // ----------------------------------------------------------------- Admin queries

    @Transactional(readOnly = true)
    public PagedResponse<AdminMemberContributionDto> listAll(Pageable pageable) {
        return PagedResponse.of(contributionRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(AdminMemberContributionDto::from));
    }

    // ----------------------------------------------------------------- Helpers

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
