package com.mdau.ushirika.module.member.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.member.dto.AdminApplicationDto;
import com.mdau.ushirika.module.member.dto.AdminReviewRequest;
import com.mdau.ushirika.module.member.dto.BulkActionResultDto;
import com.mdau.ushirika.module.member.dto.BulkApplicationActionRequest;
import com.mdau.ushirika.module.member.dto.BulkApproveRequest;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.module.member.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/membership")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','CAP_APPLICATIONS')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Membership — Admin", description = "Review and approve/reject membership applications")
public class AdminMembershipController {

    private final MembershipService membershipService;

    @GetMapping("/applications")
    @Operation(summary = "List all applications, optionally filtered by status")
    public ResponseEntity<ApiResponse<PagedResponse<AdminApplicationDto>>> list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth
    ) {
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
        PagedResponse<AdminApplicationDto> result = membershipService.listApplications(
                status, PageRequest.of(page, size, Sort.by("createdAt").descending()), isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Applications retrieved", result));
    }

    @GetMapping("/applications/{id}")
    @Operation(summary = "Get full detail of a single application")
    public ResponseEntity<ApiResponse<AdminApplicationDto>> get(@PathVariable UUID id, Authentication auth) {
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
        return ResponseEntity.ok(ApiResponse.ok("Application retrieved",
                membershipService.getApplication(id, isSuperAdmin)));
    }

    @PostMapping("/applications/{id}/review")
    @Operation(summary = "Reject a SUBMITTED application (acceptance is done via Send Form)")
    public ResponseEntity<ApiResponse<AdminApplicationDto>> review(
            @PathVariable UUID id,
            @Valid @RequestBody AdminReviewRequest req,
            Authentication auth
    ) {
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
        AdminApplicationDto result = membershipService.review(id, req, isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Vote recorded", result));
    }

    @PostMapping("/applications/{id}/send-form")
    @Operation(summary = "Accept an application in principle and send the applicant their onboarding form")
    public ResponseEntity<ApiResponse<AdminApplicationDto>> sendForm(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean waiveRegistrationFee,
            Authentication auth
    ) {
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
        return ResponseEntity.ok(ApiResponse.ok("Form sent to applicant",
                membershipService.sendForm(id, isSuperAdmin, waiveRegistrationFee)));
    }

    @PostMapping("/applications/{id}/resend-form")
    @Operation(summary = "Resend onboarding login credentials to an applicant stuck mid-onboarding (lost email, forgot password, expired link)")
    public ResponseEntity<ApiResponse<AdminApplicationDto>> resendForm(@PathVariable UUID id, Authentication auth) {
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
        return ResponseEntity.ok(ApiResponse.ok("Onboarding credentials resent", membershipService.resendFormCredentials(id, isSuperAdmin)));
    }

    @PostMapping("/applications/{id}/approve-membership")
    @Operation(summary = "Grant full membership — requires a verified registration fee payment unless waiveRegistrationFee is set")
    public ResponseEntity<ApiResponse<AdminApplicationDto>> approveMembership(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean waiveRegistrationFee,
            Authentication auth
    ) {
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
        return ResponseEntity.ok(ApiResponse.ok("Membership approved",
                membershipService.approveMembership(id, isSuperAdmin, waiveRegistrationFee)));
    }

    /**
     * Mass-onboarding tool: the first batch of applicants after launch are typically real-world
     * members who joined before the platform existed, not fresh signups — this sends onboarding
     * credentials to every SUBMITTED application in one action instead of one-by-one.
     * {@code waiveRegistrationFee} applies to the whole batch, same as {@link #bulkApprove} —
     * marks every application fee-exempt right now so their onboarding wizard skips the payment
     * step entirely, rather than waiting until final approval to waive it after the fact.
     * Each item runs through {@link MembershipService#sendForm} via this bean's proxy, so one
     * application's failure (e.g. a duplicate email) does not roll back or block the others.
     */
    @PostMapping("/applications/bulk-send-form")
    @Operation(summary = "Send onboarding forms to multiple SUBMITTED applications at once")
    public ResponseEntity<ApiResponse<BulkActionResultDto>> bulkSendForm(
            @Valid @RequestBody BulkApplicationActionRequest req, Authentication auth
    ) {
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
        List<BulkActionResultDto.ItemResult> results = new ArrayList<>();
        for (UUID id : req.applicationIds()) {
            try {
                membershipService.sendForm(id, isSuperAdmin, req.waiveRegistrationFee());
                results.add(BulkActionResultDto.ItemResult.success(id));
            } catch (Exception e) {
                log.warn("Bulk send-form failed for application {}: {}", id, e.getMessage());
                results.add(BulkActionResultDto.ItemResult.failure(id, e.getMessage()));
            }
        }
        BulkActionResultDto result = BulkActionResultDto.of(results);
        return ResponseEntity.ok(ApiResponse.ok(
                result.succeededCount() + " form(s) sent, " + result.failedCount() + " failed", result));
    }

    /**
     * Mass-approval counterpart to {@link #bulkSendForm} — one {@code waiveRegistrationFee}
     * choice applies to the whole selected batch. Applicants still need every onboarding step
     * complete (identity, address, next-of-kin, constitution/bylaws) — only the Stripe payment
     * itself is skippable, and only for applicants who haven't already paid.
     */
    @PostMapping("/applications/bulk-approve")
    @Operation(summary = "Approve membership for multiple applications at once, with an optional batch-wide fee waiver")
    public ResponseEntity<ApiResponse<BulkActionResultDto>> bulkApprove(
            @Valid @RequestBody BulkApproveRequest req, Authentication auth
    ) {
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
        List<BulkActionResultDto.ItemResult> results = new ArrayList<>();
        for (UUID id : req.applicationIds()) {
            try {
                membershipService.approveMembership(id, isSuperAdmin, req.waiveRegistrationFee());
                results.add(BulkActionResultDto.ItemResult.success(id));
            } catch (Exception e) {
                log.warn("Bulk approve failed for application {}: {}", id, e.getMessage());
                results.add(BulkActionResultDto.ItemResult.failure(id, e.getMessage()));
            }
        }
        BulkActionResultDto result = BulkActionResultDto.of(results);
        return ResponseEntity.ok(ApiResponse.ok(
                result.succeededCount() + " membership(s) approved, " + result.failedCount() + " failed", result));
    }
}
