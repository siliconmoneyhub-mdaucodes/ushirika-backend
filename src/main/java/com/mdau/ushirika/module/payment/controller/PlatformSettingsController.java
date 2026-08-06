package com.mdau.ushirika.module.payment.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.payment.dto.RegistrationFeeDto;
import com.mdau.ushirika.module.payment.dto.UpdateRegistrationFeeRequest;
import com.mdau.ushirika.module.payment.service.PlatformSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Platform Settings", description = "Editable platform-wide financial settings")
public class PlatformSettingsController {

    private final PlatformSettingsService settingsService;

    @GetMapping("/public/settings/registration-fee")
    @Operation(summary = "Current registration fee amount (public — shown during onboarding)")
    public ResponseEntity<ApiResponse<RegistrationFeeDto>> registrationFee() {
        return ResponseEntity.ok(ApiResponse.ok(new RegistrationFeeDto(settingsService.getRegistrationFeeAmount())));
    }

    @PutMapping("/financial/settings/registration-fee")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update the registration fee amount — ADMIN/SUPERADMIN and finance coordinators only")
    public ResponseEntity<ApiResponse<RegistrationFeeDto>> updateRegistrationFee(@Valid @RequestBody UpdateRegistrationFeeRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Registration fee updated",
                new RegistrationFeeDto(settingsService.updateRegistrationFeeAmount(req.amount()))));
    }
}
