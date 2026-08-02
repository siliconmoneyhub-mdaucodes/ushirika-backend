package com.mdau.ushirika.module.mgr.service;

import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.attendance.repository.FineRepository;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.mgr.repository.*;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.enums.ProgramType;
import com.mdau.ushirika.module.program.repository.ProgramAdminAssignmentRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The client's requirement extends to MGR join requests too: only the MGR program's
 * assigned coordinator can approve/reject — a plain ADMIN hitting /admin/mgr/** must be
 * rejected at the service level even though the path-level security lets them through. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MgrServiceCoordinatorAccessTest {

    @Mock private MgrCycleRepository cycleRepo;
    @Mock private MgrSlotRepository slotRepo;
    @Mock private MgrContributionRepository contributionRepo;
    @Mock private MgrJoinRequestRepository joinRequestRepo;
    @Mock private MemberProfileRepository profileRepo;
    @Mock private UserRepository userRepo;
    @Mock private EmailService emailService;
    @Mock private ProgramRepository programRepo;
    @Mock private ProgramAdminAssignmentRepository programAdminAssignmentRepo;

    private MgrService service;
    private UUID mgrProgramId;

    @BeforeEach
    void setUp() {
        service = new MgrService(cycleRepo, slotRepo, contributionRepo, joinRequestRepo,
                profileRepo, userRepo, emailService, programRepo, programAdminAssignmentRepo);

        Program mgrProgram = Program.builder().name("MGR").type(ProgramType.MGR).build();
        mgrProgramId = UUID.randomUUID();
        mgrProgram.setId(mgrProgramId);
        when(programRepo.findAllByType(ProgramType.MGR)).thenReturn(List.of(mgrProgram));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(User user) {
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));
    }

    @Test
    void plainAdmin_notCoordinator_cannotApproveJoinRequest() {
        User admin = User.builder().email("admin@test.ushirika.org").role(UserRole.ADMIN).build();
        admin.setId(UUID.randomUUID());
        loginAs(admin);
        when(programAdminAssignmentRepo.existsByProgramIdAndUserId(mgrProgramId, admin.getId())).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> service.approveJoinRequest(UUID.randomUUID(), null));
        // The guard must run BEFORE the request is even looked up.
        verifyNoInteractions(joinRequestRepo);
    }

    @Test
    void plainAdmin_notCoordinator_cannotRejectJoinRequest() {
        User admin = User.builder().email("admin2@test.ushirika.org").role(UserRole.ADMIN).build();
        admin.setId(UUID.randomUUID());
        loginAs(admin);
        when(programAdminAssignmentRepo.existsByProgramIdAndUserId(mgrProgramId, admin.getId())).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> service.rejectJoinRequest(UUID.randomUUID(), null));
        verifyNoInteractions(joinRequestRepo);
    }

    @Test
    void assignedCoordinator_passesGuard_reachesLookup() {
        User coordinator = User.builder().email("coordinator@test.ushirika.org").role(UserRole.MEMBER).build();
        coordinator.setId(UUID.randomUUID());
        loginAs(coordinator);
        when(programAdminAssignmentRepo.existsByProgramIdAndUserId(mgrProgramId, coordinator.getId())).thenReturn(true);
        UUID requestId = UUID.randomUUID();
        when(joinRequestRepo.findById(requestId)).thenReturn(Optional.empty());

        // Guard passes; fails downstream on "not found" instead of "forbidden" — proves the
        // coordinator check let it through rather than the lookup being skipped.
        assertThrows(ResourceNotFoundException.class, () -> service.approveJoinRequest(requestId, null));
    }

    @Test
    void superadmin_breakGlass_passesGuardWithoutAssignment() {
        User superadmin = User.builder().email("super@test.ushirika.org").role(UserRole.SUPERADMIN).build();
        superadmin.setId(UUID.randomUUID());
        loginAs(superadmin);
        when(programAdminAssignmentRepo.existsByProgramIdAndUserId(mgrProgramId, superadmin.getId())).thenReturn(false);
        UUID requestId = UUID.randomUUID();
        when(joinRequestRepo.findById(requestId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.approveJoinRequest(requestId, null));
    }
}
