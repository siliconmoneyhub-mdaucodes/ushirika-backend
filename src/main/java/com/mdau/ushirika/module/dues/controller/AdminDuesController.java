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

    @PatchMapping("/{id}/waive")
    public ResponseEntity<ApiResponse<MembershipDueDto>> waive(
            @PathVariable UUID id,
            @RequestBody(required = false) WaiveDuesRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Dues waived", duesService.waiveDues(id, req)));
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
