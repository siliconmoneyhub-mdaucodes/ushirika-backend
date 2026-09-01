package com.mdau.ushirika.module.program.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.program.dto.ApplyToProgramRequest;
import com.mdau.ushirika.module.program.dto.DecideProgramApplicationRequest;
import com.mdau.ushirika.module.program.dto.MyProgramDto;
import com.mdau.ushirika.module.program.dto.ProgramApplicationDto;
import com.mdau.ushirika.module.benevolence.service.BenevolenceEnrollmentService;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.entity.ProgramApplication;
import com.mdau.ushirika.module.program.enums.ProgramApplicationStatus;
import com.mdau.ushirika.module.program.enums.ProgramStatus;
import com.mdau.ushirika.module.program.enums.ProgramType;
import com.mdau.ushirika.module.program.repository.ProgramAdminAssignmentRepository;
import com.mdau.ushirika.module.program.repository.ProgramApplicationRepository;
import com.mdau.ushirika.module.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ProgramApplicationService {

    private final ProgramApplicationRepository applicationRepository;
    private final ProgramRepository programRepository;
    private final ProgramAdminAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final BenevolenceEnrollmentService benevolenceEnrollmentService;
    private final AuditLogService auditLogService;

    private static final List<ProgramApplicationStatus> COORDINATOR_VISIBLE_STATUSES =
            List.of(ProgramApplicationStatus.PENDING_REVIEW, ProgramApplicationStatus.APPROVED, ProgramApplicationStatus.REJECTED);

    /** Mirrors BenevolenceEnrollmentService's $600 total — kept here too since prepayment
     * happens before any enrollment record exists to check against. */
    private static final BigDecimal BENEVOLENCE_ENROLLMENT_TOTAL = new BigDecimal("600.00");

    // ── Member (portal) ──────────────────────────────────────────────────────

    /** Verified members apply directly — no membership-approval wait, unlike onboarding-time
     * applications, so this goes straight to PENDING_REVIEW. */
    @Transactional
    public ProgramApplicationDto applyAsMember(UUID programId, ApplyToProgramRequest req) {
        User member = currentUser();
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Program not found: " + programId));
        if (program.getType() == ProgramType.MGR) {
            throw new BadRequestException("MGR has its own dedicated join flow — use that instead.");
        }
        if (program.getStatus() != ProgramStatus.ACTIVE) {
            throw new BadRequestException("This program is not currently open for applications.");
        }
        if (applicationRepository.existsByProgramAndApplicant(program, member)) {
            throw new ConflictException("You have already applied to " + program.getName());
        }
        ProgramApplication application = applicationRepository.save(ProgramApplication.builder()
                .program(program)
                .applicant(member)
                .status(ProgramApplicationStatus.PENDING_REVIEW)
                .appliedAt(LocalDateTime.now())
                .beneficiaries(req.beneficiaries() != null ? req.beneficiaries() : List.of())
                .notes(req.notes())
                .build());
        return ProgramApplicationDto.from(application);
    }

    /** Every ACTIVE program, plus any non-active program this member already has a stake in
     * (e.g. CLOSED — existing members stay enrolled even after new applications close). */
    @Transactional(readOnly = true)
    public List<MyProgramDto> listProgramsForMember() {
        User member = currentUser();
        List<ProgramApplication> myApplications = applicationRepository.findAllByApplicant(member);
        Map<UUID, ProgramApplication> myApplicationByProgramId = myApplications.stream()
                .collect(Collectors.toMap(a -> a.getProgram().getId(), Function.identity()));

        List<Program> active = programRepository.findAllByStatus(ProgramStatus.ACTIVE);
        List<Program> myOtherPrograms = myApplications.stream()
                .map(ProgramApplication::getProgram)
                .filter(p -> p.getStatus() != ProgramStatus.ACTIVE)
                .toList();

        return Stream.concat(active.stream(), myOtherPrograms.stream())
                .map(p -> MyProgramDto.from(p, myApplicationByProgramId.get(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgramApplicationDto getMyApplicationForProgram(UUID programId) {
        User member = currentUser();
        ProgramApplication application = applicationRepository.findByProgramIdAndApplicantId(programId, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("You have not applied to this program."));
        return ProgramApplicationDto.from(application);
    }

    /** Called before building an onboarding checkout basket — throws if this applicant can't
     * prepay this amount toward this application (wrong owner, wrong program type, already
     * decided, or would exceed the $600 Benevolence total). */
    @Transactional(readOnly = true)
    public void validatePrepayable(UUID applicationId, User applicant, BigDecimal amount) {
        ProgramApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Program application not found: " + applicationId));
        if (!app.getApplicant().getId().equals(applicant.getId())) {
            throw new ForbiddenException("This application does not belong to you.");
        }
        if (app.getProgram().getType() != ProgramType.BENEVOLENCE) {
            throw new BadRequestException("Prepayment during onboarding is currently only supported for Benevolence.");
        }
        if (app.getStatus() == ProgramApplicationStatus.APPROVED || app.getStatus() == ProgramApplicationStatus.REJECTED) {
            throw new BadRequestException("This application has already been decided.");
        }
        BigDecimal existing = app.getPrepaidAmount() != null ? app.getPrepaidAmount() : BigDecimal.ZERO;
        if (existing.add(amount).compareTo(BENEVOLENCE_ENROLLMENT_TOTAL) > 0) {
            throw new BadRequestException("That would exceed the $600 Benevolence enrollment total.");
        }
    }

    /** Credits a confirmed Stripe payment-basket line toward this application's prepaidAmount.
     * Called from the webhook — no-ops rather than throwing if the application no longer
     * belongs to this member or has already been decided (defense in depth; startCheckout
     * already validated ownership via validatePrepayable at basket-creation time). */
    @Transactional
    public void applyPrepayment(UUID applicationId, User expectedApplicant, BigDecimal amount) {
        ProgramApplication app = applicationRepository.findById(applicationId).orElse(null);
        if (app == null || !app.getApplicant().getId().equals(expectedApplicant.getId())) {
            return;
        }
        BigDecimal existing = app.getPrepaidAmount() != null ? app.getPrepaidAmount() : BigDecimal.ZERO;
        app.setPrepaidAmount(existing.add(amount));
        applicationRepository.save(app);
    }

    // ── Called by MembershipService.approveMembership() ─────────────────────

    @Transactional
    public void makeApplicationsVisibleToCoordinators(User approvedMember) {
        List<ProgramApplication> pending = applicationRepository.findAllByApplicantId(approvedMember.getId()).stream()
                .filter(a -> a.getStatus() == ProgramApplicationStatus.PENDING_MEMBERSHIP)
                .toList();
        pending.forEach(a -> a.setStatus(ProgramApplicationStatus.PENDING_REVIEW));
        applicationRepository.saveAll(pending);
    }

    // ── Program coordinator ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProgramApplicationDto> listApplicationsForProgram(UUID programId) {
        requireCoordinatorAccess(programId);
        return applicationRepository.findAllByProgramIdAndStatusIn(programId, COORDINATOR_VISIBLE_STATUSES).stream()
                .map(ProgramApplicationDto::from)
                .toList();
    }

    /** Roster for coordinator-initiated notifications/messages — everyone actually enrolled. */
    @Transactional(readOnly = true)
    public List<User> listApprovedMembersForProgram(UUID programId) {
        requireCoordinatorAccess(programId);
        return applicationRepository.findAllByProgramIdAndStatusIn(programId, List.of(ProgramApplicationStatus.APPROVED)).stream()
                .map(ProgramApplication::getApplicant)
                .toList();
    }

    /** Confirms the given member has applied to this program (any status) before a coordinator
     * messages/notifies them directly — prevents a coordinator reaching members outside their program. */
    @Transactional(readOnly = true)
    public User requireProgramApplicant(UUID programId, UUID memberId) {
        requireCoordinatorAccess(programId);
        return applicationRepository.findByProgramIdAndApplicantId(programId, memberId)
                .orElseThrow(() -> new ForbiddenException("This member has not applied to this program."))
                .getApplicant();
    }

    @Transactional
    public ProgramApplicationDto decide(UUID applicationId, DecideProgramApplicationRequest req) {
        ProgramApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Program application not found"));

        requireCoordinatorDecisionAccess(application.getProgram().getId());

        if (application.getStatus() != ProgramApplicationStatus.PENDING_REVIEW) {
            throw new BadRequestException("Only applications pending review can be decided. Current status: " + application.getStatus());
        }

        User reviewer = currentUser();
        boolean approved = req.decision() == DecideProgramApplicationRequest.Decision.APPROVE;
        application.setStatus(approved ? ProgramApplicationStatus.APPROVED : ProgramApplicationStatus.REJECTED);
        application.setReviewedAt(LocalDateTime.now());
        application.setReviewedBy(reviewer);
        if (!approved) {
            application.setRejectionReason(req.reason());
        }
        ProgramApplication saved = applicationRepository.save(application);

        auditLogService.log(reviewer, "PROGRAM_APPLICATION_" + saved.getStatus().name(),
                "ProgramApplication", saved.getId(),
                "Application by " + saved.getApplicant().getFullName() + " to \""
                        + saved.getProgram().getName() + "\" was " + saved.getStatus().name().toLowerCase());

        if (approved && saved.getProgram().getType() == ProgramType.BENEVOLENCE) {
            benevolenceEnrollmentService.ensureEnrolled(saved.getApplicant());
            if (saved.getPrepaidAmount() != null && saved.getPrepaidAmount().signum() > 0) {
                benevolenceEnrollmentService.applyPayment(saved.getApplicant(), saved.getPrepaidAmount());
            }
        }

        return ProgramApplicationDto.from(saved);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Viewing an application's queue: coordinator, or admin/superadmin for oversight. */
    private void requireCoordinatorAccess(UUID programId) {
        User me = currentUser();
        boolean isAssignedCoordinator = assignmentRepository.existsByProgramIdAndUserId(programId, me.getId());
        boolean isGlobalAdmin = me.getRole() == UserRole.ADMIN || me.getRole() == UserRole.SUPERADMIN;
        if (!isAssignedCoordinator && !isGlobalAdmin) {
            throw new ForbiddenException("You do not coordinate this program");
        }
    }

    /**
     * Deciding an application: the assigned program coordinator only. Deliberately excludes
     * the plain ADMIN role — the client requires that only the program's own coordinator can
     * approve membership on a program, not a general administrator. SUPERADMIN retains a
     * break-glass override since they're the one who provisions coordinators in the first place.
     */
    private void requireCoordinatorDecisionAccess(UUID programId) {
        User me = currentUser();
        boolean isAssignedCoordinator = assignmentRepository.existsByProgramIdAndUserId(programId, me.getId());
        if (!isAssignedCoordinator && me.getRole() != UserRole.SUPERADMIN) {
            throw new ForbiddenException("Only this program's coordinator can decide applications.");
        }
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
