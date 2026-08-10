package com.mdau.ushirika.module.scholarship.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.scholarship.dto.*;
import com.mdau.ushirika.module.scholarship.enums.ScholarshipApplicationStatus;
import com.mdau.ushirika.module.scholarship.enums.ScholarshipProgramStatus;
import com.mdau.ushirika.module.scholarship.service.ScholarshipService;
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

import java.util.UUID;

@RestController
@RequestMapping("/admin/scholarships")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','CAP_CONTENT')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Scholarships — Admin", description = "Manage scholarship programs and review applications")
public class AdminScholarshipController {

    private final ScholarshipService scholarshipService;

    // ─── Programs

    @GetMapping("/programs")
    @Operation(summary = "List all scholarship programs (all statuses, paginated)")
    public ResponseEntity<ApiResponse<PagedResponse<ScholarshipProgramDto>>> listPrograms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Programs retrieved",
                scholarshipService.listAllPrograms(
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @PostMapping("/programs")
    @Operation(summary = "Create a new scholarship program")
    public ResponseEntity<ApiResponse<ScholarshipProgramDto>> createProgram(
            @Valid @RequestBody ScholarshipProgramRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Program created", scholarshipService.createProgram(req)));
    }

    @PutMapping("/programs/{id}")
    @Operation(summary = "Update scholarship program details")
    public ResponseEntity<ApiResponse<ScholarshipProgramDto>> updateProgram(
            @PathVariable UUID id, @Valid @RequestBody ScholarshipProgramRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Program updated", scholarshipService.updateProgram(id, req)));
    }

    @PatchMapping("/programs/{id}/status")
    @Operation(summary = "Change program status (DRAFT → OPEN → CLOSED → COMPLETED)")
    public ResponseEntity<ApiResponse<ScholarshipProgramDto>> updateStatus(
            @PathVariable UUID id, @RequestParam ScholarshipProgramStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Program status updated",
                scholarshipService.updateProgramStatus(id, status)));
    }

    // ─── Applications

    @GetMapping("/applications")
    @Operation(summary = "List all scholarship applications, optionally filtered by status")
    public ResponseEntity<ApiResponse<PagedResponse<AdminScholarshipApplicationDto>>> listApplications(
            @RequestParam(required = false) ScholarshipApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Applications retrieved",
                scholarshipService.listApplications(status,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()),
                        isSuperAdmin(auth))));
    }

    @GetMapping("/applications/{id}")
    @Operation(summary = "Get full detail of a scholarship application")
    public ResponseEntity<ApiResponse<AdminScholarshipApplicationDto>> getApplication(
            @PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Application retrieved",
                scholarshipService.getApplication(id, isSuperAdmin(auth))));
    }

    @PostMapping("/applications/{id}/review")
    @Operation(summary = "Cast your APPROVED or REJECTED vote on a scholarship application")
    public ResponseEntity<ApiResponse<AdminScholarshipApplicationDto>> review(
            @PathVariable UUID id,
            @Valid @RequestBody ScholarshipReviewRequest req,
            Authentication auth
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Vote recorded",
                scholarshipService.review(id, req, isSuperAdmin(auth))));
    }

    @PostMapping("/applications/{id}/award")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Operation(summary = "Record scholarship award disbursement for an APPROVED application (SUPERADMIN only)")
    public ResponseEntity<ApiResponse<AdminScholarshipApplicationDto>> award(
            @PathVariable UUID id, @Valid @RequestBody ScholarshipAwardRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Award recorded", scholarshipService.recordAward(id, req)));
    }

    // ─── Public inquiries

    @GetMapping("/inquiries")
    @Operation(summary = "List public scholarship inquiries")
    public ResponseEntity<ApiResponse<PagedResponse<PublicInquiryDto>>> listInquiries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Inquiries retrieved",
                scholarshipService.listInquiries(
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    private boolean isSuperAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
    }
}
