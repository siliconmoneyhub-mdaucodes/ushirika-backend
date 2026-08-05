package com.mdau.ushirika.module.reinstatement.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.notification.enums.InAppNotificationCategory;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import com.mdau.ushirika.module.notification.service.SmsService;
import com.mdau.ushirika.module.reinstatement.dto.ReinstatementRequestDto;
import com.mdau.ushirika.module.reinstatement.dto.SubmitReinstatementRequest;
import com.mdau.ushirika.module.reinstatement.entity.ReinstatementRequest;
import com.mdau.ushirika.module.reinstatement.enums.ReinstatementStatus;
import com.mdau.ushirika.module.reinstatement.repository.ReinstatementRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
public class ReinstatementService {

    private final ReinstatementRequestRepository reinstatementRepository;
    private final UserRepository                 userRepository;
    private final EmailService                   emailService;
    private final SmsService                     smsService;
    private final InAppNotificationService       notificationService;
    private final AuditLogService                auditLogService;

    // ── Member ────────────────────────────────────────────────────────────────

    @Transactional
    public ReinstatementRequestDto submitRequest(SubmitReinstatementRequest req) {
        User user = currentUser();

        if (!user.isMembershipCeased()) {
            throw new BadRequestException("Reinstatement is only available when your membership has been ceased.");
        }
        if (reinstatementRepository.existsByUserIdAndStatus(user.getId(), ReinstatementStatus.PENDING)) {
            throw new BadRequestException("You already have a pending reinstatement request. Please wait for the administrator to review it.");
        }

        ReinstatementRequest request = ReinstatementRequest.builder()
                .userId(user.getId())
                .reason(req.reason())
                .build();

        return ReinstatementRequestDto.from(reinstatementRepository.save(request));
    }

    public List<ReinstatementRequestDto> getMyRequests() {
        User user = currentUser();
        return reinstatementRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(ReinstatementRequestDto::from)
                .toList();
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    public Page<ReinstatementRequestDto> listAll(String statusFilter, Pageable pageable) {
        if (statusFilter != null && !statusFilter.isBlank()) {
            ReinstatementStatus status = ReinstatementStatus.valueOf(statusFilter.toUpperCase());
            return reinstatementRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
                    .map(ReinstatementRequestDto::from);
        }
        return reinstatementRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(ReinstatementRequestDto::from);
    }

    @Transactional
    public ReinstatementRequestDto approve(UUID id, String adminNotes) {
        User admin = currentUser();
        ReinstatementRequest request = findById(id);

        if (request.getStatus() != ReinstatementStatus.PENDING) {
            throw new BadRequestException("Only PENDING requests can be approved.");
        }

        request.setStatus(ReinstatementStatus.APPROVED);
        request.setAdminNotes(adminNotes);
        request.setReviewedBy(admin.getId());
        request.setReviewedAt(LocalDateTime.now());
        reinstatementRepository.save(request);

        User member = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found."));
        member.setMembershipCeased(false);
        member.setActive(true);
        userRepository.save(member);

        String subject = "Your reinstatement request has been approved";
        String body = String.format(
            "Hi %s, your membership reinstatement request has been approved. " +
            "Your account is now active again. Welcome back to Ushirika Welfare Organization! " +
            "Log in at https://ushirikacommunity.site",
            member.getFullName());

        try { emailService.sendPlain(member.getEmail(), member.getFullName(), subject, toApprovalHtml(member.getFullName(), adminNotes)); }
        catch (Exception e) { log.warn("Reinstatement approval email failed for {}: {}", member.getEmail(), e.getMessage()); }

        if (member.getPhone() != null) {
            try { smsService.send(member.getPhone(), member.getFullName(), subject + "\n" + body); }
            catch (Exception e) { log.warn("Reinstatement approval SMS failed for {}: {}", member.getPhone(), e.getMessage()); }
        }

        notificationService.createForUser(
                member.getId(),
                InAppNotificationCategory.ANNOUNCEMENT,
                subject,
                body,
                "/portal"
        );

        auditLogService.log(admin, "REINSTATEMENT_APPROVED", "ReinstatementRequest", id,
                "Approved reinstatement for " + member.getFullName() + " (" + member.getEmail() + ")");
        log.info("ReinstatementService: approved request {} for user {}", id, member.getEmail());
        return ReinstatementRequestDto.from(request);
    }

    @Transactional
    public ReinstatementRequestDto reject(UUID id, String adminNotes) {
        User admin = currentUser();
        ReinstatementRequest request = findById(id);

        if (request.getStatus() != ReinstatementStatus.PENDING) {
            throw new BadRequestException("Only PENDING requests can be rejected.");
        }

        request.setStatus(ReinstatementStatus.REJECTED);
        request.setAdminNotes(adminNotes);
        request.setReviewedBy(admin.getId());
        request.setReviewedAt(LocalDateTime.now());
        reinstatementRepository.save(request);

        User member = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found."));

        String subject = "Your reinstatement request has been reviewed";
        String body = String.format(
            "Hi %s, your membership reinstatement request has been reviewed and was not approved at this time. %s" +
            "Please contact the Ushirika Welfare Organization administrator if you have questions.",
            member.getFullName(),
            adminNotes != null ? "Admin notes: " + adminNotes + ". " : "");

        try { emailService.sendPlain(member.getEmail(), member.getFullName(), subject, toRejectionHtml(member.getFullName(), adminNotes)); }
        catch (Exception e) { log.warn("Reinstatement rejection email failed for {}: {}", member.getEmail(), e.getMessage()); }

        if (member.getPhone() != null) {
            try { smsService.send(member.getPhone(), member.getFullName(), subject + "\n" + body); }
            catch (Exception e) { log.warn("Reinstatement rejection SMS failed for {}: {}", member.getPhone(), e.getMessage()); }
        }

        notificationService.createForUser(
                member.getId(),
                InAppNotificationCategory.ANNOUNCEMENT,
                subject,
                body,
                "/portal"
        );

        auditLogService.log(admin, "REINSTATEMENT_REJECTED", "ReinstatementRequest", id,
                "Rejected reinstatement for " + member.getFullName() + " (" + member.getEmail() + ")");
        log.info("ReinstatementService: rejected request {} for user {}", id, member.getEmail());
        return ReinstatementRequestDto.from(request);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReinstatementRequest findById(UUID id) {
        return reinstatementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reinstatement request not found."));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }

    private static String toApprovalHtml(String name, String adminNotes) {
        return """
            <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
              <h2 style="color:#007834">Membership Reinstated</h2>
              <p>Dear %s,</p>
              <p>We are pleased to inform you that your membership reinstatement request has been <strong style="color:#007834">approved</strong>.</p>
              <p>Your account is now active again. You can log in and access all member services.</p>
              %s
              <p><a href="https://ushirikacommunity.site/portal"
                    style="background:#007834;color:#fff;padding:10px 20px;border-radius:8px;text-decoration:none">
                Access Member Portal
              </a></p>
              <p style="color:#888;font-size:12px">Welcome back to Ushirika Welfare Organization!</p>
            </div>
            """.formatted(
                name,
                adminNotes != null ? "<p><strong>Admin note:</strong> " + adminNotes + "</p>" : "");
    }

    private static String toRejectionHtml(String name, String adminNotes) {
        return """
            <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
              <h2 style="color:#C0392B">Reinstatement Request — Not Approved</h2>
              <p>Dear %s,</p>
              <p>Your membership reinstatement request has been reviewed and was <strong style="color:#C0392B">not approved</strong> at this time.</p>
              %s
              <p>Please contact the Ushirika Welfare Organization administrator for more information or to discuss your situation.</p>
              <p style="color:#888;font-size:12px">Ushirika Welfare Organization</p>
            </div>
            """.formatted(
                name,
                adminNotes != null ? "<p><strong>Reason:</strong> " + adminNotes + "</p>" : "");
    }
}
