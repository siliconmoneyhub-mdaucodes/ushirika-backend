package com.mdau.ushirika.module.audit.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.audit.dto.AuditLogDto;
import com.mdau.ushirika.module.audit.enums.LedgerDirection;
import com.mdau.ushirika.module.audit.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Money In & Out -- a filterable ledger view built directly from AuditLog rows that a money-moving
 * action tagged with amount/direction (Finance Visibility plan, Phase 2). Raw money movement is a
 * finance-officer concern, not the general audit trail (CAP_AUDIT_LOG stays scoped to that), so this
 * gets its own CAP_FINANCE_ADVANCED gate.
 */
@RestController
@RequestMapping("/admin/money-flow")
@PreAuthorize("hasAnyAuthority('ROLE_FINANCIAL_ADMIN','ROLE_ADMIN','ROLE_SUPERADMIN','CAP_FINANCE_ADVANCED')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Money In & Out", description = "Filterable money-movement ledger derived from the audit trail")
public class AdminMoneyFlowController {

    /** Sentinel lower bound standing in for "no from filter" -- predates the organization, so it
     * behaves as an open lower bound while keeping the ledger queries' date parameters non-null
     * (see AuditLogRepository.findLedgerEntries' Javadoc for why null breaks them on Postgres). */
    private static final LocalDateTime EPOCH = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @Operation(summary = "List ledger entries (money-moving audit rows only), optionally filtered by program/direction/date range")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) LedgerDirection direction,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Page<AuditLogDto> result = auditLogRepository
                .findLedgerEntries(entityType, direction,
                        from != null ? from : EPOCH,
                        to != null ? to : LocalDateTime.now(),
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(AuditLogDto::from);

        return ResponseEntity.ok(ApiResponse.ok("Money flow retrieved", PagedResponse.of(result)));
    }

    @GetMapping("/programs")
    @Operation(summary = "Every distinct program (entityType) that has ever appeared on a ledger row")
    public ResponseEntity<ApiResponse<List<String>>> listPrograms() {
        return ResponseEntity.ok(ApiResponse.ok(auditLogRepository.findDistinctLedgerEntityTypes()));
    }

    @GetMapping("/totals")
    @Operation(summary = "Per-program in/out/net totals for a date range, plus the org-wide grand total")
    public ResponseEntity<ApiResponse<Map<String, Object>>> totals(
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to
    ) {
        List<Object[]> rows = auditLogRepository.sumLedgerByEntityTypeAndDirection(
                from != null ? from : EPOCH,
                to != null ? to : LocalDateTime.now());

        Map<String, Map<String, BigDecimal>> byProgram = new java.util.LinkedHashMap<>();
        BigDecimal grandIn = BigDecimal.ZERO;
        BigDecimal grandOut = BigDecimal.ZERO;

        for (Object[] row : rows) {
            String entityType = (String) row[0];
            LedgerDirection direction = (LedgerDirection) row[1];
            BigDecimal amount = (BigDecimal) row[2];

            Map<String, BigDecimal> programTotals = byProgram.computeIfAbsent(entityType,
                    k -> new java.util.HashMap<>(Map.of("in", BigDecimal.ZERO, "out", BigDecimal.ZERO)));
            if (direction == LedgerDirection.IN) {
                programTotals.put("in", programTotals.get("in").add(amount));
                grandIn = grandIn.add(amount);
            } else {
                programTotals.put("out", programTotals.get("out").add(amount));
                grandOut = grandOut.add(amount);
            }
        }

        byProgram.forEach((k, v) -> v.put("net", v.get("in").subtract(v.get("out"))));

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "byProgram", byProgram,
                "grandIn", grandIn,
                "grandOut", grandOut,
                "grandNet", grandIn.subtract(grandOut)
        )));
    }
}
