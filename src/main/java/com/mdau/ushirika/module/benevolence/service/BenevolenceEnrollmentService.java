package com.mdau.ushirika.module.benevolence.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.benevolence.dto.*;
import com.mdau.ushirika.module.benevolence.entity.*;
import com.mdau.ushirika.module.benevolence.enums.EnrollmentStatus;
import com.mdau.ushirika.module.benevolence.repository.*;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BenevolenceEnrollmentService {

    private static final BigDecimal ENROLLMENT_TOTAL = new BigDecimal("600.00");
    private static final int MAX_BENEFICIARIES = 6;

    private final BenevolenceEnrollmentRepository enrollmentRepo;
    private final EnrollmentPaymentRepository paymentRepo;
    private final BenevolenceBeneficiaryRepository beneficiaryRepo;
    private final MemberProfileRepository profileRepo;
    private final UserRepository userRepo;
    private final AuditLogService auditLogService;

    // ── Admin: List Enrollments ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<BenevolenceEnrollmentDto> listEnrollments(EnrollmentStatus status, Pageable pageable) {
        Page<BenevolenceEnrollment> page = status != null
                ? enrollmentRepo.findAllByStatusOrderByEnrolledAtDesc(status, pageable)
                : enrollmentRepo.findAllByOrderByEnrolledAtDesc(pageable);
        return PagedResponse.of(page.map(e -> {
            int count = beneficiaryRepo.countByEnrollment(e);
            return BenevolenceEnrollmentDto.summary(e, memberId(e.getUser()), count);
        }));
    }

    @Transactional(readOnly = true)
    public BenevolenceEnrollmentDto getEnrollmentById(UUID id) {
        return toFullDto(findById(id));
    }

    @Transactional(readOnly = true)
    public BenevolenceEnrollmentDto getEnrollmentByUser(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        BenevolenceEnrollment e = enrollmentRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("No benevolence enrollment for this user."));
        return toFullDto(e);
    }

    // ── Admin: Beneficiary management ────────────────────────────────────────

    @Transactional
    public BenevolenceBeneficiaryDto addBeneficiary(UUID enrollmentId,
                                                      SubmitBeneficiariesRequest.BeneficiaryEntry entry) {
        BenevolenceEnrollment enrollment = findById(enrollmentId);
        if (enrollment.isBeneficiariesLocked()) {
            throw new BadRequestException("Beneficiaries are locked for this enrollment.");
        }
        if (beneficiaryRepo.countByEnrollment(enrollment) >= MAX_BENEFICIARIES) {
            throw new BadRequestException("Maximum of " + MAX_BENEFICIARIES + " beneficiaries allowed.");
        }
        BenevolenceBeneficiary b = BenevolenceBeneficiary.builder()
                .enrollment(enrollment)
                .firstName(entry.firstName())
                .lastName(entry.lastName())
                .relationship(entry.relationship())
                .phoneNumber(entry.phoneNumber())
                .dateOfBirth(entry.dateOfBirth())
                .build();
        BenevolenceBeneficiary saved = beneficiaryRepo.save(b);
        User admin = currentUser();
        auditLogService.log(admin, "BENEFICIARY_ADDED", "BenevolenceBeneficiary", saved.getId(),
                "Beneficiary " + saved.getFirstName() + " " + saved.getLastName() + " added for "
                        + enrollment.getUser().getFullName() + " by " + admin.getFullName());
        return BenevolenceBeneficiaryDto.from(saved);
    }

    @Transactional
    public void lockBeneficiaries(UUID enrollmentId) {
        BenevolenceEnrollment enrollment = findById(enrollmentId);
        if (beneficiaryRepo.countByEnrollment(enrollment) == 0) {
            throw new BadRequestException("Cannot lock — no beneficiaries added yet.");
        }
        enrollment.setBeneficiariesLocked(true);
        enrollmentRepo.save(enrollment);
        User admin = currentUser();
        auditLogService.log(admin, "BENEFICIARIES_LOCKED", "BenevolenceEnrollment", enrollment.getId(),
                "Beneficiaries locked for " + enrollment.getUser().getFullName() + " by " + admin.getFullName());
    }

    @Transactional
    public void markBeneficiaryDeceased(UUID beneficiaryId, String adminNotes) {
        BenevolenceBeneficiary b = beneficiaryRepo.findById(beneficiaryId)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found: " + beneficiaryId));
        b.setDeceased(true);
        b.setDeceasedAt(LocalDateTime.now());
        b.setAdminNotes(adminNotes);
        beneficiaryRepo.save(b);
        User admin = currentUser();
        auditLogService.log(admin, "BENEFICIARY_MARKED_DECEASED", "BenevolenceBeneficiary", b.getId(),
                "Beneficiary " + b.getFirstName() + " " + b.getLastName() + " marked deceased by "
                        + admin.getFullName());
    }

    // ── Member: Self-service ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BenevolenceEnrollmentDto getMyEnrollment() {
        User user = currentUser();
        BenevolenceEnrollment e = enrollmentRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "You have not been enrolled in the benevolence program yet."));
        return toFullDto(e);
    }

    @Transactional
    public BenevolenceEnrollmentDto submitMyBeneficiaries(SubmitBeneficiariesRequest req) {
        User user = currentUser();
        BenevolenceEnrollment enrollment = enrollmentRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "You have not been enrolled in the benevolence program yet."));

        if (enrollment.isBeneficiariesLocked()) {
            throw new BadRequestException("Your beneficiaries have already been submitted and locked.");
        }
        List<BenevolenceBeneficiary> existing = beneficiaryRepo.findByEnrollment(enrollment);
        if (!existing.isEmpty()) {
            throw new BadRequestException("Beneficiaries already submitted. Contact admin to update.");
        }
        if (req.beneficiaries().size() > MAX_BENEFICIARIES) {
            throw new BadRequestException("Maximum of " + MAX_BENEFICIARIES + " beneficiaries allowed.");
        }

        for (SubmitBeneficiariesRequest.BeneficiaryEntry entry : req.beneficiaries()) {
            BenevolenceBeneficiary b = BenevolenceBeneficiary.builder()
                    .enrollment(enrollment)
                    .firstName(entry.firstName())
                    .lastName(entry.lastName())
                    .relationship(entry.relationship())
                    .phoneNumber(entry.phoneNumber())
                    .dateOfBirth(entry.dateOfBirth())
                    .build();
            beneficiaryRepo.save(b);
        }

        enrollment.setBeneficiariesLocked(true);
        enrollmentRepo.save(enrollment);
        auditLogService.log(user, "BENEFICIARIES_SUBMITTED", "BenevolenceEnrollment", enrollment.getId(),
                req.beneficiaries().size() + " beneficiary(ies) submitted by " + user.getFullName());
        return toFullDto(enrollment);
    }

    /** Called when a coordinator approves a member's ProgramApplication for Benevolence — creates
     * the enrollment record so the member can immediately submit beneficiaries and start paying. */
    @Transactional
    public void ensureEnrolled(User user) {
        enrollmentRepo.findByUser(user).orElseGet(() -> createEnrollment(user));
    }

    /** Null if not enrolled. Otherwise the remaining balance (0 once PROBATION/ELIGIBLE) + current status name. */
    @Transactional(readOnly = true)
    public EnrollmentBalance outstandingBalance(User user) {
        return enrollmentRepo.findByUser(user).map(e -> {
            BigDecimal remaining = e.getStatus() == EnrollmentStatus.PAYING
                    ? ENROLLMENT_TOTAL.subtract(e.getTotalPaid() != null ? e.getTotalPaid() : BigDecimal.ZERO).max(BigDecimal.ZERO)
                    : BigDecimal.ZERO;
            return new EnrollmentBalance(remaining, e.getStatus().name());
        }).orElse(null);
    }

    public record EnrollmentBalance(BigDecimal balance, String status) {}

    /** Credits a confirmed Stripe payment-basket line toward this member's enrollment fee.
     * Mirrors recordEnrollmentPayment but no-ops instead of throwing if already paid in full —
     * this runs from the webhook, not a live user request. */
    @Transactional
    public void applyPayment(User user, BigDecimal amountUsd) {
        BenevolenceEnrollment enrollment = enrollmentRepo.findByUser(user)
                .orElseGet(() -> createEnrollment(user));

        if (enrollment.getStatus() == EnrollmentStatus.ELIGIBLE || enrollment.getStatus() == EnrollmentStatus.PROBATION) {
            return;
        }

        EnrollmentPayment payment = EnrollmentPayment.builder()
                .enrollment(enrollment)
                .amount(amountUsd)
                .paymentMethod("STRIPE")
                .paidAt(LocalDateTime.now())
                .build();
        paymentRepo.save(payment);

        BigDecimal newTotal = enrollment.getTotalPaid().add(amountUsd);
        if (newTotal.compareTo(ENROLLMENT_TOTAL) >= 0) {
            enrollment.setTotalPaid(ENROLLMENT_TOTAL);
            enrollment.setCompletedAt(LocalDateTime.now());
            enrollment.setProbationEndsAt(LocalDate.now().plusMonths(6));
            enrollment.setStatus(EnrollmentStatus.PROBATION);
        } else {
            enrollment.setTotalPaid(newTotal);
        }
        enrollmentRepo.save(enrollment);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private BenevolenceEnrollment createEnrollment(User user) {
        BenevolenceEnrollment e = BenevolenceEnrollment.builder()
                .user(user)
                .enrolledAt(LocalDateTime.now())
                .build();
        return enrollmentRepo.save(e);
    }

    private BenevolenceEnrollmentDto toFullDto(BenevolenceEnrollment e) {
        List<EnrollmentPaymentDto> payments = paymentRepo
                .findByEnrollmentOrderByPaidAtDesc(e).stream()
                .map(EnrollmentPaymentDto::from)
                .toList();
        List<BenevolenceBeneficiaryDto> beneficiaries = beneficiaryRepo
                .findByEnrollment(e).stream()
                .map(BenevolenceBeneficiaryDto::from)
                .toList();
        return BenevolenceEnrollmentDto.from(e, memberId(e.getUser()), payments, beneficiaries);
    }

    private String memberId(User user) {
        return profileRepo.findByUser(user)
                .map(MemberProfile::getMemberId)
                .orElse(null);
    }

    private BenevolenceEnrollment findById(UUID id) {
        return enrollmentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
