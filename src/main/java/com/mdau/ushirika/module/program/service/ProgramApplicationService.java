package com.mdau.ushirika.module.program.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.program.dto.ApplyToProgramsRequest;
import com.mdau.ushirika.module.program.dto.DecideProgramApplicationRequest;
import com.mdau.ushirika.module.program.dto.ProgramApplicationDto;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.entity.ProgramApplication;
import com.mdau.ushirika.module.program.enums.ProgramApplicationStatus;
import com.mdau.ushirika.module.program.repository.ProgramAdminAssignmentRepository;
import com.mdau.ushirika.module.program.repository.ProgramApplicationRepository;
import com.mdau.ushirika.module.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProgramApplicationService {

    private final ProgramApplicationRepository applicationRepository;
    private final ProgramRepository programRepository;
    private final ProgramAdminAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    private static final List<ProgramApplicationStatus> COORDINATOR_VISIBLE_STATUSES =
            List.of(ProgramApplicationStatus.PENDING_REVIEW, ProgramApplicationStatus.APPROVED, ProgramApplicationStatus.REJECTED);

    // ── Applicant (onboarding) ──────────────────────────────────────────────

    @Transactional
    public List<ProgramApplicationDto> applyToPrograms(ApplyToProgramsRequest req) {
        User applicant = currentUser();
        List<ProgramApplication> created = req.programIds().stream()
                .distinct()
                .map(programId -> {
                    Program program = programRepository.findById(programId)
                            .orElseThrow(() -> new ResourceNotFoundException("Program not found: " + programId));
                    if (applicationRepository.existsByProgramAndApplicant(program, applicant)) {
                        throw new ConflictException("You have already applied to " + program.getName());
                    }
                    return applicationRepository.save(ProgramApplication.builder()
                            .program(program)
                            .applicant(applicant)
                            .status(ProgramApplicationStatus.PENDING_MEMBERSHIP)
                            .appliedAt(LocalDateTime.now())
                            .build());
                })
                .toList();
        return created.stream().map(ProgramApplicationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ProgramApplicationDto> listMyApplications() {
        return applicationRepository.findAllByApplicant(currentUser()).stream()
                .map(ProgramApplicationDto::from)
                .toList();
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

    @Transactional
    public ProgramApplicationDto decide(UUID applicationId, DecideProgramApplicationRequest req) {
        ProgramApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Program application not found"));

        requireCoordinatorAccess(application.getProgram().getId());

        if (application.getStatus() != ProgramApplicationStatus.PENDING_REVIEW) {
            throw new BadRequestException("Only applications pending review can be decided. Current status: " + application.getStatus());
        }

        application.setStatus(req.decision() == DecideProgramApplicationRequest.Decision.APPROVE
                ? ProgramApplicationStatus.APPROVED : ProgramApplicationStatus.REJECTED);
        application.setReviewedAt(LocalDateTime.now());
        application.setReviewedBy(currentUser());
        if (req.decision() == DecideProgramApplicationRequest.Decision.REJECT) {
            application.setRejectionReason(req.reason());
        }
        return ProgramApplicationDto.from(applicationRepository.save(application));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void requireCoordinatorAccess(UUID programId) {
        User me = currentUser();
        boolean isAssignedCoordinator = assignmentRepository.existsByProgramIdAndUserId(programId, me.getId());
        boolean isGlobalAdmin = me.getRole() == UserRole.ADMIN || me.getRole() == UserRole.SUPERADMIN;
        if (!isAssignedCoordinator && !isGlobalAdmin) {
            throw new ForbiddenException("You do not coordinate this program");
        }
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
