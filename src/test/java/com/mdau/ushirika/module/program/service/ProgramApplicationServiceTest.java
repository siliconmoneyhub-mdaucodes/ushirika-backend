package com.mdau.ushirika.module.program.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.benevolence.service.BenevolenceEnrollmentService;
import com.mdau.ushirika.module.program.dto.DecideProgramApplicationRequest;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.entity.ProgramApplication;
import com.mdau.ushirika.module.program.enums.ProgramApplicationStatus;
import com.mdau.ushirika.module.program.enums.ProgramType;
import com.mdau.ushirika.module.program.repository.ProgramAdminAssignmentRepository;
import com.mdau.ushirika.module.program.repository.ProgramApplicationRepository;
import com.mdau.ushirika.module.program.repository.ProgramRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The client's explicit requirement: only the assigned program coordinator can approve
 * membership on a program — not even a general ADMIN. SUPERADMIN keeps a break-glass override. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgramApplicationServiceTest {

    @Mock private ProgramApplicationRepository applicationRepository;
    @Mock private ProgramRepository programRepository;
    @Mock private ProgramAdminAssignmentRepository assignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private BenevolenceEnrollmentService benevolenceEnrollmentService;
    @Mock private AuditLogService auditLogService;

    private ProgramApplicationService service;
    private UUID programId;
    private Program benevolenceProgram;

    @BeforeEach
    void setUp() {
        service = new ProgramApplicationService(
                applicationRepository, programRepository, assignmentRepository,
                userRepository, benevolenceEnrollmentService, auditLogService);

        programId = UUID.randomUUID();
        benevolenceProgram = Program.builder().name("Benevolence").type(ProgramType.BENEVOLENCE).build();
        benevolenceProgram.setId(programId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(User user) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));
    }

    private ProgramApplication pendingApplication(BigDecimal prepaid) {
        User applicant = User.builder().email("applicant@test.ushirika.org").role(UserRole.MEMBER).build();
        applicant.setId(UUID.randomUUID());
        ProgramApplication app = ProgramApplication.builder()
                .program(benevolenceProgram)
                .applicant(applicant)
                .status(ProgramApplicationStatus.PENDING_REVIEW)
                .appliedAt(LocalDateTime.now())
                .prepaidAmount(prepaid)
                .build();
        app.setId(UUID.randomUUID());
        when(applicationRepository.findById(app.getId())).thenReturn(Optional.of(app));
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return app;
    }

    @Test
    void plainAdmin_cannotDecideApplication() {
        User admin = User.builder().email("admin@test.ushirika.org").role(UserRole.ADMIN).build();
        admin.setId(UUID.randomUUID());
        loginAs(admin);
        when(assignmentRepository.existsByProgramIdAndUserId(programId, admin.getId())).thenReturn(false);
        ProgramApplication app = pendingApplication(BigDecimal.ZERO);

        assertThrows(ForbiddenException.class, () ->
                service.decide(app.getId(), new DecideProgramApplicationRequest(DecideProgramApplicationRequest.Decision.APPROVE, null)));
        verifyNoInteractions(benevolenceEnrollmentService);
    }

    @Test
    void assignedCoordinator_canApprove_andEnrollmentIsCreated() {
        User coordinator = User.builder().email("coordinator@test.ushirika.org").role(UserRole.MEMBER).build();
        coordinator.setId(UUID.randomUUID());
        loginAs(coordinator);
        when(assignmentRepository.existsByProgramIdAndUserId(programId, coordinator.getId())).thenReturn(true);
        ProgramApplication app = pendingApplication(new BigDecimal("150.00"));

        var result = service.decide(app.getId(),
                new DecideProgramApplicationRequest(DecideProgramApplicationRequest.Decision.APPROVE, null));

        assertEquals(ProgramApplicationStatus.APPROVED, result.status());
        verify(benevolenceEnrollmentService).ensureEnrolled(app.getApplicant());
        verify(benevolenceEnrollmentService).applyPayment(app.getApplicant(), new BigDecimal("150.00"));
    }

    @Test
    void approvalWithNoPrepaidAmount_doesNotCallApplyPayment() {
        User coordinator = User.builder().email("coordinator2@test.ushirika.org").role(UserRole.MEMBER).build();
        coordinator.setId(UUID.randomUUID());
        loginAs(coordinator);
        when(assignmentRepository.existsByProgramIdAndUserId(programId, coordinator.getId())).thenReturn(true);
        ProgramApplication app = pendingApplication(BigDecimal.ZERO);

        service.decide(app.getId(), new DecideProgramApplicationRequest(DecideProgramApplicationRequest.Decision.APPROVE, null));

        verify(benevolenceEnrollmentService).ensureEnrolled(app.getApplicant());
        verify(benevolenceEnrollmentService, never()).applyPayment(any(), any());
    }

    @Test
    void superadmin_breakGlass_canApproveWithoutAssignment() {
        User superadmin = User.builder().email("super@test.ushirika.org").role(UserRole.SUPERADMIN).build();
        superadmin.setId(UUID.randomUUID());
        loginAs(superadmin);
        when(assignmentRepository.existsByProgramIdAndUserId(programId, superadmin.getId())).thenReturn(false);
        ProgramApplication app = pendingApplication(BigDecimal.ZERO);

        var result = service.decide(app.getId(),
                new DecideProgramApplicationRequest(DecideProgramApplicationRequest.Decision.APPROVE, null));

        assertEquals(ProgramApplicationStatus.APPROVED, result.status());
    }

    @Test
    void rejection_recordsReasonAndDoesNotTouchEnrollment() {
        User coordinator = User.builder().email("coordinator3@test.ushirika.org").role(UserRole.MEMBER).build();
        coordinator.setId(UUID.randomUUID());
        loginAs(coordinator);
        when(assignmentRepository.existsByProgramIdAndUserId(programId, coordinator.getId())).thenReturn(true);
        ProgramApplication app = pendingApplication(BigDecimal.ZERO);

        var result = service.decide(app.getId(),
                new DecideProgramApplicationRequest(DecideProgramApplicationRequest.Decision.REJECT, "Incomplete beneficiary info"));

        assertEquals(ProgramApplicationStatus.REJECTED, result.status());
        assertEquals("Incomplete beneficiary info", result.rejectionReason());
        verifyNoInteractions(benevolenceEnrollmentService);
    }

    @Test
    void alreadyDecidedApplication_cannotBeDecidedAgain() {
        User coordinator = User.builder().email("coordinator4@test.ushirika.org").role(UserRole.MEMBER).build();
        coordinator.setId(UUID.randomUUID());
        loginAs(coordinator);
        when(assignmentRepository.existsByProgramIdAndUserId(programId, coordinator.getId())).thenReturn(true);
        ProgramApplication app = pendingApplication(BigDecimal.ZERO);
        app.setStatus(ProgramApplicationStatus.APPROVED);

        assertThrows(BadRequestException.class, () -> service.decide(app.getId(),
                new DecideProgramApplicationRequest(DecideProgramApplicationRequest.Decision.APPROVE, null)));
    }
}
