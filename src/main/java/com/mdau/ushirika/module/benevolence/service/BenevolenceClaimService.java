package com.mdau.ushirika.module.benevolence.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.benevolence.dto.*;
import com.mdau.ushirika.module.benevolence.entity.*;
import com.mdau.ushirika.module.benevolence.enums.*;
import com.mdau.ushirika.module.benevolence.repository.*;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BenevolenceClaimService {

    private static final BigDecimal MAX_CLAIM_AMOUNT = new BigDecimal("5000.00");

    private final BenevolenceClaimRepository claimRepo;
    private final BenevolenceEnrollmentRepository enrollmentRepo;
    private final BenevolenceBeneficiaryRepository beneficiaryRepo;
    private final BenevolenceReplenishmentRepository replenishmentRepo;
    private final ReplenishmentPaymentRepository replenPaymentRepo;
    private final BenevolenceClaimCategoryRepository categoryRepo;
    private final MemberProfileRepository profileRepo;
    private final UserRepository userRepo;

    // ── Member: Submit Claim ──────────────────────────────────────────────────

    @Transactional
    public BenevolenceClaimDto submitClaim(SubmitClaimRequest req) {
        User user = currentUser();
        BenevolenceEnrollment enrollment = enrollmentRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "You are not enrolled in the benevolence program."));

        if (enrollment.getStatus() != EnrollmentStatus.ELIGIBLE) {
            String reason = enrollment.getStatus() == EnrollmentStatus.PAYING
                    ? "Your enrollment fee is not fully paid."
                    : "You are still in the 6-month probation period ending "
                      + enrollment.getProbationEndsAt() + ".";
            throw new BadRequestException("Claim not allowed: " + reason);
        }

        BenevolenceBeneficiary beneficiary = null;
        if (req.beneficiaryId() != null) {
            beneficiary = beneficiaryRepo.findById(req.beneficiaryId())
                    .filter(b -> b.getEnrollment().getId().equals(enrollment.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Beneficiary not found or does not belong to your enrollment."));
        }

        BenevolenceClaimCategory category = null;
        if (req.categoryId() != null) {
            category = categoryRepo.findById(req.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Claim category not found: " + req.categoryId()));
            if (!category.isActive()) {
                throw new BadRequestException("This claim category is no longer available.");
            }
        }

        BenevolenceClaim claim = BenevolenceClaim.builder()
                .enrollment(enrollment)
                .beneficiary(beneficiary)
                .category(category)
                .referenceNumber(generateRef())
                .deceasedName(req.deceasedName())
                .relationship(req.relationship())
                .dateOfDeath(req.dateOfDeath())
                .locationOfDeath(req.locationOfDeath())
                .funeralDate(req.funeralDate())
                .funeralLocation(req.funeralLocation())
                .contactName(req.contactName())
                .contactPhone(req.contactPhone())
                .description(req.description())
                .documentUrls(req.documentUrls() != null ? req.documentUrls() : List.of())
                .submittedAt(LocalDateTime.now())
                .build();
        claimRepo.save(claim);
        return BenevolenceClaimDto.from(claim, memberId(user));
    }

    @Transactional(readOnly = true)
    public List<BenevolenceClaimDto> getMyClaims() {
        User user = currentUser();
        BenevolenceEnrollment enrollment = enrollmentRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "You are not enrolled in the benevolence program."));
        String mid = memberId(user);
        return claimRepo.findByEnrollmentOrderBySubmittedAtDesc(enrollment)
                .stream()
                .map(c -> BenevolenceClaimDto.from(c, mid))
                .toList();
    }

    // ── Admin: Claims ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<BenevolenceClaimDto> listClaims(ClaimStatus status, Pageable pageable) {
        Page<BenevolenceClaim> page = status != null
                ? claimRepo.findAllByStatusOrderBySubmittedAtDesc(status, pageable)
                : claimRepo.findAllByOrderBySubmittedAtDesc(pageable);
        return PagedResponse.of(page.map(c ->
                BenevolenceClaimDto.from(c, memberId(c.getEnrollment().getUser()))));
    }

    @Transactional(readOnly = true)
    public BenevolenceClaimDto getClaimById(UUID id) {
        BenevolenceClaim c = findClaimById(id);
        return BenevolenceClaimDto.from(c, memberId(c.getEnrollment().getUser()));
    }

    @Transactional
    public BenevolenceClaimDto reviewClaim(UUID id, ReviewClaimRequest req) {
        BenevolenceClaim claim = findClaimById(id);
        if (claim.getStatus() != ClaimStatus.SUBMITTED && claim.getStatus() != ClaimStatus.UNDER_REVIEW) {
            throw new BadRequestException("Claim is already " + claim.getStatus() + " — cannot review.");
        }

        String decision = req.decision().toUpperCase();
        switch (decision) {
            case "APPROVE" -> {
                if (req.amountApproved() == null || req.amountApproved().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Amount approved is required for approval.");
                }
                if (req.amountApproved().compareTo(MAX_CLAIM_AMOUNT) > 0) {
                    throw new BadRequestException("Approved amount exceeds maximum of $5,000.");
                }
                claim.setStatus(ClaimStatus.APPROVED);
                claim.setAmountApproved(req.amountApproved());
            }
            case "REJECT" -> {
                claim.setStatus(ClaimStatus.REJECTED);
                claim.setRejectionReason(req.rejectionReason());
            }
            case "UNDER_REVIEW" -> claim.setStatus(ClaimStatus.UNDER_REVIEW);
            default -> throw new BadRequestException("Invalid decision: " + req.decision()
                    + ". Use APPROVE, REJECT, or UNDER_REVIEW.");
        }
        claim.setAdminNotes(req.adminNotes());
        claim.setReviewedAt(LocalDateTime.now());
        claimRepo.save(claim);
        return BenevolenceClaimDto.from(claim, memberId(claim.getEnrollment().getUser()));
    }

    @Transactional
    public BenevolenceClaimDto authorizeDisbursement(UUID id) {
        BenevolenceClaim claim = findClaimById(id);
        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new BadRequestException("Claim must be APPROVED before authorizing disbursement.");
        }
        claim.setStatus(ClaimStatus.PAYMENT_AUTHORIZED);
        claimRepo.save(claim);
        return BenevolenceClaimDto.from(claim, memberId(claim.getEnrollment().getUser()));
    }

    @Transactional
    public BenevolenceClaimDto markDisbursed(UUID id) {
        BenevolenceClaim claim = findClaimById(id);
        if (claim.getStatus() != ClaimStatus.PAYMENT_AUTHORIZED) {
            throw new BadRequestException("Claim must be PAYMENT_AUTHORIZED before marking as disbursed.");
        }
        claim.setStatus(ClaimStatus.DISBURSED);
        claim.setDisbursedAt(LocalDateTime.now());
        claimRepo.save(claim);

        if (claim.getBeneficiary() != null) {
            BenevolenceBeneficiary b = claim.getBeneficiary();
            b.setDeceased(true);
            b.setDeceasedAt(LocalDateTime.now());
            beneficiaryRepo.save(b);
        }

        return BenevolenceClaimDto.from(claim, memberId(claim.getEnrollment().getUser()));
    }

    // ── Admin: Replenishments ─────────────────────────────────────────────────

    @Transactional
    public BenevolenceReplenishmentDto createReplenishment(CreateReplenishmentRequest req) {
        BenevolenceClaim claim = null;
        if (req.claimId() != null) {
            claim = findClaimById(req.claimId());
            if (claim.getStatus() != ClaimStatus.DISBURSED) {
                throw new BadRequestException("Claim must be DISBURSED before creating replenishment.");
            }
        }

        List<BenevolenceEnrollment> eligible = enrollmentRepo.findAllByStatus(EnrollmentStatus.ELIGIBLE);
        int count = eligible.size();
        if (count == 0) {
            throw new BadRequestException("No eligible members to create replenishment for.");
        }

        BigDecimal perMember = claim != null && claim.getAmountApproved() != null
                ? claim.getAmountApproved().divide(BigDecimal.valueOf(count), 2, RoundingMode.CEILING)
                : BigDecimal.ZERO;

        BigDecimal total = perMember.multiply(BigDecimal.valueOf(count));

        BenevolenceReplenishment replenishment = BenevolenceReplenishment.builder()
                .claim(claim)
                .totalAmount(total)
                .perMemberAmount(perMember)
                .dueDate(req.dueDate())
                .notes(req.notes())
                .build();
        replenishmentRepo.save(replenishment);

        for (BenevolenceEnrollment e : eligible) {
            ReplenishmentPayment rp = ReplenishmentPayment.builder()
                    .replenishment(replenishment)
                    .enrollment(e)
                    .amountDue(perMember)
                    .build();
            replenPaymentRepo.save(rp);
        }

        return toReplenishmentDto(replenishment);
    }

    @Transactional(readOnly = true)
    public PagedResponse<BenevolenceReplenishmentDto> listReplenishments(Pageable pageable) {
        Page<BenevolenceReplenishment> page = replenishmentRepo.findAllByOrderByCreatedAtDesc(pageable);
        return PagedResponse.of(page.map(r -> {
            List<ReplenishmentPayment> payments = replenPaymentRepo.findByReplenishment(r);
            long paidCount = replenPaymentRepo.countByReplenishmentAndStatus(r, ReplenishmentPaymentStatus.PAID);
            return BenevolenceReplenishmentDto.summary(r, payments.size(), paidCount);
        }));
    }

    @Transactional(readOnly = true)
    public BenevolenceReplenishmentDto getReplenishmentById(UUID id) {
        BenevolenceReplenishment r = replenishmentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Replenishment not found: " + id));
        return toReplenishmentDto(r);
    }

    /** Credits a confirmed Stripe payment-basket line toward one specific replenishment obligation. */
    @Transactional
    public void applyReplenishmentPayment(UUID paymentId, BigDecimal amount) {
        ReplenishmentPayment rp = replenPaymentRepo.findById(paymentId).orElse(null);
        if (rp == null || rp.getStatus() != ReplenishmentPaymentStatus.PENDING) {
            return;
        }
        rp.setAmountPaid(amount);
        rp.setPaidAt(LocalDateTime.now());
        rp.setPaymentMethod("STRIPE");
        rp.setStatus(ReplenishmentPaymentStatus.PAID);
        replenPaymentRepo.save(rp);
    }

    /** This member's outstanding replenishment obligations, oldest first — used by
     * PaymentAllocationService to settle them in priority order. Empty if not enrolled. */
    @Transactional(readOnly = true)
    public List<ReplenishmentPayment> getPendingReplenishmentsForMember(User member) {
        return enrollmentRepo.findByUser(member)
                .map(enrollment -> replenPaymentRepo.findByEnrollmentAndStatusOrderByCreatedAtAsc(
                        enrollment, ReplenishmentPaymentStatus.PENDING))
                .orElse(List.of());
    }

    @Transactional
    public ReplenishmentPaymentDto waiveReplenishmentPayment(UUID replenishmentPaymentId, String reason) {
        ReplenishmentPayment rp = replenPaymentRepo.findById(replenishmentPaymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Replenishment payment not found: " + replenishmentPaymentId));
        if (rp.getStatus() == ReplenishmentPaymentStatus.PAID) {
            throw new BadRequestException("Cannot waive an already paid obligation.");
        }
        rp.setStatus(ReplenishmentPaymentStatus.WAIVED);
        replenPaymentRepo.save(rp);
        return ReplenishmentPaymentDto.from(rp, memberId(rp.getEnrollment().getUser()));
    }

    @Transactional(readOnly = true)
    public List<ReplenishmentPaymentDto> getMyReplenishments() {
        User user = currentUser();
        BenevolenceEnrollment enrollment = enrollmentRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "You are not enrolled in the benevolence program."));
        String mid = memberId(user);
        return replenPaymentRepo.findByEnrollmentOrderByCreatedAtDesc(enrollment)
                .stream()
                .map(rp -> ReplenishmentPaymentDto.from(rp, mid))
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BenevolenceReplenishmentDto toReplenishmentDto(BenevolenceReplenishment r) {
        List<ReplenishmentPayment> payments = replenPaymentRepo.findByReplenishment(r);
        long paidCount = replenPaymentRepo.countByReplenishmentAndStatus(r, ReplenishmentPaymentStatus.PAID);
        List<ReplenishmentPaymentDto> paymentDtos = payments.stream()
                .map(p -> ReplenishmentPaymentDto.from(p, memberId(p.getEnrollment().getUser())))
                .toList();
        return BenevolenceReplenishmentDto.from(r, paymentDtos, paidCount);
    }

    private String generateRef() {
        return "BEN-" + System.currentTimeMillis();
    }

    private String memberId(User user) {
        return profileRepo.findByUser(user)
                .map(MemberProfile::getMemberId)
                .orElse(null);
    }

    private BenevolenceClaim findClaimById(UUID id) {
        return claimRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
