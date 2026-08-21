package com.mdau.ushirika.module.benevolence.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.benevolence.dto.*;
import com.mdau.ushirika.module.benevolence.enums.ClaimStatus;
import com.mdau.ushirika.module.benevolence.enums.EnrollmentStatus;
import com.mdau.ushirika.module.benevolence.service.BenevolenceClaimCategoryService;
import com.mdau.ushirika.module.benevolence.service.BenevolenceClaimService;
import com.mdau.ushirika.module.benevolence.service.BenevolenceEnrollmentService;
import com.mdau.ushirika.module.benevolence.util.BenevolenceSeedCsvParser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin/benevolence")
@RequiredArgsConstructor
public class AdminBenevolenceController {

    private final BenevolenceEnrollmentService enrollmentService;
    private final BenevolenceClaimService claimService;
    private final BenevolenceClaimCategoryService categoryService;

    // ── Seed pre-launch enrollments ──────────────────────────────────────────
    // For the ~60 members who were already active Benevolence participants before this platform
    // existed. See BenevolenceEnrollmentService#seedEnrollment for what this actually does.

    public record BulkSeedResultRow(int rowNumber, String memberEmail, boolean success, String message) {}
    public record BulkSeedSummary(int total, int succeeded, int failed, List<BulkSeedResultRow> rows) {}

    @PostMapping("/seed-enrollment")
    @Operation(summary = "Seed one pre-launch Benevolence enrollment with locked beneficiaries")
    public ResponseEntity<ApiResponse<BenevolenceEnrollmentDto>> seedEnrollment(
            @Valid @RequestBody SeedBenevolenceEnrollmentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Enrollment seeded", enrollmentService.seedEnrollment(req)));
    }

    @PostMapping(value = "/seed-enrollment/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk-seed pre-launch Benevolence enrollments from a CSV -- each row is independent, one bad row does not block the rest")
    public ResponseEntity<ApiResponse<BulkSeedSummary>> seedEnrollmentBulk(
            @RequestParam("file") MultipartFile file) throws java.io.IOException {
        List<BenevolenceSeedCsvParser.ParsedRow> parsed = BenevolenceSeedCsvParser.parse(file.getBytes());
        List<BulkSeedResultRow> results = new ArrayList<>();
        int succeeded = 0;

        for (BenevolenceSeedCsvParser.ParsedRow row : parsed) {
            if (row.parseError() != null) {
                results.add(new BulkSeedResultRow(row.rowNumber(), row.memberEmail(), false, row.parseError()));
                continue;
            }
            try {
                enrollmentService.seedEnrollment(row.request());
                results.add(new BulkSeedResultRow(row.rowNumber(), row.memberEmail(), true, "Seeded"));
                succeeded++;
            } catch (Exception e) {
                log.warn("Bulk Benevolence seed failed for row {} ({}): {}", row.rowNumber(), row.memberEmail(), e.getMessage());
                results.add(new BulkSeedResultRow(row.rowNumber(), row.memberEmail(), false, e.getMessage()));
            }
        }

        BulkSeedSummary summary = new BulkSeedSummary(parsed.size(), succeeded, parsed.size() - succeeded, results);
        return ResponseEntity.ok(ApiResponse.ok(
                succeeded + " of " + parsed.size() + " enrollment(s) seeded successfully", summary));
    }

    @GetMapping("/seed-enrollment/template")
    @Operation(summary = "Download a blank CSV template for bulk-seeding Benevolence enrollments")
    public ResponseEntity<byte[]> seedEnrollmentTemplate() {
        byte[] csv = BenevolenceSeedCsvParser.getTemplateCsv().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"benevolence-seed-template.csv\"")
                .body(csv);
    }

    // ── Join Requests ─────────────────────────────────────────────────────────

    @GetMapping("/join-requests")
    public ResponseEntity<ApiResponse<java.util.List<BenevolenceJoinRequestDto>>> listJoinRequests(
            @RequestParam(required = false) com.mdau.ushirika.module.benevolence.enums.BenevolenceJoinRequestStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(enrollmentService.listJoinRequests(status)));
    }

    @PostMapping("/join-requests/{id}/send-form")
    public ResponseEntity<ApiResponse<BenevolenceJoinRequestDto>> sendJoinForm(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) DecideBenevolenceJoinRequest req) {
        String adminNotes = req != null ? req.adminNotes() : null;
        return ResponseEntity.ok(ApiResponse.ok("Enrollment form sent",
                enrollmentService.sendForm(id, adminNotes)));
    }

    @PostMapping("/join-requests/{id}/approve")
    public ResponseEntity<ApiResponse<BenevolenceJoinRequestDto>> approveJoinRequest(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) DecideBenevolenceJoinRequest req) {
        String adminNotes = req != null ? req.adminNotes() : null;
        return ResponseEntity.ok(ApiResponse.ok("Join request approved",
                enrollmentService.approveJoinRequest(id, adminNotes)));
    }

    @PostMapping("/join-requests/{id}/reject")
    public ResponseEntity<ApiResponse<BenevolenceJoinRequestDto>> rejectJoinRequest(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) DecideBenevolenceJoinRequest req) {
        String adminNotes = req != null ? req.adminNotes() : null;
        return ResponseEntity.ok(ApiResponse.ok("Join request rejected",
                enrollmentService.rejectJoinRequest(id, adminNotes)));
    }

    // ── Enrollments ───────────────────────────────────────────────────────────

    @GetMapping("/enrollments")
    public ResponseEntity<ApiResponse<PagedResponse<BenevolenceEnrollmentDto>>> listEnrollments(
            @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("enrolledAt").descending());
        return ResponseEntity.ok(ApiResponse.ok(enrollmentService.listEnrollments(status, pageable)));
    }

    @GetMapping("/enrollments/{id}")
    public ResponseEntity<ApiResponse<BenevolenceEnrollmentDto>> getEnrollment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(enrollmentService.getEnrollmentById(id)));
    }

    @GetMapping("/enrollments/by-user/{userId}")
    public ResponseEntity<ApiResponse<BenevolenceEnrollmentDto>> getEnrollmentByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(enrollmentService.getEnrollmentByUser(userId)));
    }

    @PostMapping("/enrollments/{id}/beneficiaries")
    public ResponseEntity<ApiResponse<BenevolenceBeneficiaryDto>> addBeneficiary(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitBeneficiariesRequest.BeneficiaryEntry entry) {
        return ResponseEntity.ok(ApiResponse.ok("Beneficiary added", enrollmentService.addBeneficiary(id, entry)));
    }

    @PostMapping("/enrollments/{id}/beneficiaries/lock")
    public ResponseEntity<ApiResponse<Void>> lockBeneficiaries(@PathVariable UUID id) {
        enrollmentService.lockBeneficiaries(id);
        return ResponseEntity.ok(ApiResponse.ok("Beneficiaries locked"));
    }

    @PatchMapping("/beneficiaries/{beneficiaryId}/deceased")
    public ResponseEntity<ApiResponse<Void>> markBeneficiaryDeceased(
            @PathVariable UUID beneficiaryId,
            @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("adminNotes") : null;
        enrollmentService.markBeneficiaryDeceased(beneficiaryId, notes);
        return ResponseEntity.ok(ApiResponse.ok("Beneficiary marked as deceased"));
    }

    // ── Claims ────────────────────────────────────────────────────────────────

    @GetMapping("/claims")
    public ResponseEntity<ApiResponse<PagedResponse<BenevolenceClaimDto>>> listClaims(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("submittedAt").descending());
        return ResponseEntity.ok(ApiResponse.ok(claimService.listClaims(status, pageable)));
    }

    @GetMapping("/claims/{id}")
    public ResponseEntity<ApiResponse<BenevolenceClaimDto>> getClaim(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(claimService.getClaimById(id)));
    }

    @PatchMapping("/claims/{id}/review")
    public ResponseEntity<ApiResponse<BenevolenceClaimDto>> reviewClaim(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewClaimRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Claim reviewed", claimService.reviewClaim(id, req)));
    }

    @PatchMapping("/claims/{id}/authorize")
    public ResponseEntity<ApiResponse<BenevolenceClaimDto>> authorizeDisbursement(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Disbursement authorized", claimService.authorizeDisbursement(id)));
    }

    @PatchMapping("/claims/{id}/disburse")
    public ResponseEntity<ApiResponse<BenevolenceClaimDto>> markDisbursed(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Claim marked as disbursed", claimService.markDisbursed(id)));
    }

    // ── Replenishments ────────────────────────────────────────────────────────

    @GetMapping("/replenishments")
    public ResponseEntity<ApiResponse<PagedResponse<BenevolenceReplenishmentDto>>> listReplenishments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.ok(claimService.listReplenishments(pageable)));
    }

    @GetMapping("/replenishments/{id}")
    public ResponseEntity<ApiResponse<BenevolenceReplenishmentDto>> getReplenishment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(claimService.getReplenishmentById(id)));
    }

    @PostMapping("/replenishments")
    public ResponseEntity<ApiResponse<BenevolenceReplenishmentDto>> createReplenishment(
            @Valid @RequestBody CreateReplenishmentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Replenishment created", claimService.createReplenishment(req)));
    }

    @PatchMapping("/replenishments/payments/{paymentId}/waive")
    public ResponseEntity<ApiResponse<ReplenishmentPaymentDto>> waiveReplenishmentPayment(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.ok("Payment waived",
                claimService.waiveReplenishmentPayment(paymentId, reason)));
    }

    // ── Claim Categories ──────────────────────────────────────────────────────

    @GetMapping("/claim-categories")
    public ResponseEntity<ApiResponse<java.util.List<ClaimCategoryDto>>> listCategories() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.listAll()));
    }

    @PostMapping("/claim-categories")
    public ResponseEntity<ApiResponse<ClaimCategoryDto>> createCategory(
            @Valid @RequestBody SaveClaimCategoryRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Category created", categoryService.create(req)));
    }

    @PutMapping("/claim-categories/{id}")
    public ResponseEntity<ApiResponse<ClaimCategoryDto>> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody SaveClaimCategoryRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Category updated", categoryService.update(id, req)));
    }

    @PatchMapping("/claim-categories/{id}/toggle")
    public ResponseEntity<ApiResponse<ClaimCategoryDto>> toggleCategory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Category toggled", categoryService.toggleActive(id)));
    }

    @DeleteMapping("/claim-categories/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Category deleted"));
    }
}
