package com.mdau.ushirika.module.payment.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.payment.dto.*;
import com.mdau.ushirika.module.payment.service.ContributionService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/contributions")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_FINANCIAL_ADMIN','ROLE_FINANCIAL_OFFICIAL','CAP_FINANCE_DUES')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Contributions — Admin", description = "Manage contribution plans and view all payments")
public class AdminContributionController {

    private final ContributionService contributionService;

    @GetMapping
    @Operation(summary = "List all confirmed contributions (paginated) — includes member identity")
    public ResponseEntity<ApiResponse<PagedResponse<AdminMemberContributionDto>>> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Contributions retrieved",
                contributionService.listAll(PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/plans")
    @Operation(summary = "List all contribution plans (including inactive)")
    public ResponseEntity<ApiResponse<List<ContributionPlanDto>>> plans() {
        return ResponseEntity.ok(ApiResponse.ok("Plans retrieved", contributionService.listAllPlans()));
    }

    @PostMapping("/plans")
    @Operation(summary = "Create a new contribution plan")
    public ResponseEntity<ApiResponse<ContributionPlanDto>> createPlan(@Valid @RequestBody ContributionPlanRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Plan created", contributionService.createPlan(req)));
    }

    @GetMapping("/plans/{id}")
    @Operation(summary = "Get a single contribution plan by ID")
    public ResponseEntity<ApiResponse<ContributionPlanDto>> getPlan(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Plan retrieved", contributionService.getPlan(id)));
    }

    @PutMapping("/plans/{id}")
    @Operation(summary = "Update a contribution plan (including features and badge)")
    public ResponseEntity<ApiResponse<ContributionPlanDto>> updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody ContributionPlanRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Plan updated", contributionService.updatePlan(id, req)));
    }

    @DeleteMapping("/plans/{id}")
    @Operation(summary = "Delete a contribution plan (permanent)")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable UUID id) {
        contributionService.deletePlan(id);
        return ResponseEntity.ok(ApiResponse.ok("Plan deleted"));
    }
}
