package com.mdau.ushirika.module.benevolence.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.benevolence.dto.*;
import com.mdau.ushirika.module.benevolence.entity.*;
import com.mdau.ushirika.module.benevolence.enums.BenevolenceJoinRequestStatus;
import com.mdau.ushirika.module.benevolence.enums.EnrollmentStatus;
import com.mdau.ushirika.module.benevolence.repository.*;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.program.entity.Program;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BenevolenceEnrollmentService {

    private static final BigDecimal ENROLLMENT_TOTAL = new BigDecimal("600.00");
    private static final int MAX_BENEFICIARIES = 6;
    private static final Set<BenevolenceJoinRequestStatus> OPEN_JOIN_REQUEST_STATUSES =
            Set.of(BenevolenceJoinRequestStatus.PENDING, BenevolenceJoinRequestStatus.FORM_SENT);

    private final BenevolenceEnrollmentRepository enrollmentRepo;
    private final EnrollmentPaymentRepository paymentRepo;
    private final BenevolenceBeneficiaryRepository beneficiaryRepo;
    private final BenevolenceJoinRequestRepository joinRequestRepo;
    private final MemberProfileRepository profileRepo;
    private final UserRepository userRepo;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final ProgramRepository programRepo;
    private final ProgramAdminAssignmentRepository programAdminAssignmentRepo;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

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

    /** Creates the enrollment record if one doesn't already exist, so the member can immediately
     * submit beneficiaries and start paying. Called when a Benevolence coordinator sends the
     * join-request form (see sendForm() below). */
    @Transactional
    public void ensureEnrolled(User user) {
        enrollmentRepo.findByUser(user).orElseGet(() -> createEnrollment(user));
    }

    // ── Join Requests ─────────────────────────────────────────────────────────
    // Benevolence's own dedicated join flow, mirroring MgrService's join-request pattern rather
    // than the generic ProgramApplication system (which explicitly excludes Benevolence/MGR).
    // Flow: member requests -> coordinator sends the form (this also opens a real
    // BenevolenceEnrollment via ensureEnrolled(), so the member can submit beneficiaries and pay
    // through the enrollment machinery above completely unchanged) -> coordinator approves or
    // rejects once beneficiaries/payment look right.

    @Transactional
    public BenevolenceJoinRequestDto requestJoin(String memberNotes) {
        User member = currentUser();
        if (!member.isActive()) {
            throw new BadRequestException("Only active members may apply to join the Benevolence program.");
        }
        if (enrollmentRepo.findByUser(member).isPresent()) {
            throw new ConflictException("You are already enrolled in the Benevolence program.");
        }
        if (joinRequestRepo.existsByUserAndStatusIn(member, OPEN_JOIN_REQUEST_STATUSES)) {
            throw new ConflictException("You already have a pending Benevolence join request.");
        }

        BenevolenceJoinRequest request = BenevolenceJoinRequest.builder()
                .user(member)
                .memberNotes(memberNotes)
                .build();
        joinRequestRepo.save(request);

        log.info("Benevolence join request submitted: member={}", member.getEmail());
        return BenevolenceJoinRequestDto.from(request, memberId(member));
    }

    @Transactional(readOnly = true)
    public BenevolenceJoinRequestDto getMyJoinRequest() {
        User member = currentUser();
        return joinRequestRepo.findFirstByUserOrderByCreatedAtDesc(member)
                .map(r -> BenevolenceJoinRequestDto.from(r, memberId(member)))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<BenevolenceJoinRequestDto> listJoinRequests(BenevolenceJoinRequestStatus status) {
        List<BenevolenceJoinRequest> requests = status != null
                ? joinRequestRepo.findByStatusOrderByCreatedAtDesc(status)
                : joinRequestRepo.findAllByOrderByCreatedAtDesc();
        return requests.stream()
                .map(r -> BenevolenceJoinRequestDto.from(r, memberId(r.getUser())))
                .toList();
    }

    /** Coordinator sends the applicant the form — opens a real enrollment so they can submit
     * beneficiaries and pay via the same flow as everyone else. */
    @Transactional
    public BenevolenceJoinRequestDto sendForm(UUID requestId, String adminNotes) {
        User admin = currentUser();
        requireBenevolenceCoordinatorAccess(admin);
        BenevolenceJoinRequest request = findJoinRequest(requestId);

        if (request.getStatus() != BenevolenceJoinRequestStatus.PENDING) {
            throw new BadRequestException("Only PENDING requests can have the form sent.");
        }

        ensureEnrolled(request.getUser());

        request.setStatus(BenevolenceJoinRequestStatus.FORM_SENT);
        request.setAdminNotes(adminNotes);
        request.setFormSentBy(admin);
        request.setFormSentAt(LocalDateTime.now());
        joinRequestRepo.save(request);

        log.info("Benevolence join-request form sent: id={} member={} by={}",
                requestId, request.getUser().getEmail(), admin.getEmail());
        auditLogService.log(admin, "BENEVOLENCE_JOIN_FORM_SENT", "BenevolenceJoinRequest", request.getId(),
                "Benevolence enrollment form sent to " + request.getUser().getFullName() + " by " + admin.getFullName());
        sendFormEmail(request);
        return BenevolenceJoinRequestDto.from(request, memberId(request.getUser()));
    }

    @Transactional
    public BenevolenceJoinRequestDto approveJoinRequest(UUID requestId, String adminNotes) {
        User admin = currentUser();
        requireBenevolenceCoordinatorAccess(admin);
        BenevolenceJoinRequest request = findJoinRequest(requestId);

        if (request.getStatus() != BenevolenceJoinRequestStatus.FORM_SENT) {
            throw new BadRequestException("Only requests with the form already sent can be approved.");
        }

        request.setStatus(BenevolenceJoinRequestStatus.APPROVED);
        request.setAdminNotes(adminNotes);
        request.setRespondedBy(admin);
        request.setRespondedAt(LocalDateTime.now());
        joinRequestRepo.save(request);

        log.info("Benevolence join request approved: id={} member={} by={}",
                requestId, request.getUser().getEmail(), admin.getEmail());
        auditLogService.log(admin, "BENEVOLENCE_JOIN_APPROVED", "BenevolenceJoinRequest", request.getId(),
                "Benevolence membership approved for " + request.getUser().getFullName() + " by " + admin.getFullName());
        return BenevolenceJoinRequestDto.from(request, memberId(request.getUser()));
    }

    @Transactional
    public BenevolenceJoinRequestDto rejectJoinRequest(UUID requestId, String adminNotes) {
        User admin = currentUser();
        requireBenevolenceCoordinatorAccess(admin);
        BenevolenceJoinRequest request = findJoinRequest(requestId);

        if (request.getStatus() != BenevolenceJoinRequestStatus.PENDING
                && request.getStatus() != BenevolenceJoinRequestStatus.FORM_SENT) {
            throw new BadRequestException("Only PENDING or FORM_SENT requests can be rejected.");
        }

        request.setStatus(BenevolenceJoinRequestStatus.REJECTED);
        request.setAdminNotes(adminNotes);
        request.setRespondedBy(admin);
        request.setRespondedAt(LocalDateTime.now());
        joinRequestRepo.save(request);

        log.info("Benevolence join request rejected: id={} member={} by={}",
                requestId, request.getUser().getEmail(), admin.getEmail());
        auditLogService.log(admin, "BENEVOLENCE_JOIN_REJECTED", "BenevolenceJoinRequest", request.getId(),
                "Benevolence membership rejected for " + request.getUser().getFullName() + " by " + admin.getFullName());
        return BenevolenceJoinRequestDto.from(request, memberId(request.getUser()));
    }

    /**
     * Approving/rejecting/sending the form for a join request is deciding Benevolence program
     * membership — only that program's assigned coordinator (or SUPERADMIN) can do this, not a
     * general ADMIN, mirroring MgrService.requireMgrCoordinatorAccess() exactly. The
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
            throw new ForbiddenException("Only the Benevolence program's coordinator can decide join requests.");
        }
    }

    private BenevolenceJoinRequest findJoinRequest(UUID id) {
        return joinRequestRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Benevolence join request not found: " + id));
    }

    private void sendFormEmail(BenevolenceJoinRequest request) {
        User user = request.getUser();
        String html = """
                <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
                  <h2 style="color:#007834">Your Benevolence Enrollment Form is Ready</h2>
                  <p>Hi %s,</p>
                  <p>A program coordinator has reviewed your request to join the Benevolence program.
                     Log in to your member portal to submit your beneficiaries (up to 6) and pay the
                     $600 enrollment fee — installments are accepted.</p>
                  <p>
                    <a href="%s/portal/benevolence"
                       style="display:inline-block;background:#007834;color:#fff;padding:10px 22px;
                              border-radius:999px;text-decoration:none;font-weight:600">
                      Continue Enrollment &rarr;
                    </a>
                  </p>
                </div>
                """.formatted(user.getFirstName(), siteUrl);
        try {
            emailService.sendPlain(user.getEmail(), user.getFullName(),
                    "Your Ushirika Benevolence Enrollment Form is Ready", html);
        } catch (Exception e) {
            log.warn("Benevolence form-sent email failed for {}: {}", user.getEmail(), e.getMessage());
        }
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
