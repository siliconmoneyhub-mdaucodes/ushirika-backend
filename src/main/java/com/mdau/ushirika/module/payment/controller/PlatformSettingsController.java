package com.mdau.ushirika.module.payment.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.payment.dto.BenevolenceProbationDto;
import com.mdau.ushirika.module.payment.dto.DisplayCurrencyDto;
import com.mdau.ushirika.module.payment.dto.RegistrationFeeDto;
import com.mdau.ushirika.module.payment.dto.UpdateBenevolenceProbationRequest;
import com.mdau.ushirika.module.payment.dto.UpdateDisplayCurrencyRequest;
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

    @GetMapping("/public/settings/display-currency")
    @Operation(summary = "Platform-wide default display currency (public — used to initialize every page's currency toggle)")
    public ResponseEntity<ApiResponse<DisplayCurrencyDto>> displayCurrency() {
        return ResponseEntity.ok(ApiResponse.ok(new DisplayCurrencyDto(settingsService.getDefaultDisplayCurrency())));
    }

    @PutMapping("/financial/settings/display-currency")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update the platform default display currency — ADMIN, FINANCIAL_ADMIN, or SUPERADMIN only")
    public ResponseEntity<ApiResponse<DisplayCurrencyDto>> updateDisplayCurrency(@Valid @RequestBody UpdateDisplayCurrencyRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Default display currency updated",
                new DisplayCurrencyDto(settingsService.updateDefaultDisplayCurrency(req.currency()))));
    }

    @GetMapping("/public/settings/benevolence-probation")
    @Operation(summary = "Current Benevolence probation period in months (public — shown on the Benevolence program page)")
    public ResponseEntity<ApiResponse<BenevolenceProbationDto>> benevolenceProbation() {
        return ResponseEntity.ok(ApiResponse.ok(new BenevolenceProbationDto(settingsService.getBenevolenceProbationMonths())));
    }

    @PutMapping("/financial/settings/benevolence-probation")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update the Benevolence probation period — ADMIN/SUPERADMIN and finance coordinators only")
    public ResponseEntity<ApiResponse<BenevolenceProbationDto>> updateBenevolenceProbation(@Valid @RequestBody UpdateBenevolenceProbationRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Benevolence probation period updated",
                new BenevolenceProbationDto(settingsService.updateBenevolenceProbationMonths(req.months()))));
    }
}
