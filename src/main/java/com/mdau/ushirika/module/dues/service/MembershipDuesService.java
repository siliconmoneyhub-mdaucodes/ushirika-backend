package com.mdau.ushirika.module.dues.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.dues.dto.*;
import com.mdau.ushirika.module.dues.entity.DuesPayment;
import com.mdau.ushirika.module.dues.entity.MembershipDue;
import com.mdau.ushirika.module.dues.enums.DuesPaymentStatus;
import com.mdau.ushirika.module.dues.enums.DuesStatus;
import com.mdau.ushirika.module.dues.repository.DuesPaymentRepository;
import com.mdau.ushirika.module.dues.repository.MembershipDueRepository;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.payment.enums.PaymentMode;
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
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipDuesService {

    static final BigDecimal ANNUAL_FEE = new BigDecimal("100.00");

    private final MembershipDueRepository dueRepository;
    private final DuesPaymentRepository duesPaymentRepository;
    private final MemberProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    // ── Called on member approval ─────────────────────────────────────────────

    @Transactional
    public void createInitialDues(User user) {
        int year = LocalDate.now().getYear();
        if (dueRepository.findByUserAndYear(user, year).isPresent()) return;

        MembershipDue due = MembershipDue.builder()
                .user(user)
                .year(year)
                .amount(ANNUAL_FEE)
                .dueDate(LocalDate.of(year, 10, 31))
                .status(DuesStatus.PENDING)
                .build();
        dueRepository.save(due);
        log.info("Created initial dues record for user {} year {}", user.getId(), year);
    }

    // ── Current year status (used for profile status derivation) ─────────────

    @Transactional(readOnly = true)
    public Optional<DuesStatus> getCurrentYearStatus(User user) {
        return dueRepository.findByUserAndYear(user, LocalDate.now().getYear())
                .map(MembershipDue::getStatus);
    }

    // ── Member: submit an installment payment ─────────────────────────────────

    @Transactional
    public DuesPaymentDto submitInstallment(SubmitDuesInstallmentRequest req) {
        User member = currentUser();
        MembershipDue due = dueRepository.findById(req.duesId())
                .orElseThrow(() -> new ResourceNotFoundException("Dues record not found: " + req.duesId()));

        if (!due.getUser().getId().equals(member.getId())) {
            throw new ForbiddenException("You can only submit payments toward your own dues.");
        }
        if (due.getStatus() == DuesStatus.WAIVED) {
            throw new BadRequestException("These dues have been waived — no payment needed.");
        }
        if (due.getStatus() == DuesStatus.PAID) {
            throw new BadRequestException("Annual dues for " + due.getYear() + " are already fully paid.");
        }

        if (duesPaymentRepository.existsByMemberTxReferenceIgnoreCase(req.memberTxReference())) {
            throw new ConflictException(
                "This transaction reference has already been submitted. " +
                "Each transaction reference can only be used once.");
        }

        DuesPayment payment = DuesPayment.builder()
                .dues(due)
                .member(member)
                .amount(req.amount())
                .paymentMode(req.paymentMode())
                .memberTxReference(req.memberTxReference().strip())
                .notes(req.notes())
                .build();

        duesPaymentRepository.save(payment);
        log.info("Dues installment submitted: duesId={} member={} amount={}",
                req.duesId(), member.getEmail(), req.amount());

        return DuesPaymentDto.memberView(payment);
    }

    // ── Member: view own installments ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<DuesPaymentDto> getMyInstallments(Pageable pageable) {
        User member = currentUser();
        return PagedResponse.of(
            duesPaymentRepository.findAllByMemberOrderByCreatedAtDesc(member, pageable)
                .map(DuesPaymentDto::memberView));
    }

    // ── Admin: list installments for a specific dues record ───────────────────

    @Transactional(readOnly = true)
    public List<DuesPaymentDto> getInstallments(UUID duesId) {
        MembershipDue due = findById(duesId);
        return duesPaymentRepository.findAllByDuesOrderByCreatedAtAsc(due).stream()
                .map(DuesPaymentDto::from)
                .toList();
    }

    // ── Admin: list all installments (paginated, optional status filter) ──────

    @Transactional(readOnly = true)
    public PagedResponse<DuesPaymentDto> listAllInstallments(DuesPaymentStatus status, Pageable pageable) {
        if (status != null) {
            return PagedResponse.of(
                duesPaymentRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
                    .map(DuesPaymentDto::from));
        }
        return PagedResponse.of(
            duesPaymentRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(DuesPaymentDto::from));
    }

    // ── Admin: verify an installment ──────────────────────────────────────────

    /**
     * Admin enters the TX reference from their own Zelle/Venmo/CashApp.
     * If it matches (case-insensitive) what the member submitted, the installment
     * is VERIFIED and applied to the running paidAmount on the dues record.
     * If paidAmount >= $100 the dues are automatically marked PAID.
     */
    @Transactional
    public DuesPaymentDto verifyInstallment(UUID installmentId, VerifyDuesInstallmentRequest req) {
        User admin = currentUser();
        DuesPayment payment = findInstallmentById(installmentId);

        if (payment.getStatus() != DuesPaymentStatus.PENDING) {
            throw new BadRequestException(
                "Only PENDING installments can be verified. Current status: " + payment.getStatus());
        }

        if (!payment.getMemberTxReference().strip()
                    .equalsIgnoreCase(req.adminTxReference().strip())) {
            throw new BadRequestException(
                "Transaction reference mismatch. The reference you entered (" +
                req.adminTxReference().strip() + ") does not match what the member submitted. " +
                "Please check your " + label(payment.getPaymentMode()) + " app and try again, " +
                "or reject this installment if the payment was not received.");
        }

        payment.setAdminTxReference(req.adminTxReference().strip());
        payment.setStatus(DuesPaymentStatus.VERIFIED);
        payment.setVerifiedBy(admin);
        payment.setVerifiedAt(LocalDateTime.now());
        duesPaymentRepository.save(payment);

        // Re-derive paidAmount from DB sum of all VERIFIED installments (idempotent)
        MembershipDue due = payment.getDues();
        BigDecimal totalPaid = duesPaymentRepository.sumVerifiedAmount(due, DuesPaymentStatus.VERIFIED);
        due.setPaidAmount(totalPaid);

        if (totalPaid.compareTo(ANNUAL_FEE) >= 0 && due.getStatus() != DuesStatus.PAID) {
            due.setStatus(DuesStatus.PAID);
            due.setPaidAt(LocalDateTime.now());
            reactivateIfNeeded(due.getUser(), "dues paid in full via installment");
            log.info("Dues fully paid: duesId={} member={} year={} totalPaid={}",
                    due.getId(), due.getUser().getEmail(), due.getYear(), totalPaid);
        }
        dueRepository.save(due);

        log.info("Dues installment verified: id={} member={} amount={} totalPaid={} by={}",
                installmentId, payment.getMember().getEmail(), payment.getAmount(), totalPaid, admin.getEmail());

        auditLogService.log(admin, "DUES_INSTALLMENT_VERIFIED", "DuesPayment", installmentId,
            String.format("Verified dues installment of $%s for %s (year %d). Total paid: $%s",
                payment.getAmount().toPlainString(),
                due.getUser().getFullName(),
                due.getYear(),
                totalPaid.toPlainString()));

        sendInstallmentVerifiedEmail(payment, totalPaid);
        return DuesPaymentDto.from(payment);
    }

    // ── Called by other verified-payment paths (e.g. PeerPaymentService) ──────

    /**
     * Credits a verified payment from outside the installment system (currently: a
     * PeerPayment reported with purpose=DUES) toward the member's current-year dues.
     * Additive on top of paidAmount rather than a re-derived sum, since — unlike
     * DuesPayment — these records don't carry a FK back to a specific MembershipDue.
     * Safe from double-counting because callers only invoke this once per payment,
     * inside their own verify() idempotency guard.
     */
    @Transactional
    public void applyExternalPayment(User member, BigDecimal amount) {
        int year = LocalDate.now().getYear();
        dueRepository.findByUserAndYear(member, year).ifPresent(due -> {
            if (due.getStatus() == DuesStatus.PAID || due.getStatus() == DuesStatus.WAIVED) return;

            BigDecimal newTotal = due.getPaidAmount().add(amount);
            due.setPaidAmount(newTotal);

            if (newTotal.compareTo(ANNUAL_FEE) >= 0) {
                due.setStatus(DuesStatus.PAID);
                due.setPaidAt(LocalDateTime.now());
                reactivateIfNeeded(member, "dues paid in full via peer payment");
                log.info("Dues fully paid via external payment: member={} year={} totalPaid={}",
                        member.getEmail(), year, newTotal);
            }
            dueRepository.save(due);
        });
    }

    // ── Admin: reject an installment ──────────────────────────────────────────

    @Transactional
    public DuesPaymentDto rejectInstallment(UUID installmentId, RejectDuesInstallmentRequest req) {
        User admin = currentUser();
        DuesPayment payment = findInstallmentById(installmentId);

        if (payment.getStatus() != DuesPaymentStatus.PENDING) {
            throw new BadRequestException(
                "Only PENDING installments can be rejected. Current status: " + payment.getStatus());
        }

        payment.setStatus(DuesPaymentStatus.REJECTED);
        payment.setRejectionReason(req.reason());
        payment.setVerifiedBy(admin);
        payment.setVerifiedAt(LocalDateTime.now());
        duesPaymentRepository.save(payment);

        log.info("Dues installment rejected: id={} member={} reason={} by={}",
                installmentId, payment.getMember().getEmail(), req.reason(), admin.getEmail());

        sendInstallmentRejectedEmail(payment, req.reason());
        return DuesPaymentDto.from(payment);
    }

    // ── Admin: record payment (legacy direct-entry path) ─────────────────────

    @Transactional
    public MembershipDueDto recordPayment(RecordDuesPaymentRequest req) {
        User user = userRepository.findById(UUID.fromString(req.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + req.userId()));

        MembershipDue due = dueRepository.findByUserAndYear(user, req.year())
                .orElseGet(() -> MembershipDue.builder()
                        .user(user)
                        .year(req.year())
                        .amount(ANNUAL_FEE)
                        .dueDate(LocalDate.of(req.year(), 10, 31))
                        .status(DuesStatus.PENDING)
                        .build());

        if (due.getStatus() == DuesStatus.PAID) {
            throw new BadRequestException("Dues for " + req.year() + " are already marked PAID.");
        }

        due.setStatus(DuesStatus.PAID);
        due.setPaidAt(LocalDateTime.now());
        due.setPaidAmount(ANNUAL_FEE);
        due.setPaymentMethod(req.paymentMethod());
        due.setPaymentReference(req.paymentReference());
        due.setNotes(req.notes());
        dueRepository.save(due);
        reactivateIfNeeded(user, "dues recorded by admin");

        log.info("Dues paid (direct): user={} year={} method={}", user.getId(), req.year(), req.paymentMethod());
        MembershipDueDto dto = MembershipDueDto.from(due, memberId(user));
        auditLogService.log(currentUser(), "DUES_RECORDED", "MembershipDue", due.getId(),
                String.format("Dues recorded for %s — year %d via %s",
                        user.getFullName(), req.year(), req.paymentMethod()));
        return dto;
    }

    // ── Admin: waive dues ─────────────────────────────────────────────────────

    @Transactional
    public MembershipDueDto waiveDues(UUID dueId, WaiveDuesRequest req) {
        MembershipDue due = findById(dueId);
        if (due.getStatus() == DuesStatus.PAID) {
            throw new BadRequestException("Cannot waive dues that are already PAID.");
        }
        due.setStatus(DuesStatus.WAIVED);
        due.setNotes(req != null && req.reason() != null ? req.reason() : due.getNotes());
        dueRepository.save(due);
        reactivateIfNeeded(due.getUser(), "dues waived by admin");
        auditLogService.log(currentUser(), "DUES_WAIVED", "MembershipDue", due.getId(),
                "Waived dues for " + due.getUser().getFullName() + " — year " + due.getYear());
        return MembershipDueDto.from(due, memberId(due.getUser()));
    }

    // ── Scheduler: mark overdue ───────────────────────────────────────────────

    @Transactional
    public int assessOverdue() {
        List<MembershipDue> overdue = dueRepository.findOverdue(LocalDate.now(), DuesStatus.PENDING);
        overdue.forEach(d -> d.setStatus(DuesStatus.OVERDUE));
        dueRepository.saveAll(overdue);

        overdue.stream()
                .map(MembershipDue::getUser)
                .filter(User::isActive)
                .forEach(u -> {
                    u.setActive(false);
                    userRepository.save(u);
                    sendDeactivationEmail(u);
                    log.info("Deactivated member {} — overdue dues", u.getEmail());
                });

        log.info("Marked {} dues records as OVERDUE", overdue.size());
        return overdue.size();
    }

    // ── Admin: list all dues ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<MembershipDueDto> listAll(Integer year, DuesStatus status, Pageable pageable) {
        Page<MembershipDue> page;
        if (year != null && status != null) {
            page = dueRepository.findAllByYearAndStatusOrderByCreatedAtDesc(year, status, pageable);
        } else if (year != null) {
            page = dueRepository.findAllByYearOrderByCreatedAtDesc(year, pageable);
        } else if (status != null) {
            page = dueRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            page = dueRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return PagedResponse.of(page.map(d -> MembershipDueDto.from(d, memberId(d.getUser()))));
    }

    @Transactional(readOnly = true)
    public List<MembershipDueDto> getMemberDuesHistory(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        String mid = memberId(user);
        return dueRepository.findByUserOrderByYearDesc(user).stream()
                .map(d -> MembershipDueDto.from(d, mid))
                .toList();
    }

    // ── Member: view own dues ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MembershipDueDto> getMyDues() {
        User user = currentUser();
        String mid = memberId(user);
        return dueRepository.findByUserOrderByYearDesc(user).stream()
                .map(d -> MembershipDueDto.from(d, mid))
                .toList();
    }

    // ── Email notifications ───────────────────────────────────────────────────

    private void sendInstallmentVerifiedEmail(DuesPayment p, BigDecimal totalPaid) {
        String name       = p.getMember().getFullName();
        String email      = p.getMember().getEmail();
        String method     = label(p.getPaymentMode());
        String amount     = "$" + p.getAmount().toPlainString();
        String total      = "$" + totalPaid.toPlainString();
        BigDecimal remaining = ANNUAL_FEE.subtract(totalPaid).max(BigDecimal.ZERO);
        boolean fullPaid  = remaining.compareTo(BigDecimal.ZERO) == 0;
        String html = fullPaid ? """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#1A4731">Dues Fully Paid ✓</h2>
              <p>Hi %s,</p>
              <p>Your <strong>%s</strong> payment of <strong>%s</strong> has been verified.
                 Your annual dues for %d are now <strong>fully paid</strong>.</p>
              <p style="color:#555">Transaction reference: <strong>%s</strong></p>
              <p>Thank you for your timely contribution to Ushirika Welfare DFW.</p>
              <p>— Ushirika Welfare Team</p>
            </div>
            """.formatted(name, method, amount, p.getDues().getYear(), p.getMemberTxReference())
            : """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#1A4731">Dues Installment Verified ✓</h2>
              <p>Hi %s,</p>
              <p>Your <strong>%s</strong> installment of <strong>%s</strong> has been verified
                 and applied to your %d annual dues.</p>
              <p style="color:#555">Transaction reference: <strong>%s</strong></p>
              <p>Total paid so far: <strong>%s</strong> &nbsp;|&nbsp;
                 Remaining: <strong>$%s</strong></p>
              <p>— Ushirika Welfare Team</p>
            </div>
            """.formatted(name, method, amount, p.getDues().getYear(),
                          p.getMemberTxReference(), total, remaining.toPlainString());
        emailService.sendPlain(email, name, "Dues Installment Verified — Ushirika Welfare", html);
    }

    private void sendInstallmentRejectedEmail(DuesPayment p, String reason) {
        String name   = p.getMember().getFullName();
        String email  = p.getMember().getEmail();
        String method = label(p.getPaymentMode());
        String portal = siteUrl + "/portal";
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#B91C1C">Dues Installment Could Not Be Verified</h2>
              <p>Hi %s,</p>
              <p>Your <strong>%s</strong> payment submission of
                 <strong>$%s</strong> toward your %d dues
                 (reference: <strong>%s</strong>) could not be verified.</p>
              <p><strong>Reason:</strong> %s</p>
              <p>If you have already sent the payment, please log into your portal and
                 re-submit the correct transaction reference from your %s app.</p>
              <p>
                <a href="%s"
                   style="display:inline-block;background:#1A4731;color:#fff;padding:10px 22px;
                          border-radius:999px;text-decoration:none;font-weight:600">
                  Re-submit in Portal
                </a>
              </p>
              <p>— Ushirika Welfare Team</p>
            </div>
            """.formatted(name, method, p.getAmount().toPlainString(),
                          p.getDues().getYear(), p.getMemberTxReference(),
                          reason, method, portal);
        emailService.sendPlain(email, name,
            "Action Required: Re-submit Dues Payment — Ushirika Welfare", html);
    }

    // ── Membership activation helpers ─────────────────────────────────────────

    private void reactivateIfNeeded(User user, String reason) {
        if (!user.isActive()) {
            user.setActive(true);
            userRepository.save(user);
            log.info("Reactivated member {} — {}", user.getEmail(), reason);
        }
    }

    private void sendDeactivationEmail(User user) {
        String name = user.getFullName();
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
              <h2 style="color:#B91C1C">Membership Status: Inactive</h2>
              <p>Hi %s,</p>
              <p>Your Ushirika Welfare membership has been set to <strong>Inactive</strong>
                 because your annual dues ($100) were not paid by the October 31st deadline.</p>
              <p>To restore your Active status, please log in to your member portal and submit
                 your payment. Your access to programs and benefits is paused until dues are settled.</p>
              <p>
                <a href="%s/portal/payments"
                   style="display:inline-block;background:#1A4731;color:#fff;padding:10px 22px;
                          border-radius:999px;text-decoration:none;font-weight:600">
                  Pay Now &rarr;
                </a>
              </p>
              <p style="color:#888;font-size:12px">
                Questions? Contact <a href="mailto:admin@ushirikawelfare.org">admin@ushirikawelfare.org</a>
              </p>
            </div>
            """.formatted(name, siteUrl);
        try {
            emailService.sendPlain(user.getEmail(), name,
                "Your Ushirika Membership is Now Inactive — Pay to Restore", html);
        } catch (Exception e) {
            log.warn("Could not send deactivation email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private MembershipDue findById(UUID id) {
        return dueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dues record not found: " + id));
    }

    private DuesPayment findInstallmentById(UUID id) {
        return duesPaymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dues installment not found: " + id));
    }

    private String memberId(User user) {
        return profileRepository.findByUser(user)
                .map(MemberProfile::getMemberId)
                .orElse(null);
    }

    private static String label(PaymentMode mode) {
        return switch (mode) {
            case ZELLE   -> "Zelle";
            case VENMO   -> "Venmo";
            case CASHAPP -> "CashApp";
        };
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
