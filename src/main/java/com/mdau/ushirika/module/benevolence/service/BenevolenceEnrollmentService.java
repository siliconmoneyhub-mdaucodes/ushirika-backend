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
import com.mdau.ushirika.module.notification.service.NotificationCategory;
import com.mdau.ushirika.module.notification.service.NotificationDispatcher;
import com.mdau.ushirika.module.payment.service.PlatformSettingsService;
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
    private static final BigDecimal MIN_FIRST_PAYMENT = new BigDecimal("100.00");
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
    private final NotificationDispatcher notificationDispatcher;
    private final ProgramRepository programRepo;
    private final ProgramAdminAssignmentRepository programAdminAssignmentRepo;
    private final PlatformSettingsService platformSettingsService;

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

    // ── Admin: Seed a pre-launch enrollment ──────────────────────────────────
    // For the ~60 members who were already active Benevolence participants before this platform
    // existed. Deliberately bypasses requestJoin()/sendForm() entirely -- those always start a
    // member at PAYING with zero beneficiaries, but these members' real-world status (probation,
    // eligible, partially paid) and beneficiaries are already established facts to record, not
    // something to re-derive through the normal application flow. beneficiariesLocked is set
    // true at creation so there is never a window where the member could self-submit or change
    // who they listed -- the one hard requirement this phase exists for.

    private static final Set<EnrollmentStatus> SEEDABLE_STATUSES =
            Set.of(EnrollmentStatus.PAYING, EnrollmentStatus.PROBATION, EnrollmentStatus.ELIGIBLE);

    @Transactional
    public BenevolenceEnrollmentDto seedEnrollment(SeedBenevolenceEnrollmentRequest req) {
        User admin = currentUser();
        User member = userRepo.findByEmail(req.memberEmail().trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("No member found with email: " + req.memberEmail()));

        if (enrollmentRepo.findByUser(member).isPresent()) {
            throw new ConflictException(member.getFullName() + " is already enrolled in Benevolence.");
        }
        if (!SEEDABLE_STATUSES.contains(req.status())) {
            throw new BadRequestException("Status must be PAYING, PROBATION, or ELIGIBLE.");
        }
        if (req.status() != EnrollmentStatus.PAYING && req.amountPaid().compareTo(ENROLLMENT_TOTAL) < 0) {
            throw new BadRequestException("PROBATION/ELIGIBLE status requires the $600 enrollment fee to already be fully paid.");
        }
        if (req.status() == EnrollmentStatus.PROBATION && req.probationEndsAt() == null) {
            throw new BadRequestException("Probation end date is required when seeding a PROBATION enrollment.");
        }
        if (req.beneficiaries().size() > MAX_BENEFICIARIES) {
            throw new BadRequestException("Maximum of " + MAX_BENEFICIARIES + " beneficiaries allowed.");
        }

        BigDecimal cappedPaid = req.amountPaid().min(ENROLLMENT_TOTAL);
        boolean fullyPaid = cappedPaid.compareTo(ENROLLMENT_TOTAL) >= 0;

        BenevolenceEnrollment enrollment = BenevolenceEnrollment.builder()
                .user(member)
                .enrolledAt(LocalDateTime.now())
                .totalPaid(cappedPaid)
                .status(req.status())
                .beneficiariesLocked(true)
                .completedAt(fullyPaid ? LocalDateTime.now() : null)
                .probationEndsAt(req.probationEndsAt())
                .build();
        enrollmentRepo.save(enrollment);

        if (cappedPaid.signum() > 0) {
            paymentRepo.save(EnrollmentPayment.builder()
                    .enrollment(enrollment)
                    .amount(cappedPaid)
                    .paymentMethod("LEGACY")
                    .paidAt(LocalDateTime.now())
                    .notes("Pre-launch balance seeded by " + admin.getFullName())
                    .build());
        }

        for (SubmitBeneficiariesRequest.BeneficiaryEntry entry : req.beneficiaries()) {
            beneficiaryRepo.save(BenevolenceBeneficiary.builder()
                    .enrollment(enrollment)
                    .firstName(entry.firstName())
                    .lastName(entry.lastName())
                    .relationship(entry.relationship())
                    .phoneNumber(entry.phoneNumber())
                    .dateOfBirth(entry.dateOfBirth())
                    .build());
        }

        auditLogService.log(admin, "BENEVOLENCE_ENROLLMENT_SEEDED", "BenevolenceEnrollment", enrollment.getId(),
                "Seeded Benevolence enrollment for " + member.getFullName() + " (" + req.beneficiaries().size()
                        + " beneficiaries, status=" + req.status() + ", $" + cappedPaid + " paid) by " + admin.getFullName());

        sendEnrolledEmail(member);

        return toFullDto(enrollment);
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
        // A CANCELLED enrollment (join request rejected after the form was already sent) reads
        // the same as never having enrolled -- the member's own join-request status (REJECTED)
        // is what portal/benevolence.tsx actually renders instead.
        if (e.getStatus() == EnrollmentStatus.CANCELLED) {
            throw new ResourceNotFoundException("You have not been enrolled in the benevolence program yet.");
        }
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
        notifyCoordinatorsOfJoinRequest(member);

        log.info("Benevolence join request submitted: member={}", member.getEmail());
        return BenevolenceJoinRequestDto.from(request, memberId(member));
    }

    /** No admin/coordinator was ever emailed when a join request came in -- it only ever showed
     *  up passively in the Needs Your Attention feed for whoever happened to be assigned and
     *  happened to check. Scoped to the actually-assigned Benevolence coordinator(s), not a
     *  broadcast to every admin, matching who's allowed to act on it (requireBenevolenceCoordinatorAccess). */
    private void notifyCoordinatorsOfJoinRequest(User member) {
        programRepo.findAllByType(ProgramType.BENEVOLENCE).stream()
                .flatMap(p -> programAdminAssignmentRepo.findAllByProgramId(p.getId()).stream())
                .map(ProgramAdminAssignment::getUser)
                .distinct()
                .forEach(coordinator -> emailService.sendPlain(
                        coordinator.getEmail(), coordinator.getFullName(),
                        "New Benevolence Join Request — Action Required",
                        "Hello " + coordinator.getFirstName() + ",\n\n" +
                        "A member has requested to join the Benevolence program.\n\n" +
                        "Member: " + member.getFullName() + "\n\n" +
                        "Review it here: " + siteUrl + "/admin/benevolence\n\n" +
                        "Ushirika Welfare Organization"
                ));
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

        // FORM_SENT means sendForm() already opened a live enrollment via ensureEnrolled() --
        // without this, a rejected applicant kept an active, payable enrollment with no
        // member-visible sign anything was rejected. See getMyEnrollment()/applyPayment() above
        // for how CANCELLED is then treated as "not enrolled."
        boolean hadOpenEnrollment = request.getStatus() == BenevolenceJoinRequestStatus.FORM_SENT;

        request.setStatus(BenevolenceJoinRequestStatus.REJECTED);
        request.setAdminNotes(adminNotes);
        request.setRespondedBy(admin);
        request.setRespondedAt(LocalDateTime.now());
        joinRequestRepo.save(request);

        String paidNote = "";
        if (hadOpenEnrollment) {
            BenevolenceEnrollment enrollment = enrollmentRepo.findByUser(request.getUser()).orElse(null);
            if (enrollment != null) {
                enrollment.setStatus(EnrollmentStatus.CANCELLED);
                enrollmentRepo.save(enrollment);
                if (enrollment.getTotalPaid() != null && enrollment.getTotalPaid().signum() > 0) {
                    paidNote = " -- had already paid $" + enrollment.getTotalPaid()
                            + " toward enrollment; needs a manual refund follow-up.";
                }
            }
        }

        log.info("Benevolence join request rejected: id={} member={} by={}",
                requestId, request.getUser().getEmail(), admin.getEmail());
        auditLogService.log(admin, "BENEVOLENCE_JOIN_REJECTED", "BenevolenceJoinRequest", request.getId(),
                "Benevolence membership rejected for " + request.getUser().getFullName() + " by " + admin.getFullName() + paidNote);
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
        int probationMonths = platformSettingsService.getBenevolenceProbationMonths();
        String html = """
                <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
                  <h2 style="color:#007834">Your Benevolence Application is Ready to Complete</h2>
                  <p>Hi %s,</p>
                  <p>A program coordinator has reviewed your request and it's time to finish your
                     application. In your member portal, you'll:</p>
                  <ol style="line-height:1.7">
                    <li>List up to 6 beneficiaries who could receive the death benefit</li>
                    <li>Pay at least <strong>$100</strong> toward the $600 enrollment fee to submit —
                        or pay the full $600 now if you'd rather be done with it</li>
                  </ol>
                  <p>As soon as that payment goes through, you're enrolled — no further review needed
                     on our end. If you pay in installments, the remaining balance is tracked in your
                     portal until it's cleared; once it is, a %d-month probation period begins, after
                     which you're eligible to file a claim.</p>
                  <p>
                    <a href="%s/portal/benevolence"
                       style="display:inline-block;background:#007834;color:#fff;padding:10px 22px;
                              border-radius:999px;text-decoration:none;font-weight:600">
                      Complete My Application &rarr;
                    </a>
                  </p>
                </div>
                """.formatted(user.getFirstName(), probationMonths, siteUrl);
        try {
            emailService.sendPlain(user.getEmail(), user.getFullName(),
                    "Your Ushirika Benevolence Application is Ready to Complete", html);
        } catch (Exception e) {
            log.warn("Benevolence form-sent email failed for {}: {}", user.getEmail(), e.getMessage());
        }

        notificationDispatcher.dispatchWhatsApp(NotificationCategory.APPLICATION_READY,
                user.getPhone(), user.getFullName(), List.of(
                        user.getFullName(),
                        "Benevolence",
                        siteUrl + "/portal/benevolence"
                ));
    }

    private void sendEnrolledEmail(User user) {
        int probationMonths = platformSettingsService.getBenevolenceProbationMonths();
        String html = """
                <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
                  <h2 style="color:#007834">You're Enrolled in the Benevolence Program</h2>
                  <p>Hi %s,</p>
                  <p>Your payment went through and your Benevolence application is approved — you're
                     in. Your beneficiaries are on file, and your enrollment fee balance (if any is
                     left) is tracked in your portal until it's fully paid.</p>
                  <p>Once your $600 fee is fully paid, a %d-month probation period begins. After that,
                     you're eligible to file a death benefit claim of up to $5,000 for a covered
                     beneficiary.</p>
                  <p>
                    <a href="%s/portal/benevolence"
                       style="display:inline-block;background:#007834;color:#fff;padding:10px 22px;
                              border-radius:999px;text-decoration:none;font-weight:600">
                      View My Enrollment &rarr;
                    </a>
                  </p>
                </div>
                """.formatted(user.getFirstName(), probationMonths, siteUrl);
        try {
            emailService.sendPlain(user.getEmail(), user.getFullName(),
                    "You're Enrolled — Ushirika Benevolence Program", html);
        } catch (Exception e) {
            log.warn("Benevolence enrolled email failed for {}: {}", user.getEmail(), e.getMessage());
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

    /** Confirms this member currently has a FORM_SENT join request open to pay against — the gate
     * that makes the "pay to submit" flow meaningful. A member with no open application, or one
     * already decided (approved/rejected), can't just show up and pay. */
    @Transactional(readOnly = true)
    public void assertApplicationPayable(User user) {
        BenevolenceJoinRequest request = joinRequestRepo.findFirstByUserOrderByCreatedAtDesc(user).orElse(null);
        if (request == null || request.getStatus() != BenevolenceJoinRequestStatus.FORM_SENT) {
            throw new BadRequestException("You don't have an open Benevolence application to pay for right now.");
        }
    }

    /** Enforces the $100 minimum on a member's very first Benevolence enrollment payment — the
     * "pay to submit your application" gate. Later installment payments have no minimum. */
    @Transactional(readOnly = true)
    public void validateFirstPayment(User user, BigDecimal amountUsd) {
        BenevolenceEnrollment enrollment = enrollmentRepo.findByUser(user).orElse(null);
        boolean isFirstPayment = enrollment == null || enrollment.getTotalPaid() == null
                || enrollment.getTotalPaid().signum() == 0;
        if (isFirstPayment && amountUsd.compareTo(MIN_FIRST_PAYMENT) < 0) {
            throw new BadRequestException("Your first Benevolence payment must be at least $"
                    + MIN_FIRST_PAYMENT + " (or pay the full $" + ENROLLMENT_TOTAL + " now).");
        }
    }

    /** Credits a confirmed Stripe payment-basket line toward this member's enrollment fee.
     * Mirrors recordEnrollmentPayment but no-ops instead of throwing if already paid in full —
     * this runs from the webhook, not a live user request. A member's first payment of any size
     * also auto-approves their still-FORM_SENT join request — paying is what puts them "in,"
     * not a separate manual admin click. */
    @Transactional
    public void applyPayment(User user, BigDecimal amountUsd) {
        BenevolenceEnrollment enrollment = enrollmentRepo.findByUser(user)
                .orElseGet(() -> createEnrollment(user));

        if (enrollment.getStatus() == EnrollmentStatus.ELIGIBLE || enrollment.getStatus() == EnrollmentStatus.PROBATION
                || enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
            return;
        }

        boolean wasFirstPayment = enrollment.getTotalPaid() == null || enrollment.getTotalPaid().signum() == 0;

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
            enrollment.setProbationEndsAt(LocalDate.now().plusMonths(platformSettingsService.getBenevolenceProbationMonths()));
            enrollment.setStatus(EnrollmentStatus.PROBATION);
        } else {
            enrollment.setTotalPaid(newTotal);
        }
        enrollmentRepo.save(enrollment);

        if (wasFirstPayment) {
            autoApproveOnFirstPayment(user);
        }
    }

    /** Paying is what puts a member "in" — no separate manual admin approval click needed once
     * the coordinator has already sent the form. Only fires for a request still awaiting the
     * member's action; a request the coordinator already decided is left alone. */
    private void autoApproveOnFirstPayment(User user) {
        joinRequestRepo.findFirstByUserOrderByCreatedAtDesc(user)
                .filter(r -> r.getStatus() == BenevolenceJoinRequestStatus.FORM_SENT)
                .ifPresent(request -> {
                    request.setStatus(BenevolenceJoinRequestStatus.APPROVED);
                    request.setRespondedAt(LocalDateTime.now());
                    joinRequestRepo.save(request);

                    log.info("Benevolence join request auto-approved on first payment: id={} member={}",
                            request.getId(), user.getEmail());
                    auditLogService.log(user, "BENEVOLENCE_JOIN_APPROVED", "BenevolenceJoinRequest", request.getId(),
                            user.getFullName() + "'s Benevolence application was automatically approved after their enrollment payment.");
                    sendEnrolledEmail(user);
                });
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
