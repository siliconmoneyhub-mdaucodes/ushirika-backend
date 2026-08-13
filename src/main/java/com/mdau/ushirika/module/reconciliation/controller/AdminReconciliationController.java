package com.mdau.ushirika.module.reconciliation.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.reconciliation.dto.BankReconciliationDto;
import com.mdau.ushirika.module.reconciliation.dto.ReconciliationSummaryDto;
import com.mdau.ushirika.module.reconciliation.dto.RecordReconciliationRequest;
import com.mdau.ushirika.module.reconciliation.service.BankReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Bank reconciliation -- org-wide and per-program expected-vs-physical balance checks
 * (Finance Visibility plan, Phase 8). Same finance-officer gate as Money Flow.
 */
@RestController
@RequestMapping("/admin/reconciliation")
@PreAuthorize("hasAnyAuthority('ROLE_FINANCIAL_ADMIN','ROLE_ADMIN','ROLE_SUPERADMIN','CAP_FINANCE_ADVANCED')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Bank Reconciliation", description = "Expected-vs-physical balance checks, org-wide and per-program")
public class AdminReconciliationController {

    private final BankReconciliationService reconciliationService;

    @GetMapping("/summary")
    @Operation(summary = "Current expected balance (org-wide and per program) plus each scope's most recent physical check")
    public ResponseEntity<ApiResponse<ReconciliationSummaryDto>> summary() {
        return ResponseEntity.ok(ApiResponse.ok(reconciliationService.getSummary()));
    }

    @PostMapping
    @Operation(summary = "Record a physical balance check for a scope (org-wide if scope is omitted)")
    public ResponseEntity<ApiResponse<BankReconciliationDto>> record(@Valid @RequestBody RecordReconciliationRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Reconciliation recorded", reconciliationService.record(req)));
    }

    @GetMapping("/history")
    @Operation(summary = "Paginated reconciliation history, optionally filtered to one scope")
    public ResponseEntity<ApiResponse<PagedResponse<BankReconciliationDto>>> history(
            @RequestParam(required = false) String scope,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = reconciliationService.listHistory(scope,
                PageRequest.of(page, size, Sort.by("recordedAt").descending()));
        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.of(result)));
    }
}
