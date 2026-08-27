package com.mdau.ushirika.module.dues.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.common.util.AppClock;
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
import com.mdau.ushirika.module.member.enums.MemberStatus;
import com.mdau.ushirika.module.member.enums.MemberStatusReason;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.member.repository.MembershipApplicationRepository;
import com.mdau.ushirika.module.member.service.MemberStatusChangeService;
import com.mdau.ushirika.module.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final MembershipApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final MemberStatusChangeService statusChangeService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    // ── Called on member approval ─────────────────────────────────────────────

    @Transactional
    public void createInitialDues(User user) {
        LocalDate today = AppClock.today();
        int year = today.getYear();
        // The membership year runs November 1 -> October 31. A brand-new member owes nothing
        // toward a cycle that's already mostly (or entirely) elapsed -- dues only become
        // required once the new cycle actually starts, with a ~2-month window to pay before
        // non-payment counts as overdue. Nov 1 of `year` is always either just about to happen
        // (approved Jan-Oct) or just happened (approved Nov-Dec) -- either way the matching due
        // date, two months in, is December 31 of that same calendar year.
        if (dueRepository.findByUserAndYear(user, year).isPresent()) return;

        MembershipDue due = MembershipDue.builder()
                .user(user)
                .year(year)
                .amount(ANNUAL_FEE)
                .dueDate(LocalDate.of(year, 12, 31))
                .status(DuesStatus.PENDING)
                .build();
        dueRepository.save(due);
        log.info("Created initial dues record for user {} year {}", user.getId(), year);
    }

    // ── Current year status (used for profile status derivation) ─────────────

    @Transactional(readOnly = true)
    public Optional<DuesStatus> getCurrentYearStatus(User user) {
        return dueRepository.findByUserAndYear(user, AppClock.today().getYear())
                .map(MembershipDue::getStatus);
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

    // ── Called by PaymentBasketService after a confirmed Stripe payment ───────

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
        int year = AppClock.today().getYear();
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

    // ── Admin: waive dues ─────────────────────────────────────────────────────

    /** Permanent waive — SUPERADMIN only (gated in AdminDuesController). Reactivates the
     *  member if they were deactivated for this. */
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
        return MembershipDueDto.from(due, memberId(due.getUser()), wasRegistrationFeeWaived(due.getUser()));
    }

    /** Temporary relief — available to any dues-capable role (not just SUPERADMIN). Resets the
     *  due back to PENDING with a fresh deadline instead of forgiving it outright, and
     *  reactivates the member so they can actually use their portal during the grace window.
     *  The nightly assessOverdue() run will pick it back up as OVERDUE if it's still unpaid
     *  once the new date passes -- the debt isn't gone, just deferred. */
    @Transactional
    public MembershipDueDto grantGracePeriod(UUID dueId, int days) {
        MembershipDue due = findById(dueId);
        if (due.getStatus() == DuesStatus.PAID || due.getStatus() == DuesStatus.WAIVED) {
            throw new BadRequestException("This due is already " + due.getStatus().name().toLowerCase() + ".");
        }
        due.setStatus(DuesStatus.PENDING);
        due.setDueDate(AppClock.today().plusDays(days));
        dueRepository.save(due);
        reactivateIfNeeded(due.getUser(), "granted a " + days + "-day dues grace period by admin");
        auditLogService.log(currentUser(), "DUES_GRACE_PERIOD_GRANTED", "MembershipDue", due.getId(),
                "Granted " + days + "-day dues grace period for " + due.getUser().getFullName() + " — year " + due.getYear());
        return MembershipDueDto.from(due, memberId(due.getUser()), wasRegistrationFeeWaived(due.getUser()));
    }

    /** Called from AdminUserService.setActive() whenever a member is reactivated -- resets any
     *  OVERDUE dues rows back to a fresh grace window so the very next nightly assessOverdue()
     *  run doesn't immediately undo the reactivation for the same unresolved debt. A no-op if
     *  the member has no overdue dues (e.g. they were suspended for an unrelated reason). */
    @Transactional
    public void resetOverdueDuesToGracePeriod(User user, int days) {
        LocalDate newDueDate = AppClock.today().plusDays(days);
        List<MembershipDue> overdue = dueRepository.findByUserOrderByYearDesc(user).stream()
                .filter(d -> d.getStatus() == DuesStatus.OVERDUE)
                .toList();
        if (overdue.isEmpty()) return;
        overdue.forEach(d -> { d.setStatus(DuesStatus.PENDING); d.setDueDate(newDueDate); });
        dueRepository.saveAll(overdue);
        auditLogService.log(currentUser(), "DUES_GRACE_PERIOD_GRANTED", "User", user.getId(),
                "Granted " + days + "-day dues grace period for " + user.getFullName()
                        + " (" + overdue.size() + " overdue due(s)) on reactivation");
    }

    // ── Scheduler: mark overdue ───────────────────────────────────────────────
    // Previously only reachable via the manual POST /admin/dues/assess-overdue endpoint -- an
    // admin had to remember to trigger it. Now also runs daily on its own; the manual endpoint
    // stays available for an on-demand re-check. Already idempotent by construction: only
    // still-PENDING dues get flipped (never re-processes ones already OVERDUE), and the
    // deactivation loop skips anyone already inactive.

    @Scheduled(cron = "0 0 6 * * *", zone = "America/Chicago")
    @Transactional
    public int assessOverdue() {
        List<MembershipDue> overdue = dueRepository.findOverdue(AppClock.today(), DuesStatus.PENDING);
        overdue.forEach(d -> d.setStatus(DuesStatus.OVERDUE));
        dueRepository.saveAll(overdue);

        overdue.stream()
                .map(MembershipDue::getUser)
                .filter(User::isActive)
                .forEach(u -> {
                    MemberStatus previousStatus = MemberStatusChangeService.statusOf(u.isActive(), u.isMembershipCeased());
                    u.setActive(false);
                    userRepository.save(u);
                    statusChangeService.record(u, previousStatus, MemberStatus.INACTIVE,
                            MemberStatusReason.DUES_NONPAYMENT, null, "Annual dues not paid by the December 31 deadline");
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
        return PagedResponse.of(page.map(d -> MembershipDueDto.from(d, memberId(d.getUser()), wasRegistrationFeeWaived(d.getUser()))));
    }

    @Transactional(readOnly = true)
    public List<MembershipDueDto> getMemberDuesHistory(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        String mid = memberId(user);
        boolean feeWaived = wasRegistrationFeeWaived(user);
        return dueRepository.findByUserOrderByYearDesc(user).stream()
                .map(d -> MembershipDueDto.from(d, mid, feeWaived))
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

    /** Outstanding balance on this year's dues — 0 if PAID/WAIVED or no due row exists yet. */
    @Transactional(readOnly = true)
    public BigDecimal outstandingBalance(User user) {
        return dueRepository.findByUserAndYear(user, AppClock.today().getYear())
                .filter(d -> d.getStatus() != DuesStatus.PAID && d.getStatus() != DuesStatus.WAIVED)
                .map(d -> ANNUAL_FEE.subtract(d.getPaidAmount() != null ? d.getPaidAmount() : BigDecimal.ZERO).max(BigDecimal.ZERO))
                .orElse(BigDecimal.ZERO);
    }

    /** The current year's still-owed due row, if any — used to surface recommended monthly
     * pace (see {@link MembershipDue#recommendedMonthlyAmount()}) alongside the outstanding
     * balance on "Pay My Balances". */
    @Transactional(readOnly = true)
    public Optional<MembershipDue> currentYearOutstandingDue(User user) {
        return dueRepository.findByUserAndYear(user, AppClock.today().getYear())
                .filter(d -> d.getStatus() != DuesStatus.PAID && d.getStatus() != DuesStatus.WAIVED);
    }

    // ── Membership activation helpers ─────────────────────────────────────────

    private void reactivateIfNeeded(User user, String reason) {
        if (!user.isActive()) {
            MemberStatus previousStatus = MemberStatusChangeService.statusOf(user.isActive(), user.isMembershipCeased());
            user.setActive(true);
            userRepository.save(user);
            statusChangeService.record(user, previousStatus, MemberStatus.ACTIVE,
                    MemberStatusReason.REINSTATED, null, reason);
            // Deactivation here already emails the member (sendDeactivationEmail below) -- until
            // now, reactivation via payment/waiver was completely silent by comparison.
            statusChangeService.notifyStatusChange(user, MemberStatus.ACTIVE, MemberStatusReason.REINSTATED, reason);
            log.info("Reactivated member {} — {}", user.getEmail(), reason);
        }
    }

    private void sendDeactivationEmail(User user) {
        String name = user.getFullName();
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
              <h2 style="color:#B91C1C">Membership Status: Inactive</h2>
              <p>Hi %s,</p>
              <p>Your Ushirika Welfare Organization membership has been set to <strong>Inactive</strong>
                 because your annual dues ($100) were not paid by the December 31st deadline.</p>
              <p>To restore your Active status, please log in to your member portal and submit
                 your payment. Your access to programs and benefits is paused until dues are settled.</p>
              <p>
                <a href="%s/portal/payments"
                   style="display:inline-block;background:#007834;color:#fff;padding:10px 22px;
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

    private String memberId(User user) {
        return profileRepository.findByUser(user)
                .map(MemberProfile::getMemberId)
                .orElse(null);
    }

    private boolean wasRegistrationFeeWaived(User user) {
        return applicationRepository.findByUser(user)
                .map(a -> a.isRegistrationFeeWaived())
                .orElse(false);
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
