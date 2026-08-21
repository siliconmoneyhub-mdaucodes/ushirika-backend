package com.mdau.ushirika.module.member.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.service.TurnstileVerificationService;
import com.mdau.ushirika.module.member.dto.ApplicationTrackDto;
import com.mdau.ushirika.module.member.dto.MembershipApplicationRequest;
import com.mdau.ushirika.module.member.dto.PublicMembershipApplicationRequest;
import com.mdau.ushirika.module.member.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Membership — Member", description = "Apply for and track membership")
public class MembershipController {

    private final MembershipService membershipService;
    private final TurnstileVerificationService turnstileVerificationService;

    // ------------------------------------------------------------------ Public

    @GetMapping("/public/membership/track")
    @Operation(summary = "Track membership application by reference number (public, no auth required)")
    public ResponseEntity<ApiResponse<ApplicationTrackDto>> track(@RequestParam String ref) {
        return ResponseEntity.ok(ApiResponse.ok("Application found", membershipService.trackByReference(ref)));
    }

    @PostMapping("/public/membership/applications")
    @Operation(summary = "Submit a public membership enquiry (no auth required)")
    public ResponseEntity<ApiResponse<ApplicationTrackDto>> submitPublic(
            @Valid @RequestBody PublicMembershipApplicationRequest req) {
        turnstileVerificationService.verify(req.captchaToken());
        ApplicationTrackDto result = membershipService.submitPublicApplication(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Application submitted. Track with reference: " + result.referenceNumber(), result));
    }

    // ------------------------------------------------------------------ Member (authenticated)

    @PostMapping("/membership/applications")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Save or update your draft membership application")
    public ResponseEntity<ApiResponse<ApplicationTrackDto>> save(@Valid @RequestBody MembershipApplicationRequest req) {
        ApplicationTrackDto result = membershipService.saveApplication(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Application saved as draft", result));
    }

    @PostMapping("/membership/applications/submit")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Submit your draft application to the review board")
    public ResponseEntity<ApiResponse<ApplicationTrackDto>> submit() {
        return ResponseEntity.ok(ApiResponse.ok("Application submitted for review", membershipService.submitApplication()));
    }

    @GetMapping("/membership/applications/my")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get the status of your own membership application")
    public ResponseEntity<ApiResponse<ApplicationTrackDto>> myApplication() {
        return ResponseEntity.ok(ApiResponse.ok("Application retrieved", membershipService.getMyApplication()));
    }
}
