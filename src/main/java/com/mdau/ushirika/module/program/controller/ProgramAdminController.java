package com.mdau.ushirika.module.program.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.program.dto.ProgramDto;
import com.mdau.ushirika.module.program.dto.UpdateProgramDetailsRequest;
import com.mdau.ushirika.module.program.service.ProgramService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Self-service for whoever has been assigned as a program's admin — no ADMIN/SUPERADMIN
 * role required, authorization is scoped per-program via ProgramAdminAssignment
 * (see ProgramService.updateDetailsAsProgramAdmin). Sits under "any authenticated user"
 * in SecurityConfig, same delegation pattern as the manual-payment module.
 */
@RestController
@RequestMapping("/programs/mine")
@RequiredArgsConstructor
public class ProgramAdminController {

    private final ProgramService programService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProgramDto>>> myPrograms() {
        return ResponseEntity.ok(ApiResponse.ok(programService.listProgramsIAdminister()));
    }

    @PatchMapping("/{id}/details")
    public ResponseEntity<ApiResponse<ProgramDto>> updateDetails(
            @PathVariable UUID id, @Valid @RequestBody UpdateProgramDetailsRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Program details updated", programService.updateDetailsAsProgramAdmin(id, req)));
    }
}
