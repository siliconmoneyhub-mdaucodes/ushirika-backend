package com.mdau.ushirika.module.dues.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.dues.dto.*;
import com.mdau.ushirika.module.dues.enums.DuesPaymentStatus;
import com.mdau.ushirika.module.dues.enums.DuesStatus;
import com.mdau.ushirika.module.dues.service.MembershipDuesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/dues")
@RequiredArgsConstructor
public class AdminDuesController {

    private final MembershipDuesService duesService;

    // ── Dues records ──────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<MembershipDueDto>>> listAll(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        DuesStatus ds = status != null ? DuesStatus.valueOf(status.toUpperCase()) : null;
        return ResponseEntity.ok(ApiResponse.ok("Dues fetched",
                duesService.listAll(year, ds, PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/member/{userId}")
    public ResponseEntity<ApiResponse<List<MembershipDueDto>>> getMemberDues(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok("Member dues fetched",
                duesService.getMemberDuesHistory(userId)));
    }

    /** Permanent waive -- SUPERADMIN only. The client's stated policy: a full write-off of
     *  owed dues is a superadmin-level financial decision, not a routine admin action. Admins
     *  (and financial roles) still have /grace-period below for temporary relief. */
    @PatchMapping("/{id}/waive")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<MembershipDueDto>> waive(
            @PathVariable UUID id,
            @RequestBody(required = false) WaiveDuesRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Dues waived", duesService.waiveDues(id, req)));
    }

    /** Temporary relief, open to any dues-capable role -- resets the due to PENDING with a
     *  fresh 7-day deadline and reactivates the member, without forgiving the debt outright. */
    @PatchMapping("/{id}/grace-period")
    public ResponseEntity<ApiResponse<MembershipDueDto>> grantGracePeriod(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("7-day grace period granted", duesService.grantGracePeriod(id, 7)));
    }

    @PostMapping("/assess-overdue")
    public ResponseEntity<ApiResponse<String>> assessOverdue() {
        int count = duesService.assessOverdue();
        return ResponseEntity.ok(ApiResponse.ok("Overdue assessment complete", count + " records updated"));
    }

    // ── Installments ──────────────────────────────────────────────────────────

    @GetMapping("/installments")
    public ResponseEntity<ApiResponse<PagedResponse<DuesPaymentDto>>> listAllInstallments(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        DuesPaymentStatus ds = status != null ? DuesPaymentStatus.valueOf(status.toUpperCase()) : null;
        return ResponseEntity.ok(ApiResponse.ok("Installments fetched",
                duesService.listAllInstallments(ds,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{duesId}/installments")
    public ResponseEntity<ApiResponse<List<DuesPaymentDto>>> getInstallments(@PathVariable UUID duesId) {
        return ResponseEntity.ok(ApiResponse.ok("Installments fetched",
                duesService.getInstallments(duesId)));
    }

}
