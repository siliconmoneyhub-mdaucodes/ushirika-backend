package com.mdau.ushirika.module.benevolence.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.benevolence.dto.*;
import com.mdau.ushirika.module.benevolence.service.BenevolenceClaimService;
import com.mdau.ushirika.module.benevolence.service.BenevolenceEnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MemberBenevolenceController {

    private final BenevolenceEnrollmentService enrollmentService;
    private final BenevolenceClaimService claimService;

    @GetMapping("/benevolence/my")
    public ResponseEntity<ApiResponse<BenevolenceEnrollmentDto>> getMyEnrollment() {
        return ResponseEntity.ok(ApiResponse.ok(enrollmentService.getMyEnrollment()));
    }

    @PostMapping("/benevolence/my/beneficiaries")
    public ResponseEntity<ApiResponse<BenevolenceEnrollmentDto>> submitMyBeneficiaries(
            @Valid @RequestBody SubmitBeneficiariesRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Beneficiaries submitted and locked",
                enrollmentService.submitMyBeneficiaries(req)));
    }

    @GetMapping("/benevolence/my/claims")
    public ResponseEntity<ApiResponse<List<BenevolenceClaimDto>>> getMyClaims() {
        return ResponseEntity.ok(ApiResponse.ok(claimService.getMyClaims()));
    }

    @PostMapping("/benevolence/my/claims")
    public ResponseEntity<ApiResponse<BenevolenceClaimDto>> submitClaim(
            @Valid @RequestBody SubmitClaimRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Claim submitted successfully",
                claimService.submitClaim(req)));
    }

    @GetMapping("/benevolence/my/replenishments")
    public ResponseEntity<ApiResponse<List<ReplenishmentPaymentDto>>> getMyReplenishments() {
        return ResponseEntity.ok(ApiResponse.ok(claimService.getMyReplenishments()));
    }
}
