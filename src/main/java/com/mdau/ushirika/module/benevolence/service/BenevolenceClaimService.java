package com.mdau.ushirika.module.benevolence.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.common.util.TextNormalizer;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.benevolence.dto.*;
import com.mdau.ushirika.module.benevolence.entity.*;
import com.mdau.ushirika.module.benevolence.enums.*;
import com.mdau.ushirika.module.benevolence.repository.*;
import com.mdau.ushirika.module.audit.enums.LedgerDirection;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.notification.enums.InAppNotificationCategory;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import com.mdau.ushirika.module.notification.service.NotificationCategory;
import com.mdau.ushirika.module.notification.service.NotificationDispatcher;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.entity.ProgramAdminAssignment;
import com.mdau.ushirika.module.program.enums.ProgramType;
import com.mdau.ushirika.module.program.repository.ProgramAdminAssignmentRepository;
import com.mdau.ushirika.module.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

@Slf4j
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
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final NotificationDispatcher notificationDispatcher;
    private final InAppNotificationService notificationService;
    private final ProgramRepository programRepo;
    private final ProgramAdminAssignmentRepository programAdminAssignmentRepo;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

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
                    : "You are still in your probation period, ending "
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
            if (category.isRequiresDocuments() && (req.documentUrls() == null || req.documentUrls().isEmpty())) {
                throw new BadRequestException("\"" + category.getName() + "\" claims require at least one supporting document.");
            }
        }

        BenevolenceClaim claim = BenevolenceClaim.builder()
                .enrollment(enrollment)
                .beneficiary(beneficiary)
                .category(category)
                .referenceNumber(generateRef())
                .deceasedName(TextNormalizer.titleCase(req.deceasedName()))
                .relationship(TextNormalizer.titleCase(req.relationship()))
                .dateOfDeath(req.dateOfDeath())
                .locationOfDeath(req.locationOfDeath())
                .funeralDate(req.funeralDate())
                .funeralLocation(req.funeralLocation())
                .contactName(TextNormalizer.titleCase(req.contactName()))
                .contactPhone(req.contactPhone())
                .description(req.description())
                .documentUrls(req.documentUrls() != null ? req.documentUrls() : List.of())
                .submittedAt(LocalDateTime.now())
                .build();
        claimRepo.save(claim);
        auditLogService.log(user, "CLAIM_SUBMITTED", "BenevolenceClaim", claim.getId(),
                "Benevolence claim " + claim.getReferenceNumber() + " submitted by " + user.getFullName()
                        + " for " + req.deceasedName());
        notifyClaimUpdate(claim, "has been received and is now under review",
                "Your Benevolence Claim Was Received — " + claim.getReferenceNumber());
        notifyCoordinatorsOfClaim(claim, user);
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
        User admin = currentUser();
        requireBenevolenceCoordinatorAccess(admin);
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
        auditLogService.log(admin, "CLAIM_" + claim.getStatus().name(), "BenevolenceClaim", claim.getId(),
                "Benevolence claim " + claim.getReferenceNumber() + " for "
                        + claim.getEnrollment().getUser().getFullName() + " marked " + claim.getStatus()
                        + " by " + admin.getFullName());

        switch (decision) {
            case "APPROVE" -> notifyClaimUpdate(claim, "has been approved for $" + claim.getAmountApproved(),
                    "Your Benevolence Claim Was Approved — " + claim.getReferenceNumber());
            case "REJECT" -> notifyClaimUpdate(claim,
                    "was not approved" + (req.rejectionReason() != null ? " (" + req.rejectionReason() + ")" : ""),
                    "Update on Your Benevolence Claim — " + claim.getReferenceNumber());
            default -> { /* UNDER_REVIEW is a transitional internal state -- no member notification. */ }
        }

        return BenevolenceClaimDto.from(claim, memberId(claim.getEnrollment().getUser()));
    }

    @Transactional
    public BenevolenceClaimDto authorizeDisbursement(UUID id) {
        User admin = currentUser();
        requireBenevolenceCoordinatorAccess(admin);
        BenevolenceClaim claim = findClaimById(id);
        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new BadRequestException("Claim must be APPROVED before authorizing disbursement.");
        }
        claim.setStatus(ClaimStatus.PAYMENT_AUTHORIZED);
        claimRepo.save(claim);
        auditLogService.log(admin, "CLAIM_DISBURSEMENT_AUTHORIZED", "BenevolenceClaim", claim.getId(),
                "Disbursement of $" + claim.getAmountApproved() + " authorized for claim "
                        + claim.getReferenceNumber() + " by " + admin.getFullName());
        return BenevolenceClaimDto.from(claim, memberId(claim.getEnrollment().getUser()));
    }

    @Transactional
    public BenevolenceClaimDto markDisbursed(UUID id) {
        User admin = currentUser();
        requireBenevolenceCoordinatorAccess(admin);
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

        auditLogService.log(admin, "CLAIM_DISBURSED", "BENEVOLENCE_CLAIM", claim.getId(),
                "Claim " + claim.getReferenceNumber() + " ($" + claim.getAmountApproved()
                        + ") marked disbursed by " + admin.getFullName(),
                claim.getAmountApproved(), LedgerDirection.OUT);
        notifyClaimUpdate(claim, "of $" + claim.getAmountApproved() + " has been disbursed",
                "Your Benevolence Claim Payment Has Been Sent — " + claim.getReferenceNumber());
        return BenevolenceClaimDto.from(claim, memberId(claim.getEnrollment().getUser()));
    }

    /** Notifies the member of a status change on their claim via email + WhatsApp + in-app --
     * previously this service sent no notification of any kind, so this is new infrastructure,
     * not a channel bolted onto an existing send. statusPhrase completes "Your claim {ref} ...",
     * e.g. "has been approved for $500" or "was not approved (reason)". */
    private void notifyClaimUpdate(BenevolenceClaim claim, String statusPhrase, String subject) {
        User user = claim.getEnrollment().getUser();
        String portalUrl = siteUrl + "/portal/benevolence";
        String html = """
                <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
                  <h2 style="color:#007834">Benevolence Claim Update</h2>
                  <p>Hi %s,</p>
                  <p>Your claim <strong>%s</strong> %s.</p>
                  <p>
                    <a href="%s" style="display:inline-block;background:#007834;color:#fff;
                       padding:10px 22px;border-radius:999px;text-decoration:none;font-weight:600">
                      View My Claim &rarr;
                    </a>
                  </p>
                  <p>— Ushirika Welfare Organization Team</p>
                </div>
                """.formatted(user.getFirstName(), claim.getReferenceNumber(), statusPhrase, portalUrl);

        try {
            emailService.sendPlain(user.getEmail(), user.getFullName(), subject, html);
        } catch (Exception e) {
            log.warn("Benevolence claim update email failed for {}: {}", user.getEmail(), e.getMessage());
        }

        notificationDispatcher.dispatchWhatsApp(NotificationCategory.WELFARE_CLAIM_UPDATE,
                user.getPhone(), user.getFullName(), List.of(
                        user.getFullName(),
                        claim.getReferenceNumber(),
                        statusPhrase,
                        portalUrl
                ));

        try {
            notificationService.createForUser(
                    user.getId(),
                    InAppNotificationCategory.WELFARE_CLAIM,
                    subject,
                    "Your claim " + claim.getReferenceNumber() + " " + statusPhrase + ".",
                    "/portal/benevolence"
            );
        } catch (Exception e) {
            log.warn("Benevolence claim update in-app notification failed for {}: {}", user.getEmail(), e.getMessage());
        }
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
            if (replenishmentRepo.existsByClaim(claim)) {
                throw new BadRequestException("A replenishment has already been created for this claim.");
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

        User admin = currentUser();
        auditLogService.log(admin, "REPLENISHMENT_CREATED", "BenevolenceReplenishment", replenishment.getId(),
                "Replenishment of $" + total + " ($" + perMember + " each) created for " + count
                        + " eligible member(s) by " + admin.getFullName());
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
        User admin = currentUser();
        auditLogService.log(admin, "REPLENISHMENT_PAYMENT_WAIVED", "ReplenishmentPayment", rp.getId(),
                "Replenishment payment of $" + rp.getAmountDue() + " for "
                        + rp.getEnrollment().getUser().getFullName() + " waived by " + admin.getFullName()
                        + (reason != null ? " (" + reason + ")" : ""));
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

    /**
     * Deciding a claim (review/authorize disbursement/mark disbursed) is a Benevolence program
     * decision — only that program's assigned coordinator (or SUPERADMIN) can do this, mirroring
     * BenevolenceEnrollmentService.requireBenevolenceCoordinatorAccess() exactly. The
     * /admin/benevolence/** path is open to any ADMIN at the security-filter level, so this
     * service-level check is what actually enforces the restriction.
     */
    private void requireBenevolenceCoordinatorAccess(User user) {
        if (user.getRole() == UserRole.SUPERADMIN) {
            return;
        }
        List<Program> benevolencePrograms = programRepo.findAllByType(ProgramType.BENEVOLENCE);
        boolean isAssignedCoordinator = benevolencePrograms.stream()
                .anyMatch(p -> programAdminAssignmentRepo.existsByProgramIdAndUserId(p.getId(), user.getId()));
        if (!isAssignedCoordinator) {
            throw new ForbiddenException("Only the Benevolence program's coordinator can decide claims.");
        }
    }

    /** No admin/coordinator was ever emailed when a claim came in -- it only ever showed up
     *  passively in the Needs Your Attention feed. Scoped to the actually-assigned coordinator(s),
     *  same set that requireBenevolenceCoordinatorAccess above would let act on this claim. */
    private void notifyCoordinatorsOfClaim(BenevolenceClaim claim, User submittedBy) {
        programRepo.findAllByType(ProgramType.BENEVOLENCE).stream()
                .flatMap(p -> programAdminAssignmentRepo.findAllByProgramId(p.getId()).stream())
                .map(ProgramAdminAssignment::getUser)
                .distinct()
                .forEach(coordinator -> emailService.sendPlain(
                        coordinator.getEmail(), coordinator.getFullName(),
                        "New Benevolence Claim — " + claim.getReferenceNumber(),
                        "<p>Hello " + coordinator.getFirstName() + ",</p>" +
                        "<p>A Benevolence claim was just submitted and needs review.</p>" +
                        "<p><strong>Submitted by:</strong> " + submittedBy.getFullName() + "<br>" +
                        "<strong>Reference:</strong> " + claim.getReferenceNumber() + "<br>" +
                        "<strong>For:</strong> " + claim.getDeceasedName() + "</p>" +
                        "<p style=\"margin:24px 0\"><a href=\"" + siteUrl + "/admin/benevolence\" " +
                        "style=\"display:inline-block;background:#007834;color:#fff;padding:10px 20px;" +
                        "border-radius:24px;text-decoration:none;font-weight:600\">Review Claim</a></p>" +
                        "<p>Ushirika Welfare Organization</p>"
                ));
    }

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
