package com.mdau.ushirika.module.program.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.program.dto.ApplyToProgramRequest;
import com.mdau.ushirika.module.program.dto.MyProgramDto;
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
import java.util.UUID;

/**
 * Verified members browsing and joining programs from the member portal — distinct from
 * the onboarding-time program selection (removed) and from ProgramAdminController's
 * coordinator-facing endpoints under /programs/mine.
 */
@RestController
@RequestMapping("/programs")
@PreAuthorize("hasRole('MEMBER')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Member Programs", description = "Browse programs and manage your own program applications")
public class MemberProgramController {

    private final ProgramApplicationService programApplicationService;

    @GetMapping
    @Operation(summary = "List joinable programs along with your own application state in each")
    public ResponseEntity<ApiResponse<List<MyProgramDto>>> myPrograms() {
        return ResponseEntity.ok(ApiResponse.ok(programApplicationService.listProgramsForMember()));
    }

    @PostMapping("/{id}/apply")
    @Operation(summary = "Apply to join a program")
    public ResponseEntity<ApiResponse<ProgramApplicationDto>> apply(
            @PathVariable UUID id, @Valid @RequestBody ApplyToProgramRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Application submitted", programApplicationService.applyAsMember(id, req)));
    }

    @GetMapping("/{id}/my-application")
    @Operation(summary = "Your own application status for a specific program")
    public ResponseEntity<ApiResponse<ProgramApplicationDto>> myApplication(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(programApplicationService.getMyApplicationForProgram(id)));
    }
}
