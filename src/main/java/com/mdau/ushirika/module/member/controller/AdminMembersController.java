package com.mdau.ushirika.module.member.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.auth.dto.UserProfileDto;
import com.mdau.ushirika.module.member.dto.MemberFinancialSummaryDto;
import com.mdau.ushirika.module.member.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/members")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_FINANCIAL_ADMIN','ROLE_SECRETARY','CAP_MEMBERS')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Members — Admin", description = "Read-only member directory — SecurityConfig further restricts FINANCIAL_ADMIN/SECRETARY to GET only")
public class AdminMembersController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "List all users with profile — accessible to ADMIN and SUPERADMIN")
    public ResponseEntity<ApiResponse<PagedResponse<UserProfileDto>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Members retrieved",
                adminUserService.listMembersWithProfile(PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{id}/financial-summary")
    @Operation(summary = "Dues balance, Benevolence status/balance, and outstanding fines for one member")
    public ResponseEntity<ApiResponse<MemberFinancialSummaryDto>> financialSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Financial summary retrieved",
                adminUserService.getFinancialSummary(id)));
    }
}
