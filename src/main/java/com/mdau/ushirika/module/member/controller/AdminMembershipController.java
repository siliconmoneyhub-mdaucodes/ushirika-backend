package com.mdau.ushirika.module.member.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.member.dto.AdminApplicationDto;
import com.mdau.ushirika.module.member.dto.AdminReviewRequest;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.module.member.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/membership")
@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
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
    public ResponseEntity<ApiResponse<AdminApplicationDto>> sendForm(@PathVariable UUID id, Authentication auth) {
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
        return ResponseEntity.ok(ApiResponse.ok("Form sent to applicant", membershipService.sendForm(id, isSuperAdmin)));
    }

    @PostMapping("/applications/{id}/resend-form")
    @Operation(summary = "Resend onboarding login credentials to an applicant stuck mid-onboarding (lost email, forgot password, expired link)")
    public ResponseEntity<ApiResponse<AdminApplicationDto>> resendForm(@PathVariable UUID id, Authentication auth) {
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
        return ResponseEntity.ok(ApiResponse.ok("Onboarding credentials resent", membershipService.resendFormCredentials(id, isSuperAdmin)));
    }

    @PostMapping("/applications/{id}/approve-membership")
    @Operation(summary = "Grant full membership once the registration fee payment has been verified")
    public ResponseEntity<ApiResponse<AdminApplicationDto>> approveMembership(@PathVariable UUID id, Authentication auth) {
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
        return ResponseEntity.ok(ApiResponse.ok("Membership approved", membershipService.approveMembership(id, isSuperAdmin)));
    }
}
