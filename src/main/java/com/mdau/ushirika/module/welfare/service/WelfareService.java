package com.mdau.ushirika.module.welfare.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
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
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.welfare.dto.*;
import com.mdau.ushirika.module.welfare.entity.WelfareCategory;
import com.mdau.ushirika.module.welfare.entity.WelfareDisbursement;
import com.mdau.ushirika.module.welfare.entity.WelfareRequest;
import com.mdau.ushirika.module.welfare.entity.WelfareRequestApproval;
import com.mdau.ushirika.module.welfare.enums.WelfareRequestStatus;
import com.mdau.ushirika.module.welfare.repository.WelfareCategoryRepository;
import com.mdau.ushirika.module.welfare.repository.WelfareDisbursementRepository;
import com.mdau.ushirika.module.welfare.repository.WelfareRequestApprovalRepository;
import com.mdau.ushirika.module.welfare.repository.WelfareRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WelfareService {

    private final WelfareRequestRepository requestRepository;
    private final WelfareCategoryRepository categoryRepository;
    private final WelfareRequestApprovalRepository approvalRepository;
    private final WelfareDisbursementRepository disbursementRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final QuorumApprovalService quorumApprovalService;
    private final AuditLogService auditLogService;

    // ─────────────────────────────────────── Categories (admin-managed)

    @Transactional(readOnly = true)
    public List<WelfareCategoryDto> listActiveCategories() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc()
                .stream().map(WelfareCategoryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<WelfareCategoryDto> listAllCategories() {
        return categoryRepository.findAll()
                .stream().map(WelfareCategoryDto::from).toList();
    }

    @Transactional
    public WelfareCategoryDto createCategory(WelfareCategoryRequest req) {
        if (categoryRepository.existsByNameIgnoreCase(req.name())) {
            throw new ConflictException("A welfare category named '" + req.name() + "' already exists.");
        }
        WelfareCategory cat = WelfareCategory.builder()
                .name(req.name())
                .description(req.description())
                .maxAmount(req.maxAmount())
                .active(req.active())
                .build();
        return WelfareCategoryDto.from(categoryRepository.save(cat));
    }

    @Transactional
    public WelfareCategoryDto updateCategory(UUID id, WelfareCategoryRequest req) {
        WelfareCategory cat = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Welfare category not found: " + id));
        cat.setName(req.name());
        cat.setDescription(req.description());
        cat.setMaxAmount(req.maxAmount());
        cat.setActive(req.active());
        return WelfareCategoryDto.from(categoryRepository.save(cat));
    }

    // ─────────────────────────────────────── Member — create & submit

    @Transactional
    public WelfareRequestTrackDto saveRequest(WelfareRequestRequest req) {
        User member = currentUser();
        WelfareCategory category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Welfare category not found: " + req.categoryId()));

        if (!category.isActive()) {
            throw new BadRequestException("The selected welfare category is no longer active.");
        }

        if (category.getMaxAmount() != null
                && req.amountRequested().compareTo(category.getMaxAmount()) > 0) {
            throw new BadRequestException(
                    "Requested amount exceeds the category limit of " +
                    category.getMaxAmount() + " " + category.getCurrency() + ".");
        }

        WelfareRequest request = WelfareRequest.builder()
                .member(member)
                .category(category)
                .referenceNumber(generateReference())
                .amountRequested(req.amountRequested())
                .description(req.description())
                .documentUrls(req.documentUrls() != null ? req.documentUrls() : List.of())
                .build();

        return WelfareRequestTrackDto.from(requestRepository.save(request));
    }

    @Transactional
    public WelfareRequestTrackDto submitRequest(UUID requestId) {
        User member = currentUser();
        WelfareRequest request = findRequestById(requestId);

        if (!request.getMember().getId().equals(member.getId())) {
            throw new BadRequestException("You can only submit your own welfare requests.");
        }
        if (request.getStatus() != WelfareRequestStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT requests can be submitted. Current: " + request.getStatus());
        }

        request.setStatus(WelfareRequestStatus.SUBMITTED);
        request.setSubmittedAt(LocalDateTime.now());
        requestRepository.save(request);

        notifyAdmins(request, member);
        return WelfareRequestTrackDto.from(request);
    }

    @Transactional(readOnly = true)
    public PagedResponse<WelfareRequestTrackDto> myRequests(Pageable pageable) {
        User member = currentUser();
        return PagedResponse.of(
                requestRepository.findAllByMemberOrderByCreatedAtDesc(member, pageable)
                        .map(WelfareRequestTrackDto::from)
        );
    }

    @Transactional(readOnly = true)
    public WelfareRequestTrackDto myRequest(UUID id) {
        User member = currentUser();
        WelfareRequest request = findRequestById(id);
        if (!request.getMember().getId().equals(member.getId())) {
            throw new BadRequestException("Request not found.");
        }
        return WelfareRequestTrackDto.from(request);
    }

    // ─────────────────────────────────────── Admin — list & review

    @Transactional(readOnly = true)
    public PagedResponse<AdminWelfareRequestDto> listRequests(
            WelfareRequestStatus status, Pageable pageable, boolean isSuperAdmin) {
        var page = status != null
                ? requestRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
                : requestRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PagedResponse.of(page.map(r -> AdminWelfareRequestDto.from(r, isSuperAdmin)));
    }

    @Transactional(readOnly = true)
    public AdminWelfareRequestDto getRequest(UUID id, boolean isSuperAdmin) {
        return AdminWelfareRequestDto.from(findRequestById(id), isSuperAdmin);
    }

    @Transactional
    public AdminWelfareRequestDto review(UUID requestId, WelfareReviewRequest req, boolean isSuperAdmin) {
        User admin = currentUser();
        WelfareRequest request = findRequestById(requestId);

        if (!List.of(WelfareRequestStatus.SUBMITTED, WelfareRequestStatus.UNDER_REVIEW)
                .contains(request.getStatus())) {
            throw new BadRequestException(
                    "Only SUBMITTED or UNDER_REVIEW requests can be reviewed. Current: " + request.getStatus());
        }
        if (approvalRepository.existsByWelfareRequestAndAdmin(request, admin)) {
            throw new ConflictException("You have already cast your vote on this request.");
        }

        WelfareRequestApproval approval = WelfareRequestApproval.builder()
                .welfareRequest(request)
                .admin(admin)
                .decision(req.decision())
                .comment(req.comment())
                .decidedAt(LocalDateTime.now())
                .build();
        approvalRepository.save(approval);
        request.getApprovals().add(approval);
        request.setStatus(WelfareRequestStatus.UNDER_REVIEW);

        long approved = approvalRepository.countByWelfareRequestAndDecision(request, ApprovalDecision.APPROVED);
        long rejected = approvalRepository.countByWelfareRequestAndDecision(request, ApprovalDecision.REJECTED);

        QuorumResult result = quorumApprovalService.evaluate(approved, rejected);
        switch (result) {
            case REJECTED -> applyRejection(request);
            case APPROVED -> applyApproval(request);
            case PENDING  -> {}
        }

        requestRepository.save(request);
        return AdminWelfareRequestDto.from(request, isSuperAdmin);
    }

    // ─────────────────────────────────────── Superadmin — disburse

    @Transactional
    public AdminWelfareRequestDto recordDisbursement(UUID requestId, DisbursementRequest req) {
        User admin = currentUser();
        WelfareRequest request = findRequestById(requestId);

        if (request.getStatus() != WelfareRequestStatus.APPROVED) {
            throw new BadRequestException(
                    "Only APPROVED requests can be disbursed. Current: " + request.getStatus());
        }
        if (disbursementRepository.existsByWelfareRequest(request)) {
            throw new ConflictException("Disbursement already recorded for this request.");
        }

        WelfareDisbursement disbursement = WelfareDisbursement.builder()
                .welfareRequest(request)
                .amountDisbursed(req.amountDisbursed())
                .method(req.method())
                .transactionReference(req.transactionReference())
                .disbursedBy(admin)
                .disbursedAt(LocalDateTime.now())
                .notes(req.notes())
                .build();
        disbursementRepository.save(disbursement);
        request.setDisbursement(disbursement);
        request.setStatus(WelfareRequestStatus.DISBURSED);
        requestRepository.save(request);

        auditLogService.log(admin, "WELFARE_DISBURSED", "WELFARE_REQUEST", request.getId(),
                "Welfare disbursement of $" + req.amountDisbursed() + " to " + request.getMember().getFullName()
                        + " (ref: " + request.getReferenceNumber() + ") via " + req.method(),
                req.amountDisbursed(), LedgerDirection.OUT);

        emailService.sendPlain(
                request.getMember().getEmail(),
                request.getMember().getFullName(),
                "Welfare Disbursement — Ushirika Welfare Organization",
                "Dear " + request.getMember().getFirstName() + ",\n\n" +
                "Your welfare support of $" + req.amountDisbursed() + " (ref: " +
                request.getReferenceNumber() + ") has been disbursed via " +
                req.method().name().replace("_", " ") + ".\n\n" +
                (req.transactionReference() != null
                        ? "Transaction reference: " + req.transactionReference() + "\n\n"
                        : "") +
                "Thank you for being part of Ushirika Welfare Organization.\n\n" +
                "Regards,\nUshirika Welfare Organization"
        );

        log.info("Welfare request {} disbursed: ${} via {}",
                request.getReferenceNumber(), req.amountDisbursed(), req.method());
        return AdminWelfareRequestDto.from(request, true);
    }

    // ─────────────────────────────────────── Private

    private void applyRejection(WelfareRequest request) {
        request.setStatus(WelfareRequestStatus.REJECTED);
        request.setRejectionReason("Your welfare request was reviewed and not approved by the board.");
        request.setReviewedAt(LocalDateTime.now());

        emailService.sendPlain(
                request.getMember().getEmail(),
                request.getMember().getFullName(),
                "Welfare Request Update — Ushirika Welfare Organization",
                "Dear " + request.getMember().getFirstName() + ",\n\n" +
                "We regret to inform you that your welfare request (ref: " +
                request.getReferenceNumber() + ") has not been approved at this time.\n\n" +
                "If you have questions or wish to appeal, please contact our office.\n\n" +
                "Regards,\nUshirika Welfare Organization"
        );
        log.info("Welfare request {} rejected.", request.getReferenceNumber());
    }

    private void applyApproval(WelfareRequest request) {
        request.setStatus(WelfareRequestStatus.APPROVED);
        request.setApprovedAt(LocalDateTime.now());
        request.setReviewedAt(LocalDateTime.now());

        emailService.sendPlain(
                request.getMember().getEmail(),
                request.getMember().getFullName(),
                "Welfare Request Approved — Ushirika Welfare Organization",
                "Dear " + request.getMember().getFirstName() + ",\n\n" +
                "Great news! Your welfare request (ref: " + request.getReferenceNumber() +
                ") for $" + request.getAmountRequested() + " has been approved.\n\n" +
                "Disbursement will be processed shortly. You will receive a confirmation once done.\n\n" +
                "Warmly,\nUshirika Welfare Organization"
        );
        log.info("Welfare request {} approved.", request.getReferenceNumber());
    }

    private void notifyAdmins(WelfareRequest request, User member) {
        userRepository.findAllByRoleIn(List.of(UserRole.ADMIN, UserRole.SUPERADMIN)).forEach(admin ->
                emailService.sendPlain(
                        admin.getEmail(), admin.getFullName(),
                        "New Welfare Request — Action Required",
                        "Hello " + admin.getFirstName() + ",\n\n" +
                        "A welfare request requires your review.\n\n" +
                        "Member: " + member.getFullName() + "\n" +
                        "Category: " + request.getCategory().getName() + "\n" +
                        "Amount: $" + request.getAmountRequested() + "\n" +
                        "Reference: " + request.getReferenceNumber() + "\n\n" +
                        "Log in to the admin portal to cast your vote.\n\n" +
                        "Ushirika Welfare Organization"
                )
        );
    }

    private WelfareRequest findRequestById(UUID id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Welfare request not found: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }

    private String generateReference() {
        return "UWF-WEL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
