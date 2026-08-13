package com.mdau.ushirika.module.reconciliation.service;

import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.audit.repository.AuditLogRepository;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.dashboard.dto.FinanceDashboardDto.Balances;
import com.mdau.ushirika.module.dashboard.service.DashboardService;
import com.mdau.ushirika.module.reconciliation.dto.BankReconciliationDto;
import com.mdau.ushirika.module.reconciliation.dto.ReconciliationSummaryDto;
import com.mdau.ushirika.module.reconciliation.dto.ReconciliationSummaryDto.ScopeSummary;
import com.mdau.ushirika.module.reconciliation.dto.RecordReconciliationRequest;
import com.mdau.ushirika.module.reconciliation.entity.BankReconciliation;
import com.mdau.ushirika.module.reconciliation.repository.BankReconciliationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bank reconciliation -- lets an admin record what the bank physically shows and compares it
 * against the ledger-derived expected balance (Finance Visibility plan, Phase 8), org-wide and
 * per-program. Reuses DashboardService#getBalances() (Phase 4's all-time net-per-program figures)
 * as the "expected" side rather than recomputing ledger sums independently.
 */
@Service
@RequiredArgsConstructor
public class BankReconciliationService {

    private final BankReconciliationRepository reconciliationRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final DashboardService dashboardService;
    private final UserRepository userRepository;

    @Transactional
    public BankReconciliationDto record(RecordReconciliationRequest req) {
        User actor = currentUser();
        Balances balances = dashboardService.getBalances();
        BigDecimal expected = expectedFor(balances, req.scope());
        BigDecimal variance = req.physicalBalance().subtract(expected);

        BankReconciliation saved = reconciliationRepository.save(BankReconciliation.builder()
                .scope(req.scope())
                .physicalBalance(req.physicalBalance())
                .expectedBalance(expected)
                .variance(variance)
                .note(req.note())
                .recordedById(actor.getId())
                .recordedByName(actor.getFullName())
                .recordedByTitle(actor.getOfficialTitle() != null ? actor.getOfficialTitle().name() : null)
                .build());

        String scopeLabel = req.scope() == null ? "org-wide" : req.scope();
        auditLogService.log(actor, "RECONCILIATION_RECORDED", "BankReconciliation", saved.getId(),
                "Bank reconciliation recorded for " + scopeLabel + " by " + actor.getFullName()
                        + " -- physical $" + req.physicalBalance() + ", expected $" + expected
                        + ", variance $" + variance);

        return BankReconciliationDto.from(saved);
    }

    @Transactional(readOnly = true)
    public ReconciliationSummaryDto getSummary() {
        Balances balances = dashboardService.getBalances();
        List<String> scopes = auditLogRepository.findDistinctLedgerEntityTypes();

        ScopeSummary orgWide = summaryFor(null, balances.orgWideNet());
        List<ScopeSummary> byProgram = scopes.stream()
                .map(scope -> summaryFor(scope, balances.byProgram().getOrDefault(scope, BigDecimal.ZERO)))
                .toList();

        return new ReconciliationSummaryDto(orgWide, byProgram);
    }

    @Transactional(readOnly = true)
    public Page<BankReconciliationDto> listHistory(String scope, Pageable pageable) {
        return reconciliationRepository.findByScope(scope, pageable).map(BankReconciliationDto::from);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScopeSummary summaryFor(String scope, BigDecimal currentExpected) {
        BankReconciliationDto last = reconciliationRepository.findMostRecent(scope)
                .map(BankReconciliationDto::from)
                .orElse(null);
        return new ScopeSummary(scope, currentExpected, last);
    }

    private static BigDecimal expectedFor(Balances balances, String scope) {
        return scope == null ? balances.orgWideNet() : balances.byProgram().getOrDefault(scope, BigDecimal.ZERO);
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
