package com.mdau.ushirika.module.member.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.auth.dto.UserDto;
import com.mdau.ushirika.module.auth.dto.UserProfileDto;
import com.mdau.ushirika.module.member.dto.AdminResetCredentialsRequest;
import com.mdau.ushirika.module.member.dto.CreateMemberRequest;
import com.mdau.ushirika.module.member.dto.UpdateMemberTierRequest;
import com.mdau.ushirika.module.member.dto.UpdateRoleRequest;
import com.mdau.ushirika.module.member.service.AdminUserService;
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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/superadmin/users")
@PreAuthorize("hasRole('SUPERADMIN')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Users — SuperAdmin", description = "Manage user roles and account status (SUPERADMIN only)")
public class SuperAdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "List all users (paginated)")
    public ResponseEntity<ApiResponse<PagedResponse<UserDto>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Users retrieved",
                adminUserService.listUsers(PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single user by ID")
    public ResponseEntity<ApiResponse<UserDto>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("User retrieved", adminUserService.getUser(id)));
    }

    @PostMapping
    @Operation(summary = "Create a verified member account directly — bypasses the application flow. Sends a welcome email with temporary credentials.")
    public ResponseEntity<ApiResponse<UserProfileDto>> create(@Valid @RequestBody CreateMemberRequest req) {
        UserProfileDto created = adminUserService.createMember(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Member account created. Credentials have been emailed to " + req.email(), created));
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "Update a user's role and/or official title")
    public ResponseEntity<ApiResponse<UserDto>> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok("User role updated", adminUserService.updateRole(id, req)));
    }

    // Activate/deactivate moved to AdminUserStatusController (/admin/users/{id}/activate|deactivate)
    // -- now requires a reason and is available to ADMIN as well as SUPERADMIN, which /superadmin/**
    // (hard-gated to SUPERADMIN at the filter-chain level) could never allow.

    @PatchMapping("/{id}/credentials")
    @Operation(summary = "Force-reset a user's email and/or password — no current password required (SUPERADMIN only)")
    public ResponseEntity<ApiResponse<UserDto>> resetCredentials(
            @PathVariable UUID id,
            @Valid @RequestBody AdminResetCredentialsRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Credentials updated", adminUserService.resetCredentials(id, req)));
    }

    @PatchMapping("/{id}/tier")
    @Operation(summary = "Update an approved member's contribution plan tier")
    public ResponseEntity<ApiResponse<UserProfileDto>> updateTier(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMemberTierRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Member tier updated", adminUserService.updateTier(id, req)));
    }
}
