package com.mdau.ushirika.module.actionitems.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.actionitems.dto.ActionItemsDto;
import com.mdau.ushirika.module.actionitems.service.ActionItemsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/action-items")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Action Items — Admin", description = "\"Needs your attention\" items computed live from existing data (Phase 1: applications + messages)")
public class ActionItemsController {

    private final ActionItemsService actionItemsService;

    @GetMapping
    @Operation(summary = "Items needing the current admin's attention, filtered to what their role/assignments can act on")
    public ResponseEntity<ApiResponse<ActionItemsDto>> get() {
        return ResponseEntity.ok(ApiResponse.ok("Action items retrieved", actionItemsService.getActionItems()));
    }
}
