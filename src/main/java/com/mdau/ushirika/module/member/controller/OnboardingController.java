package com.mdau.ushirika.module.member.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.member.dto.AdditionalInfoRequest;
import com.mdau.ushirika.module.member.dto.OnboardingStatusDto;
import com.mdau.ushirika.module.member.dto.VerifyOnboardingEmailRequest;
import com.mdau.ushirika.module.member.service.OnboardingService;
import com.mdau.ushirika.module.program.dto.ApplyToProgramsRequest;
import com.mdau.ushirika.module.program.dto.ProgramApplicationDto;
import com.mdau.ushirika.module.program.service.ProgramApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Applicant onboarding steps between "Send Form" and final membership approval.
 * Restricted to APPLICANT role only — see SecurityConfig's /onboarding/** matcher.
 */
@RestController
@RequestMapping("/onboarding")
@PreAuthorize("hasRole('APPLICANT')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Onboarding", description = "Applicant onboarding: additional info, bylaws acceptance, registration payment")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final ProgramApplicationService programApplicationService;

    @GetMapping("/status")
    @Operation(summary = "Current onboarding progress for the logged-in applicant")
    public ResponseEntity<ApiResponse<OnboardingStatusDto>> status() {
        return ResponseEntity.ok(ApiResponse.ok("Onboarding status retrieved", onboardingService.getStatus()));
    }

    @PostMapping("/email-otp/request")
    @Operation(summary = "Request a fresh email verification code for the onboarding step")
    public ResponseEntity<ApiResponse<Void>> requestEmailOtp() {
        onboardingService.requestEmailOtp();
        return ResponseEntity.ok(ApiResponse.ok("Verification code sent. Check your email."));
    }

    @PostMapping("/email-otp/verify")
    @Operation(summary = "Verify the onboarding email code")
    public ResponseEntity<ApiResponse<OnboardingStatusDto>> verifyEmailOtp(@Valid @RequestBody VerifyOnboardingEmailRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Email verified", onboardingService.verifyEmailOtp(req)));
    }

    @PostMapping("/additional-info")
    @Operation(summary = "Submit additional information not captured on the original apply form")
    public ResponseEntity<ApiResponse<OnboardingStatusDto>> additionalInfo(@Valid @RequestBody AdditionalInfoRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Additional information saved", onboardingService.submitAdditionalInfo(req)));
    }

    @PostMapping("/accept-bylaws")
    @Operation(summary = "Confirm you have read and accept the bylaws and constitution")
    public ResponseEntity<ApiResponse<OnboardingStatusDto>> acceptBylaws() {
        return ResponseEntity.ok(ApiResponse.ok("Bylaws acceptance recorded", onboardingService.acceptBylaws()));
    }

    @PostMapping("/submit-registration")
    @Operation(summary = "Final onboarding step — submit for membership approval once the registration fee is reported")
    public ResponseEntity<ApiResponse<OnboardingStatusDto>> submitRegistration() {
        return ResponseEntity.ok(ApiResponse.ok("Registration submitted for final approval", onboardingService.submitRegistration()));
    }

    @PostMapping("/programs")
    @Operation(summary = "Apply to join one or more programs (MGR, Benevolence, etc.) — visible to the program's coordinator only once membership is approved")
    public ResponseEntity<ApiResponse<List<ProgramApplicationDto>>> applyToPrograms(@Valid @RequestBody ApplyToProgramsRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Program application(s) submitted", programApplicationService.applyToPrograms(req)));
    }

    @GetMapping("/programs")
    @Operation(summary = "List the programs this applicant has applied to")
    public ResponseEntity<ApiResponse<List<ProgramApplicationDto>>> myProgramApplications() {
        return ResponseEntity.ok(ApiResponse.ok(programApplicationService.listMyApplications()));
    }
}
