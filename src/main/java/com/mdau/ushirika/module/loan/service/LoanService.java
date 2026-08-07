package com.mdau.ushirika.module.loan.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.loan.dto.*;
import com.mdau.ushirika.module.loan.entity.*;
import com.mdau.ushirika.module.loan.enums.*;
import com.mdau.ushirika.module.loan.repository.*;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanApplicationRepository loanRepo;
    private final LoanGuarantorRepository guarantorRepo;
    private final LoanInstallmentRepository installmentRepo;
    private final MemberProfileRepository profileRepo;
    private final UserRepository userRepo;
    private final AuditLogService auditLogService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private String memberId(User user) {
        return profileRepo.findByUser(user)
                .map(MemberProfile::getMemberId)
                .orElse(null);
    }

    private LoanApplication findLoan(UUID id) {
        return loanRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + id));
    }

    private LoanInstallment findInstallment(UUID id) {
        return installmentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Installment not found: " + id));
    }

    private String generateRef() {
        return "LOAN-" + System.currentTimeMillis();
    }

    private LoanApplicationDto toFullDto(LoanApplication loan) {
        List<LoanGuarantorDto> guarantorDtos = guarantorRepo.findByLoan(loan).stream()
                .map(g -> LoanGuarantorDto.from(g, memberId(g.getGuarantorUser())))
                .toList();
        List<LoanInstallmentDto> installmentDtos = installmentRepo
                .findByLoanOrderByInstallmentNumber(loan).stream()
                .map(LoanInstallmentDto::from)
                .toList();
        return LoanApplicationDto.from(loan, memberId(loan.getUser()), guarantorDtos, installmentDtos);
    }

    private LoanApplicationDto toSummaryDto(LoanApplication loan) {
        long total = installmentRepo.countByLoanAndStatusNotIn(loan, List.of());
        long pending = installmentRepo.countByLoanAndStatusNotIn(
                loan, List.of(InstallmentStatus.PAID, InstallmentStatus.WAIVED));
        return LoanApplicationDto.summary(loan, memberId(loan.getUser()),
                (int) total, (int) (total - pending));
    }

    // ── Member: apply ─────────────────────────────────────────────────────────

    @Transactional
    public LoanApplicationDto applyForLoan(ApplyForLoanRequest req) {
        User user = currentUser();

        List<LoanStatus> activeStatuses = List.of(
                LoanStatus.PENDING, LoanStatus.UNDER_REVIEW, LoanStatus.APPROVED,
                LoanStatus.DISBURSED, LoanStatus.REPAYING);
        if (loanRepo.existsByUserAndStatusIn(user, activeStatuses)) {
            throw new ConflictException("You already have an active loan application or loan.");
        }

        LoanApplication loan = LoanApplication.builder()
                .user(user)
                .referenceNumber(generateRef())
                .requestedAmount(req.requestedAmount())
                .purpose(req.purpose())
                .termMonths(req.termMonths())
                .build();
        loanRepo.save(loan);

        List<LoanGuarantor> guarantors = new ArrayList<>();
        for (UUID gId : req.guarantorUserIds()) {
            if (gId.equals(user.getId())) {
                throw new BadRequestException("You cannot guarantee your own loan.");
            }
            User guarantorUser = userRepo.findById(gId)
                    .orElseThrow(() -> new ResourceNotFoundException("Guarantor user not found: " + gId));
            if (guarantorRepo.existsByLoanAndGuarantorUser(loan, guarantorUser)) {
                continue; // deduplicate
            }
            guarantors.add(LoanGuarantor.builder()
                    .loan(loan)
                    .guarantorUser(guarantorUser)
                    .build());
        }
        guarantorRepo.saveAll(guarantors);

        auditLogService.log(user, "LOAN_APPLIED", "LoanApplication", loan.getId(),
                "Loan application " + loan.getReferenceNumber() + " for $" + loan.getRequestedAmount()
                        + " submitted by " + user.getFullName());
        return toFullDto(loan);
    }

    @Transactional(readOnly = true)
    public List<LoanApplicationDto> getMyLoans() {
        User user = currentUser();
        return loanRepo.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public LoanApplicationDto getMyLoanById(UUID id) {
        User user = currentUser();
        LoanApplication loan = findLoan(id);
        if (!loan.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Loan does not belong to you.");
        }
        return toFullDto(loan);
    }

    // ── Member: guarantor requests ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LoanGuarantorDto> getMyGuarantorRequests() {
        User user = currentUser();
        return guarantorRepo.findByGuarantorUserOrderByCreatedAtDesc(user).stream()
                .map(g -> LoanGuarantorDto.from(g, memberId(g.getGuarantorUser())))
                .toList();
    }

    @Transactional
    public LoanGuarantorDto respondToGuarantorRequest(UUID guarantorId, GuarantorResponseRequest req) {
        User user = currentUser();
        LoanGuarantor g = guarantorRepo.findById(guarantorId)
                .orElseThrow(() -> new ResourceNotFoundException("Guarantor record not found"));
        if (!g.getGuarantorUser().getId().equals(user.getId())) {
            throw new BadRequestException("This guarantor request does not belong to you.");
        }
        if (g.getStatus() != GuarantorStatus.PENDING) {
            throw new BadRequestException("This request has already been responded to.");
        }
        g.setStatus("ACCEPTED".equalsIgnoreCase(req.decision())
                ? GuarantorStatus.ACCEPTED : GuarantorStatus.DECLINED);
        g.setRespondedAt(LocalDateTime.now());
        g.setNotes(req.notes());
        guarantorRepo.save(g);
        auditLogService.log(user, "GUARANTOR_" + g.getStatus().name(), "LoanGuarantor", g.getId(),
                user.getFullName() + " " + g.getStatus().name().toLowerCase()
                        + " guarantor request for loan " + g.getLoan().getReferenceNumber());
        return LoanGuarantorDto.from(g, memberId(g.getGuarantorUser()));
    }

    // ── Admin: list / get ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<LoanApplicationDto> listLoans(LoanStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var pg = status == null
                ? loanRepo.findAllByOrderByCreatedAtDesc(pageable)
                : loanRepo.findAllByStatusOrderByCreatedAtDesc(status, pageable);
        return PagedResponse.of(pg.map(this::toSummaryDto));
    }

    @Transactional(readOnly = true)
    public LoanApplicationDto getLoanById(UUID id) {
        return toFullDto(findLoan(id));
    }

    @Transactional(readOnly = true)
    public List<LoanApplicationDto> getLoansByUser(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return loanRepo.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toSummaryDto)
                .toList();
    }

    // ── Admin: review ─────────────────────────────────────────────────────────

    @Transactional
    public LoanApplicationDto reviewLoan(UUID id, ReviewLoanRequest req) {
        LoanApplication loan = findLoan(id);
        if (loan.getStatus() != LoanStatus.PENDING && loan.getStatus() != LoanStatus.UNDER_REVIEW) {
            throw new BadRequestException("Only PENDING or UNDER_REVIEW loans can be reviewed.");
        }

        switch (req.decision().toUpperCase()) {
            case "APPROVED" -> {
                if (req.approvedAmount() == null) {
                    throw new BadRequestException("approvedAmount is required when approving.");
                }
                if (req.interestRate() == null) {
                    throw new BadRequestException("interestRate is required when approving.");
                }
                loan.setApprovedAmount(req.approvedAmount());
                loan.setInterestRate(req.interestRate());
                loan.setStatus(LoanStatus.APPROVED);
            }
            case "REJECTED" -> {
                loan.setRejectionReason(req.rejectionReason());
                loan.setStatus(LoanStatus.REJECTED);
            }
            case "UNDER_REVIEW" -> loan.setStatus(LoanStatus.UNDER_REVIEW);
            default -> throw new BadRequestException("Invalid decision. Use APPROVED, REJECTED, or UNDER_REVIEW.");
        }
        loan.setAdminNotes(req.adminNotes());
        loanRepo.save(loan);
        User admin = currentUser();
        auditLogService.log(admin, "LOAN_" + loan.getStatus().name(), "LoanApplication", loan.getId(),
                "Loan " + loan.getReferenceNumber() + " for " + loan.getUser().getFullName()
                        + " marked " + loan.getStatus() + " by " + admin.getFullName());
        return toFullDto(loan);
    }

    // ── Admin: disburse ───────────────────────────────────────────────────────

    @Transactional
    public LoanApplicationDto disburseLoan(UUID id, DisburseLoanRequest req) {
        LoanApplication loan = findLoan(id);
        if (loan.getStatus() != LoanStatus.APPROVED) {
            throw new BadRequestException("Only APPROVED loans can be disbursed.");
        }

        LocalDate today = LocalDate.now();
        BigDecimal principal = loan.getApprovedAmount();
        BigDecimal rate = loan.getInterestRate();
        BigDecimal totalRepayable = principal.add(principal.multiply(rate))
                .setScale(2, RoundingMode.HALF_UP);
        int months = loan.getTermMonths();

        loan.setDisbursedAt(today);
        loan.setDueDate(today.plusMonths(months));
        loan.setTotalRepayable(totalRepayable);
        loan.setDisbursementMethod(req.disbursementMethod());
        loan.setDisbursementReference(req.disbursementReference());
        if (req.notes() != null) loan.setAdminNotes(req.notes());
        loan.setStatus(LoanStatus.DISBURSED);
        loanRepo.save(loan);

        // Generate repayment schedule
        BigDecimal principalPerMonth = principal.divide(
                BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        BigDecimal interestPerMonth = principal.multiply(rate)
                .divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        BigDecimal monthlyTotal = principalPerMonth.add(interestPerMonth);

        List<LoanInstallment> installments = new ArrayList<>();
        BigDecimal runningTotal = BigDecimal.ZERO;

        for (int i = 1; i <= months; i++) {
            BigDecimal p = principalPerMonth;
            BigDecimal interest = interestPerMonth;

            // Last installment absorbs rounding difference
            if (i == months) {
                BigDecimal remaining = totalRepayable.subtract(runningTotal);
                BigDecimal pRemaining = principal.subtract(
                        principalPerMonth.multiply(BigDecimal.valueOf(months - 1)));
                p = pRemaining;
                interest = remaining.subtract(pRemaining);
            }

            BigDecimal total = p.add(interest);
            runningTotal = runningTotal.add(total);

            installments.add(LoanInstallment.builder()
                    .loan(loan)
                    .installmentNumber(i)
                    .dueDate(today.plusMonths(i))
                    .principal(p)
                    .interest(interest)
                    .totalDue(total)
                    .build());
        }
        installmentRepo.saveAll(installments);

        User admin = currentUser();
        auditLogService.log(admin, "LOAN_DISBURSED", "LoanApplication", loan.getId(),
                "Loan " + loan.getReferenceNumber() + " ($" + principal + ") disbursed to "
                        + loan.getUser().getFullName() + " by " + admin.getFullName());
        return toFullDto(loan);
    }

    // ── Admin: record repayment ───────────────────────────────────────────────

    @Transactional
    public LoanInstallmentDto recordRepayment(RecordRepaymentRequest req) {
        LoanInstallment inst = findInstallment(req.installmentId());
        LoanApplication loan = inst.getLoan();

        if (inst.getStatus() == InstallmentStatus.PAID || inst.getStatus() == InstallmentStatus.WAIVED) {
            throw new BadRequestException("This installment is already " + inst.getStatus() + ".");
        }

        BigDecimal newAmountPaid = inst.getAmountPaid().add(req.amountPaid());
        inst.setAmountPaid(newAmountPaid);
        inst.setPaymentMethod(req.paymentMethod());
        inst.setPaymentReference(req.paymentReference());
        inst.setNotes(req.notes());

        if (newAmountPaid.compareTo(inst.getTotalDue()) >= 0) {
            inst.setStatus(InstallmentStatus.PAID);
            inst.setPaidAt(LocalDateTime.now());
        } else {
            inst.setStatus(InstallmentStatus.PARTIAL);
        }
        installmentRepo.save(inst);

        // Update loan totals
        BigDecimal newTotalPaid = installmentRepo.sumAmountPaidByLoan(loan);
        loan.setTotalPaid(newTotalPaid);

        if (loan.getStatus() == LoanStatus.DISBURSED) {
            loan.setStatus(LoanStatus.REPAYING);
        }

        // Auto-complete when all installments resolved
        long unresolved = installmentRepo.countByLoanAndStatusNotIn(
                loan, List.of(InstallmentStatus.PAID, InstallmentStatus.WAIVED));
        if (unresolved == 0) {
            loan.setStatus(LoanStatus.COMPLETED);
        }
        loanRepo.save(loan);

        User admin = currentUser();
        auditLogService.log(admin, "LOAN_REPAYMENT_RECORDED", "LoanInstallment", inst.getId(),
                "Repayment of $" + req.amountPaid() + " recorded on installment #" + inst.getInstallmentNumber()
                        + " for loan " + loan.getReferenceNumber() + " by " + admin.getFullName());
        return LoanInstallmentDto.from(inst);
    }

    // ── Admin: waive installment ──────────────────────────────────────────────

    @Transactional
    public LoanInstallmentDto waiveInstallment(UUID installmentId, String reason) {
        LoanInstallment inst = findInstallment(installmentId);
        if (inst.getStatus() == InstallmentStatus.PAID) {
            throw new BadRequestException("Cannot waive an already-paid installment.");
        }
        inst.setStatus(InstallmentStatus.WAIVED);
        inst.setNotes(reason);
        inst.setPaidAt(LocalDateTime.now());
        installmentRepo.save(inst);

        LoanApplication loan = inst.getLoan();
        long unresolved = installmentRepo.countByLoanAndStatusNotIn(
                loan, List.of(InstallmentStatus.PAID, InstallmentStatus.WAIVED));
        if (unresolved == 0) {
            loan.setStatus(LoanStatus.COMPLETED);
            loanRepo.save(loan);
        }
        User admin = currentUser();
        auditLogService.log(admin, "LOAN_INSTALLMENT_WAIVED", "LoanInstallment", inst.getId(),
                "Installment #" + inst.getInstallmentNumber() + " ($" + inst.getTotalDue() + ") for loan "
                        + loan.getReferenceNumber() + " waived by " + admin.getFullName()
                        + (reason != null ? " (" + reason + ")" : ""));
        return LoanInstallmentDto.from(inst);
    }

    // ── Admin: mark defaulted ─────────────────────────────────────────────────

    @Transactional
    public LoanApplicationDto markDefaulted(UUID id, String notes) {
        LoanApplication loan = findLoan(id);
        if (loan.getStatus() != LoanStatus.REPAYING && loan.getStatus() != LoanStatus.DISBURSED) {
            throw new BadRequestException("Only DISBURSED or REPAYING loans can be marked as defaulted.");
        }
        loan.setStatus(LoanStatus.DEFAULTED);
        loan.setDefaultedAt(LocalDateTime.now());
        if (notes != null) loan.setAdminNotes(notes);
        loanRepo.save(loan);
        User admin = currentUser();
        auditLogService.log(admin, "LOAN_DEFAULTED", "LoanApplication", loan.getId(),
                "Loan " + loan.getReferenceNumber() + " for " + loan.getUser().getFullName()
                        + " marked defaulted by " + admin.getFullName());
        return toFullDto(loan);
    }
}
