package com.mdau.ushirika.module.program.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.program.dto.*;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.entity.ProgramAdminAssignment;
import com.mdau.ushirika.module.program.enums.ProgramStatus;
import com.mdau.ushirika.module.program.repository.ProgramAdminAssignmentRepository;
import com.mdau.ushirika.module.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgramService {

    private final ProgramRepository programRepository;
    private final ProgramAdminAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    // ── Public ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PublicProgramDto> listActivePrograms() {
        return programRepository.findAllByStatus(ProgramStatus.ACTIVE).stream()
                .map(PublicProgramDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicProgramDto getActiveProgramBySlug(String slug) {
        Program program = programRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Program not found"));
        if (program.getStatus() != ProgramStatus.ACTIVE) {
            throw new ResourceNotFoundException("Program not found");
        }
        return PublicProgramDto.from(program);
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProgramDto> listAllPrograms() {
        return programRepository.findAllByOrderByNameAsc().stream()
                .map(p -> ProgramDto.from(p, adminsFor(p.getId())))
                .toList();
    }

    @Transactional
    public ProgramDto createProgram(CreateProgramRequest req) {
        String slug = generateUniqueSlug(req.name());
        Program program = Program.builder()
                .name(req.name())
                .slug(slug)
                .shortDescription(req.shortDescription())
                .type(req.type())
                .status(ProgramStatus.DRAFT)
                .createdByUser(currentUser())
                .build();
        program = programRepository.save(program);
        return ProgramDto.from(program, List.of());
    }

    @Transactional
    public ProgramDto updateBasicInfo(UUID programId, UpdateProgramBasicInfoRequest req) {
        Program program = findProgram(programId);
        program.setName(req.name());
        program.setShortDescription(req.shortDescription());
        programRepository.save(program);
        return ProgramDto.from(program, adminsFor(programId));
    }

    @Transactional
    public ProgramDto updateStatus(UUID programId, UpdateProgramStatusRequest req) {
        Program program = findProgram(programId);
        program.setStatus(req.status());
        programRepository.save(program);
        return ProgramDto.from(program, adminsFor(programId));
    }

    @Transactional
    public ProgramAdminDto assignAdmin(UUID programId, AssignProgramAdminRequest req) {
        Program program = findProgram(programId);
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (assignmentRepository.existsByProgramIdAndUserId(programId, user.getId())) {
            throw new ConflictException("This user already administers this program");
        }

        ProgramAdminAssignment assignment = ProgramAdminAssignment.builder()
                .program(program)
                .user(user)
                .assignedBy(currentUser())
                .build();
        return ProgramAdminDto.from(assignmentRepository.save(assignment));
    }

    @Transactional
    public void removeAdmin(UUID programId, UUID userId) {
        if (!assignmentRepository.existsByProgramIdAndUserId(programId, userId)) {
            throw new ResourceNotFoundException("This user does not administer this program");
        }
        assignmentRepository.deleteByProgramIdAndUserId(programId, userId);
    }

    // ── Program-admin-scoped (assigned admin OR global admin/superadmin) ──────

    @Transactional(readOnly = true)
    public List<ProgramDto> listProgramsIAdminister() {
        User me = currentUser();
        return assignmentRepository.findAllByUserId(me.getId()).stream()
                .map(ProgramAdminAssignment::getProgram)
                .map(p -> ProgramDto.from(p, adminsFor(p.getId())))
                .toList();
    }

    /** Callable by the assigned program admin OR a global ADMIN/SUPERADMIN — role check happens at the controller. */
    @Transactional
    public ProgramDto updateDetailsAsProgramAdmin(UUID programId, UpdateProgramDetailsRequest req) {
        Program program = findProgram(programId);
        User me = currentUser();

        boolean isAssignedAdmin = assignmentRepository.existsByProgramIdAndUserId(programId, me.getId());
        boolean isGlobalAdmin = me.getRole() == com.mdau.ushirika.module.auth.enums.UserRole.ADMIN
                || me.getRole() == com.mdau.ushirika.module.auth.enums.UserRole.SUPERADMIN;

        if (!isAssignedAdmin && !isGlobalAdmin) {
            throw new ForbiddenException("You do not administer this program");
        }

        program.setContributionAmount(req.contributionAmount());
        program.setContributionFrequency(req.contributionFrequency());
        program.setRules(req.rules());
        program.setBenefits(req.benefits() != null ? req.benefits() : List.of());
        program.setMaxBeneficiaries(req.maxBeneficiaries());
        programRepository.save(program);
        return ProgramDto.from(program, adminsFor(programId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Program findProgram(UUID id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Program not found"));
    }

    private List<ProgramAdminDto> adminsFor(UUID programId) {
        return assignmentRepository.findAllByProgramId(programId).stream()
                .map(ProgramAdminDto::from)
                .collect(Collectors.toList());
    }

    private String generateUniqueSlug(String name) {
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
        if (base.isBlank()) {
            throw new BadRequestException("Could not generate a URL slug from this name");
        }
        String slug = base;
        int suffix = 2;
        while (programRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
