package com.mdau.ushirika.module.scholarship.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.common.service.QuorumApprovalService;
import com.mdau.ushirika.common.service.QuorumApprovalService.QuorumResult;
import com.mdau.ushirika.module.audit.enums.LedgerDirection;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.enums.ApprovalDecision;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.scholarship.dto.*;
import com.mdau.ushirika.module.scholarship.entity.*;
import com.mdau.ushirika.module.scholarship.enums.ScholarshipApplicationStatus;
import com.mdau.ushirika.module.scholarship.enums.ScholarshipProgramStatus;
import com.mdau.ushirika.module.scholarship.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScholarshipService {

    private final ScholarshipProgramRepository programRepository;
    private final ScholarshipApplicationRepository applicationRepository;
    private final ScholarshipApprovalRepository approvalRepository;
    private final ScholarshipAwardRepository awardRepository;
    private final PublicScholarshipInquiryRepository inquiryRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final QuorumApprovalService quorumApprovalService;
    private final AuditLogService auditLogService;

    // ─────────────────────────────────────── Programs

    @Transactional(readOnly = true)
    public List<ScholarshipProgramDto> listOpenPrograms() {
        return programRepository
                .findAllByStatusOrderByApplicationDeadlineAsc(ScholarshipProgramStatus.OPEN)
                .stream().map(ScholarshipProgramDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<ScholarshipProgramDto> listAllPrograms(Pageable pageable) {
        return PagedResponse.of(programRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(ScholarshipProgramDto::from));
    }

    @Transactional
    public ScholarshipProgramDto createProgram(ScholarshipProgramRequest req) {
        ScholarshipProgram program = ScholarshipProgram.builder()
                .name(req.name())
                .description(req.description())
                .eligibilityCriteria(req.eligibilityCriteria())
                .amountPerRecipient(req.amountPerRecipient())
                .totalSlots(req.totalSlots())
                .academicYear(req.academicYear())
                .applicationDeadline(req.applicationDeadline())
                .build();
        ScholarshipProgram saved = programRepository.save(program);
        User admin = currentUser();
        auditLogService.log(admin, "SCHOLARSHIP_PROGRAM_CREATED", "ScholarshipProgram", saved.getId(),
                "Scholarship program '" + saved.getName() + "' created by " + admin.getFullName());
        return ScholarshipProgramDto.from(saved);
    }

    @Transactional
    public ScholarshipProgramDto updateProgram(UUID id, ScholarshipProgramRequest req) {
        ScholarshipProgram program = findProgramById(id);
        program.setName(req.name());
        program.setDescription(req.description());
        program.setEligibilityCriteria(req.eligibilityCriteria());
        program.setAmountPerRecipient(req.amountPerRecipient());
        program.setTotalSlots(req.totalSlots());
        program.setAcademicYear(req.academicYear());
        program.setApplicationDeadline(req.applicationDeadline());
        ScholarshipProgram saved = programRepository.save(program);
        User admin = currentUser();
        auditLogService.log(admin, "SCHOLARSHIP_PROGRAM_UPDATED", "ScholarshipProgram", saved.getId(),
                "Scholarship program '" + saved.getName() + "' updated by " + admin.getFullName());
        return ScholarshipProgramDto.from(saved);
    }

    @Transactional
    public ScholarshipProgramDto updateProgramStatus(UUID id, ScholarshipProgramStatus newStatus) {
        ScholarshipProgram program = findProgramById(id);
        program.setStatus(newStatus);
        ScholarshipProgram saved = programRepository.save(program);
        User admin = currentUser();
        auditLogService.log(admin, "SCHOLARSHIP_PROGRAM_STATUS_CHANGED", "ScholarshipProgram", saved.getId(),
                "Scholarship program '" + saved.getName() + "' status changed to " + newStatus
                        + " by " + admin.getFullName());
        return ScholarshipProgramDto.from(saved);
    }

    // ─────────────────────────────────────── Public inquiry (low-priority)

    @Transactional
    public void submitInquiry(PublicInquiryRequest req) {
        ScholarshipProgram program = null;
        if (req.programId() != null) {
            program = programRepository.findById(req.programId()).orElse(null);
        }

        PublicScholarshipInquiry inquiry = PublicScholarshipInquiry.builder()
                .fullName(req.fullName())
                .email(req.email())
                .phone(req.phone())
                .message(req.message())
                .program(program)
                .build();
        inquiryRepository.save(inquiry);

        // Acknowledge the inquiry
        emailService.sendPlain(
                req.email(), req.fullName(),
                "Scholarship Inquiry Received — Ushirika Welfare Organization",
                "Dear " + req.fullName() + ",\n\n" +
                "Thank you for your interest in the Ushirika Welfare Organization scholarship program. " +
                "We have received your inquiry and will get back to you shortly.\n\n" +
                "Regards,\nUshirika Welfare Organization"
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<PublicInquiryDto> listInquiries(Pageable pageable) {
        return PagedResponse.of(inquiryRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(PublicInquiryDto::from));
    }

    // ─────────────────────────────────────── Member — apply

    @Transactional
    public ScholarshipApplicationTrackDto apply(ScholarshipApplicationRequest req) {
        User member = currentUser();
        assertApprovedMember(member);

        ScholarshipProgram program = findProgramById(req.programId());

        if (program.getStatus() != ScholarshipProgramStatus.OPEN) {
            throw new BadRequestException("This scholarship program is not currently accepting applications.");
        }
        if (program.getApplicationDeadline() != null
                && LocalDate.now().isAfter(program.getApplicationDeadline())) {
            throw new BadRequestException("The application deadline for this program has passed.");
        }
        if (applicationRepository.existsByMemberAndProgram(member, program)) {
            throw new ConflictException("You have already applied for this scholarship program.");
        }
        if (program.getTotalSlots() != null) {
            long awarded = applicationRepository.countByProgramAndStatusIn(
                    program, List.of(ScholarshipApplicationStatus.APPROVED, ScholarshipApplicationStatus.AWARDED));
            if (awarded >= program.getTotalSlots()) {
                throw new BadRequestException("All slots for this program have been filled.");
            }
        }

        ScholarshipApplication application = ScholarshipApplication.builder()
                .member(member)
                .program(program)
                .referenceNumber(generateReference())
                .beneficiaryName(req.beneficiaryName())
                .institutionName(req.institutionName())
                .courseOfStudy(req.courseOfStudy())
                .academicYear(req.academicYear())
                .personalStatement(req.personalStatement())
                .documentUrls(req.documentUrls() != null ? req.documentUrls() : List.of())
                .build();

        ScholarshipApplication saved = applicationRepository.save(application);
        auditLogService.log(member, "SCHOLARSHIP_APPLICATION_CREATED", "ScholarshipApplication", saved.getId(),
                "Scholarship application " + saved.getReferenceNumber() + " for " + saved.getBeneficiaryName()
                        + " started by " + member.getFullName());
        return ScholarshipApplicationTrackDto.from(saved);
    }

    @Transactional
    public ScholarshipApplicationTrackDto submitApplication(UUID applicationId) {
        User member = currentUser();
        ScholarshipApplication application = findApplicationById(applicationId);

        if (!application.getMember().getId().equals(member.getId())) {
            throw new BadRequestException("Application not found.");
        }
        if (application.getStatus() != ScholarshipApplicationStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT applications can be submitted. Current: " + application.getStatus());
        }

        application.setStatus(ScholarshipApplicationStatus.SUBMITTED);
        application.setSubmittedAt(LocalDateTime.now());
        applicationRepository.save(application);

        auditLogService.log(member, "SCHOLARSHIP_APPLICATION_SUBMITTED", "ScholarshipApplication", application.getId(),
                "Scholarship application " + application.getReferenceNumber() + " submitted for board review by "
                        + member.getFullName());
        notifyAdmins(application);
        return ScholarshipApplicationTrackDto.from(application);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ScholarshipApplicationTrackDto> myApplications(Pageable pageable) {
        User member = currentUser();
        return PagedResponse.of(applicationRepository.findAllByMemberOrderByCreatedAtDesc(member, pageable)
                .map(ScholarshipApplicationTrackDto::from));
    }

    @Transactional(readOnly = true)
    public ScholarshipApplicationTrackDto myApplication(UUID id) {
        User member = currentUser();
        ScholarshipApplication app = findApplicationById(id);
        if (!app.getMember().getId().equals(member.getId())) {
            throw new BadRequestException("Application not found.");
        }
        return ScholarshipApplicationTrackDto.from(app);
    }

    // ─────────────────────────────────────── Admin — review

    @Transactional(readOnly = true)
    public PagedResponse<AdminScholarshipApplicationDto> listApplications(
            ScholarshipApplicationStatus status, Pageable pageable, boolean isSuperAdmin) {
        var page = status != null
                ? applicationRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
                : applicationRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PagedResponse.of(page.map(a -> AdminScholarshipApplicationDto.from(a, isSuperAdmin)));
    }

    @Transactional(readOnly = true)
    public AdminScholarshipApplicationDto getApplication(UUID id, boolean isSuperAdmin) {
        return AdminScholarshipApplicationDto.from(findApplicationById(id), isSuperAdmin);
    }

    @Transactional
    public AdminScholarshipApplicationDto review(UUID applicationId, ScholarshipReviewRequest req, boolean isSuperAdmin) {
        User admin = currentUser();
        ScholarshipApplication application = findApplicationById(applicationId);

        if (!List.of(ScholarshipApplicationStatus.SUBMITTED, ScholarshipApplicationStatus.UNDER_REVIEW)
                .contains(application.getStatus())) {
            throw new BadRequestException(
                    "Only SUBMITTED or UNDER_REVIEW applications can be reviewed. Current: " + application.getStatus());
        }
        if (approvalRepository.existsByApplicationAndAdmin(application, admin)) {
            throw new ConflictException("You have already cast your vote on this application.");
        }

        ScholarshipApproval approval = ScholarshipApproval.builder()
                .application(application)
                .admin(admin)
                .decision(req.decision())
                .comment(req.comment())
                .decidedAt(LocalDateTime.now())
                .build();
        approvalRepository.save(approval);
        application.getApprovals().add(approval);
        application.setStatus(ScholarshipApplicationStatus.UNDER_REVIEW);

        auditLogService.log(admin, "SCHOLARSHIP_VOTE_CAST", "ScholarshipApplication", application.getId(),
                admin.getFullName() + " voted " + req.decision() + " on application "
                        + application.getReferenceNumber());

        long approved = approvalRepository.countByApplicationAndDecision(application, ApprovalDecision.APPROVED);
        long rejected = approvalRepository.countByApplicationAndDecision(application, ApprovalDecision.REJECTED);

        QuorumResult result = quorumApprovalService.evaluate(approved, rejected);
        switch (result) {
            case REJECTED -> {
                applyRejection(application);
                auditLogService.log(admin, "SCHOLARSHIP_APPLICATION_REJECTED", "ScholarshipApplication", application.getId(),
                        "Scholarship application " + application.getReferenceNumber() + " rejected by board quorum");
            }
            case APPROVED -> {
                applyApproval(application);
                auditLogService.log(admin, "SCHOLARSHIP_APPLICATION_APPROVED", "ScholarshipApplication", application.getId(),
                        "Scholarship application " + application.getReferenceNumber() + " approved by board quorum");
            }
            case PENDING  -> {}
        }

        applicationRepository.save(application);
        return AdminScholarshipApplicationDto.from(application, isSuperAdmin);
    }

    // ─────────────────────────────────────── Superadmin — award

    @Transactional
    public AdminScholarshipApplicationDto recordAward(UUID applicationId, ScholarshipAwardRequest req) {
        User admin = currentUser();
        ScholarshipApplication application = findApplicationById(applicationId);

        if (application.getStatus() != ScholarshipApplicationStatus.APPROVED) {
            throw new BadRequestException(
                    "Only APPROVED applications can be awarded. Current: " + application.getStatus());
        }
        if (awardRepository.existsByApplication(application)) {
            throw new ConflictException("Award already recorded for this application.");
        }

        ScholarshipAward award = ScholarshipAward.builder()
                .application(application)
                .amountAwarded(req.amountAwarded())
                .method(req.method())
                .transactionReference(req.transactionReference())
                .awardedBy(admin)
                .awardedAt(LocalDateTime.now())
                .notes(req.notes())
                .build();
        awardRepository.save(award);
        application.setAward(award);
        application.setStatus(ScholarshipApplicationStatus.AWARDED);
        applicationRepository.save(application);

        auditLogService.log(admin, "SCHOLARSHIP_AWARDED", "SCHOLARSHIP", application.getId(),
                "Scholarship of " + req.amountAwarded() + " awarded to " + application.getBeneficiaryName()
                        + " (ref " + application.getReferenceNumber() + ") by " + admin.getFullName(),
                req.amountAwarded(), LedgerDirection.OUT);

        emailService.sendPlain(
                application.getMember().getEmail(),
                application.getMember().getFullName(),
                "Scholarship Award — Ushirika Welfare Organization",
                "Dear " + application.getMember().getFirstName() + ",\n\n" +
                "Congratulations! The scholarship award for " + application.getBeneficiaryName() +
                " (ref: " + application.getReferenceNumber() + ") of KES " + req.amountAwarded() +
                " has been disbursed via " + req.method().name().replace("_", " ") + ".\n\n" +
                (req.transactionReference() != null
                        ? "Transaction reference: " + req.transactionReference() + "\n\n"
                        : "") +
                "We wish " + application.getBeneficiaryName() + " success in their studies.\n\n" +
                "Warmly,\nUshirika Welfare Organization"
        );

        log.info("Scholarship awarded: ref={} beneficiary={} amount={}",
                application.getReferenceNumber(), application.getBeneficiaryName(), req.amountAwarded());
        return AdminScholarshipApplicationDto.from(application, true);
    }

    // ─────────────────────────────────────── Private

    private void applyRejection(ScholarshipApplication application) {
        application.setStatus(ScholarshipApplicationStatus.REJECTED);
        application.setRejectionReason("Your scholarship application was reviewed and not approved by the board.");
        application.setReviewedAt(LocalDateTime.now());

        emailService.sendPlain(
                application.getMember().getEmail(),
                application.getMember().getFullName(),
                "Scholarship Application Update — Ushirika Welfare Organization",
                "Dear " + application.getMember().getFirstName() + ",\n\n" +
                "We regret to inform you that the scholarship application for " +
                application.getBeneficiaryName() + " (ref: " + application.getReferenceNumber() +
                ") was not approved at this time.\n\n" +
                "You are welcome to apply for future programs. Please contact our office if you have questions.\n\n" +
                "Regards,\nUshirika Welfare Organization"
        );
        log.info("Scholarship application {} rejected.", application.getReferenceNumber());
    }

    private void applyApproval(ScholarshipApplication application) {
        application.setStatus(ScholarshipApplicationStatus.APPROVED);
        application.setApprovedAt(LocalDateTime.now());
        application.setReviewedAt(LocalDateTime.now());

        emailService.sendPlain(
                application.getMember().getEmail(),
                application.getMember().getFullName(),
                "Scholarship Application Approved — Ushirika Welfare Organization",
                "Dear " + application.getMember().getFirstName() + ",\n\n" +
                "Great news! The scholarship application for " + application.getBeneficiaryName() +
                " (ref: " + application.getReferenceNumber() + ") has been approved.\n\n" +
                "The award disbursement will be processed shortly.\n\n" +
                "Warmly,\nUshirika Welfare Organization"
        );
        log.info("Scholarship application {} approved.", application.getReferenceNumber());
    }

    private void notifyAdmins(ScholarshipApplication application) {
        userRepository.findAllByRoleIn(List.of(UserRole.ADMIN, UserRole.SUPERADMIN)).forEach(admin ->
                emailService.sendPlain(
                        admin.getEmail(), admin.getFullName(),
                        "New Scholarship Application — Action Required",
                        "Hello " + admin.getFirstName() + ",\n\n" +
                        "A scholarship application requires your review.\n\n" +
                        "Applicant: " + application.getMember().getFullName() + "\n" +
                        "Beneficiary: " + application.getBeneficiaryName() + "\n" +
                        "Program: " + application.getProgram().getName() + "\n" +
                        "Reference: " + application.getReferenceNumber() + "\n\n" +
                        "Log in to the admin portal to cast your vote.\n\n" +
                        "Ushirika Welfare Organization"
                )
        );
    }

    /**
     * Verifies that the current user is an approved member (has a memberId).
     * Scholarship applications are restricted to full members only.
     */
    private void assertApprovedMember(User user) {
        boolean isApprovedMember = memberProfileRepository.findByUser(user)
                .map(p -> p.getMemberId() != null)
                .orElse(false);
        if (!isApprovedMember) {
            throw new ForbiddenException(
                    "You must be an approved Ushirika Welfare Organization member to apply for a scholarship.");
        }
    }

    private ScholarshipProgram findProgramById(UUID id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scholarship program not found: " + id));
    }

    private ScholarshipApplication findApplicationById(UUID id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scholarship application not found: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }

    private String generateReference() {
        return "UWF-SCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
