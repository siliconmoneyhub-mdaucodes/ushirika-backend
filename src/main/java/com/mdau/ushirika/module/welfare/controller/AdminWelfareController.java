package com.mdau.ushirika.module.welfare.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.welfare.dto.*;
import com.mdau.ushirika.module.welfare.enums.WelfareRequestStatus;
import com.mdau.ushirika.module.welfare.service.WelfareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/welfare")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','CAP_FINANCE_ADVANCED')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Welfare — Admin", description = "Review welfare requests, manage categories, record disbursements")
public class AdminWelfareController {

    private final WelfareService welfareService;

    // ─── Categories

    @GetMapping("/categories")
    @Operation(summary = "List all welfare categories (including inactive)")
    public ResponseEntity<ApiResponse<List<WelfareCategoryDto>>> listCategories() {
        return ResponseEntity.ok(ApiResponse.ok("Categories retrieved", welfareService.listAllCategories()));
    }

    @PostMapping("/categories")
    @Operation(summary = "Create a new welfare category")
    public ResponseEntity<ApiResponse<WelfareCategoryDto>> createCategory(
            @Valid @RequestBody WelfareCategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Category created", welfareService.createCategory(req)));
    }

    @PutMapping("/categories/{id}")
    @Operation(summary = "Update a welfare category")
    public ResponseEntity<ApiResponse<WelfareCategoryDto>> updateCategory(
            @PathVariable UUID id, @Valid @RequestBody WelfareCategoryRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Category updated", welfareService.updateCategory(id, req)));
    }

    // ─── Requests

    @GetMapping("/requests")
    @Operation(summary = "List all welfare requests, optionally filtered by status")
    public ResponseEntity<ApiResponse<PagedResponse<AdminWelfareRequestDto>>> listRequests(
            @RequestParam(required = false) WelfareRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth
    ) {
        boolean isSuperAdmin = isSuperAdmin(auth);
        return ResponseEntity.ok(ApiResponse.ok("Requests retrieved",
                welfareService.listRequests(status,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()), isSuperAdmin)));
    }

    @GetMapping("/requests/{id}")
    @Operation(summary = "Get full detail of a welfare request")
    public ResponseEntity<ApiResponse<AdminWelfareRequestDto>> getRequest(
            @PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Request retrieved",
                welfareService.getRequest(id, isSuperAdmin(auth))));
    }

    @PostMapping("/requests/{id}/review")
    @Operation(summary = "Cast your APPROVED or REJECTED vote on a welfare request")
    public ResponseEntity<ApiResponse<AdminWelfareRequestDto>> review(
            @PathVariable UUID id,
            @Valid @RequestBody WelfareReviewRequest req,
            Authentication auth
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Vote recorded",
                welfareService.review(id, req, isSuperAdmin(auth))));
    }

    @PostMapping("/requests/{id}/disburse")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Operation(summary = "Record disbursement for an APPROVED welfare request (SUPERADMIN only)")
    public ResponseEntity<ApiResponse<AdminWelfareRequestDto>> disburse(
            @PathVariable UUID id,
            @Valid @RequestBody DisbursementRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Disbursement recorded",
                welfareService.recordDisbursement(id, req)));
    }

    private boolean isSuperAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
    }
}
