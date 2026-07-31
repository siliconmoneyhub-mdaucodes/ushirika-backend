package com.mdau.ushirika.module.program.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.program.dto.*;
import com.mdau.ushirika.module.program.service.ProgramService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Full program management: create, edit, status changes, and program-admin assignment. */
@RestController
@RequestMapping("/admin/programs")
@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
@RequiredArgsConstructor
public class AdminProgramController {

    private final ProgramService programService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProgramDto>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(programService.listAllPrograms()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProgramDto>> create(@Valid @RequestBody CreateProgramRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Program created", programService.createProgram(req)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ProgramDto>> updateBasicInfo(
            @PathVariable UUID id, @Valid @RequestBody UpdateProgramBasicInfoRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Program updated", programService.updateBasicInfo(id, req)));
    }

    @PatchMapping("/{id}/details")
    public ResponseEntity<ApiResponse<ProgramDto>> updateDetails(
            @PathVariable UUID id, @Valid @RequestBody UpdateProgramDetailsRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Program details updated", programService.updateDetailsAsProgramAdmin(id, req)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ProgramDto>> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateProgramStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Program status updated", programService.updateStatus(id, req)));
    }

    @PostMapping("/{id}/admins")
    public ResponseEntity<ApiResponse<ProgramAdminDto>> assignAdmin(
            @PathVariable UUID id, @Valid @RequestBody AssignProgramAdminRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Program admin assigned", programService.assignAdmin(id, req)));
    }

    @DeleteMapping("/{id}/admins/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeAdmin(@PathVariable UUID id, @PathVariable UUID userId) {
        programService.removeAdmin(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Program admin removed"));
    }
}
