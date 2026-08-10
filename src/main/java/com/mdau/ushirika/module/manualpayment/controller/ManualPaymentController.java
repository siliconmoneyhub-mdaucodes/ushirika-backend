package com.mdau.ushirika.module.manualpayment.controller;

import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.manualpayment.dto.*;
import com.mdau.ushirika.module.manualpayment.enums.ManualPaymentCategory;
import com.mdau.ushirika.module.manualpayment.enums.ManualPaymentStatus;
import com.mdau.ushirika.module.manualpayment.service.ManualPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Accessible to: FINANCIAL_ADMIN, FINANCIAL_OFFICIAL, ADMIN (read-only), SUPERADMIN (read-only).
 * Write operations enforce role + permission checks inside the service.
 */
@RestController
@RequestMapping("/financial/manual-payments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_FINANCIAL_ADMIN','ROLE_FINANCIAL_OFFICIAL','ROLE_ADMIN','ROLE_SUPERADMIN','CAP_FINANCE_DUES')")
public class ManualPaymentController {

    private final ManualPaymentService service;

    /** Record a new manual (cash) payment. Service enforces who can record. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManualPaymentDto record(@Valid @RequestBody RecordManualPaymentRequest req) {
        return service.record(req);
    }

    /** List all manual payments with optional status/category filters. */
    @GetMapping
    public PagedResponse<ManualPaymentDto> list(
            @RequestParam(required = false) ManualPaymentStatus status,
            @RequestParam(required = false) ManualPaymentCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listAll(status, category,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    /** List payments that the current user recorded. */
    @GetMapping("/mine")
    public PagedResponse<ManualPaymentDto> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.myRecorded(PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @GetMapping("/{id}")
    public ManualPaymentDto getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    /** Approve a PENDING payment. Service enforces maker-checker and role. */
    @PostMapping("/{id}/approve")
    public ManualPaymentDto approve(@PathVariable UUID id,
                                    @Valid @RequestBody(required = false) ReviewManualPaymentRequest req) {
        return service.approve(id, req);
    }

    /** Reject a PENDING payment. Reason is mandatory. */
    @PostMapping("/{id}/reject")
    public ManualPaymentDto reject(@PathVariable UUID id,
                                   @Valid @RequestBody ReviewManualPaymentRequest req) {
        return service.reject(id, req);
    }

    /** Full chronological audit trail for a single payment. */
    @GetMapping("/{id}/audit-log")
    public List<AuditLogDto> auditLog(@PathVariable UUID id) {
        return service.getAuditLog(id);
    }
}
