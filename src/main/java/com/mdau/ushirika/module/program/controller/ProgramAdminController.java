package com.mdau.ushirika.module.program.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.notification.dto.BroadcastRequest;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import com.mdau.ushirika.module.program.dto.DecideProgramApplicationRequest;
import com.mdau.ushirika.module.program.dto.ProgramApplicationDto;
import com.mdau.ushirika.module.program.dto.ProgramDto;
import com.mdau.ushirika.module.program.dto.UpdateProgramDetailsRequest;
import com.mdau.ushirika.module.program.service.ProgramApplicationService;
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
    private final ProgramApplicationService programApplicationService;
    private final InAppNotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProgramDto>>> myPrograms() {
        return ResponseEntity.ok(ApiResponse.ok(programService.listProgramsIAdminister()));
    }

    @PatchMapping("/{id}/details")
    public ResponseEntity<ApiResponse<ProgramDto>> updateDetails(
            @PathVariable UUID id, @Valid @RequestBody UpdateProgramDetailsRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Program details updated", programService.updateDetailsAsProgramAdmin(id, req)));
    }

    @GetMapping("/{id}/applications")
    public ResponseEntity<ApiResponse<List<ProgramApplicationDto>>> applications(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(programApplicationService.listApplicationsForProgram(id)));
    }

    @PatchMapping("/applications/{applicationId}/decision")
    public ResponseEntity<ApiResponse<ProgramApplicationDto>> decide(
            @PathVariable UUID applicationId, @Valid @RequestBody DecideProgramApplicationRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Decision recorded", programApplicationService.decide(applicationId, req)));
    }

    @PostMapping("/{id}/notifications/broadcast")
    public ResponseEntity<ApiResponse<Void>> notifyProgramMembers(
            @PathVariable UUID id, @Valid @RequestBody BroadcastRequest req) {
        List<com.mdau.ushirika.module.auth.entity.User> recipients = programApplicationService.listApprovedMembersForProgram(id);
        notificationService.notifyUsers(recipients, req);
        return ResponseEntity.ok(ApiResponse.ok(recipients.size() + " member(s) notified"));
    }

    @PostMapping("/{id}/notifications/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> notifyProgramMember(
            @PathVariable UUID id, @PathVariable UUID memberId, @Valid @RequestBody BroadcastRequest req) {
        com.mdau.ushirika.module.auth.entity.User member = programApplicationService.requireProgramApplicant(id, memberId);
        notificationService.notifyMember(member.getId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Notification sent"));
    }
}
