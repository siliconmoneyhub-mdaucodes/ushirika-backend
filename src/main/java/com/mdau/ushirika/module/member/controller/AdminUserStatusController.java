package com.mdau.ushirika.module.member.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.auth.dto.UserDto;
import com.mdau.ushirika.module.member.dto.SetActiveActionRequest;
import com.mdau.ushirika.module.member.dto.SetActiveRequest;
import com.mdau.ushirika.module.member.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Activate/deactivate a member account with a required reason -- previously SUPERADMIN-only
 * (see SuperAdminUserController's now-removed /superadmin/users/{id}/activate|deactivate), widened
 * to ADMIN as well since /admin/** already defaults to ADMIN+SUPERADMIN in SecurityConfig (unlike
 * /superadmin/**, which is hard-gated to SUPERADMIN at the filter-chain level, before any
 * controller-level @PreAuthorize would even be evaluated).
 */
@RestController
@RequestMapping("/admin/users")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Users — Admin", description = "Activate/deactivate a member account (ADMIN and SUPERADMIN)")
public class AdminUserStatusController {

    private final AdminUserService adminUserService;

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Reactivate a member account")
    public ResponseEntity<ApiResponse<UserDto>> activate(
            @PathVariable UUID id, @Valid @RequestBody SetActiveActionRequest req) {
        UserDto result = adminUserService.setActive(id, new SetActiveRequest(true, req.reason(), req.notes()));
        return ResponseEntity.ok(ApiResponse.ok("User activated", result));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a member account (login blocked)")
    public ResponseEntity<ApiResponse<UserDto>> deactivate(
            @PathVariable UUID id, @Valid @RequestBody SetActiveActionRequest req) {
        UserDto result = adminUserService.setActive(id, new SetActiveRequest(false, req.reason(), req.notes()));
        return ResponseEntity.ok(ApiResponse.ok("User deactivated", result));
    }
}
