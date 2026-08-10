package com.mdau.ushirika.module.donation.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.donation.dto.*;
import com.mdau.ushirika.module.donation.enums.CampaignStatus;
import com.mdau.ushirika.module.donation.enums.DonationStatus;
import com.mdau.ushirika.module.donation.service.DonationService;
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
@RequestMapping("/admin/donations")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','CAP_CONTENT')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Donations — Admin", description = "Manage donation campaigns and view donation records")
public class AdminDonationController {

    private final DonationService donationService;

    // ─── Campaigns

    @GetMapping("/campaigns")
    @Operation(summary = "List all donation campaigns (all statuses)")
    public ResponseEntity<ApiResponse<PagedResponse<CampaignDto>>> listCampaigns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Campaigns retrieved",
                donationService.listAllCampaigns(
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @PostMapping("/campaigns")
    @Operation(summary = "Create a new donation campaign")
    public ResponseEntity<ApiResponse<CampaignDto>> createCampaign(
            @Valid @RequestBody CampaignRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Campaign created", donationService.createCampaign(req)));
    }

    @PutMapping("/campaigns/{id}")
    @Operation(summary = "Update campaign details")
    public ResponseEntity<ApiResponse<CampaignDto>> updateCampaign(
            @PathVariable UUID id, @Valid @RequestBody CampaignRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Campaign updated", donationService.updateCampaign(id, req)));
    }

    @PatchMapping("/campaigns/{id}/status")
    @Operation(summary = "Change campaign status (ACTIVE → PAUSED | COMPLETED | CANCELLED)")
    public ResponseEntity<ApiResponse<CampaignDto>> updateStatus(
            @PathVariable UUID id, @RequestParam CampaignStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Campaign status updated",
                donationService.updateCampaignStatus(id, status)));
    }

    @GetMapping("/campaigns/{id}/donations")
    @Operation(summary = "List all donations for a specific campaign")
    public ResponseEntity<ApiResponse<PagedResponse<DonationDto>>> campaignDonations(
            @PathVariable UUID id,
            @RequestParam(required = false) DonationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Donations retrieved",
                donationService.listCampaignDonations(id, status,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    // ─── All donations

    @GetMapping
    @Operation(summary = "List all donations across all campaigns (filterable by status)")
    public ResponseEntity<ApiResponse<PagedResponse<DonationDto>>> listAll(
            @RequestParam(required = false) DonationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Donations retrieved",
                donationService.listAllDonations(status,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }
}
