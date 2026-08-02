package com.mdau.ushirika.module.mgr.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.mgr.dto.*;
import com.mdau.ushirika.module.mgr.enums.JoinRequestStatus;
import com.mdau.ushirika.module.mgr.service.MgrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/mgr")
@RequiredArgsConstructor
public class AdminMgrController {

    private final MgrService mgrService;

    // ── Cycles ────────────────────────────────────────────────────────────────

    @GetMapping("/cycles")
    public ResponseEntity<ApiResponse<List<MgrCycleDto>>> listCycles() {
        return ResponseEntity.ok(ApiResponse.ok(mgrService.listCycles()));
    }

    @PostMapping("/cycles")
    public ResponseEntity<ApiResponse<MgrCycleDto>> createCycle(
            @Valid @RequestBody CreateCycleRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Cycle created", mgrService.createCycle(req)));
    }

    @GetMapping("/cycles/{id}")
    public ResponseEntity<ApiResponse<MgrCycleDto>> getCycle(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(mgrService.getCycle(id)));
    }

    @PutMapping("/cycles/{id}")
    public ResponseEntity<ApiResponse<MgrCycleDto>> updateCycle(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCycleRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Cycle updated", mgrService.updateCycle(id, req)));
    }

    @PostMapping("/cycles/{id}/activate")
    public ResponseEntity<ApiResponse<MgrCycleDto>> activateCycle(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Cycle activated", mgrService.activateCycle(id)));
    }

    @PostMapping("/cycles/{id}/complete")
    public ResponseEntity<ApiResponse<MgrCycleDto>> completeCycle(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Cycle completed", mgrService.completeCycle(id)));
    }

    @PostMapping("/cycles/{id}/cancel")
    public ResponseEntity<ApiResponse<MgrCycleDto>> cancelCycle(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Cycle cancelled", mgrService.cancelCycle(id)));
    }

    /** Open or close enrollment for a cycle. */
    @PatchMapping("/cycles/{id}/enrollment")
    public ResponseEntity<ApiResponse<MgrCycleDto>> toggleEnrollment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Enrollment toggled", mgrService.toggleEnrollment(id)));
    }

    // ── Join Requests ─────────────────────────────────────────────────────────

    @GetMapping("/cycles/{cycleId}/join-requests")
    public ResponseEntity<ApiResponse<List<MgrJoinRequestDto>>> listJoinRequests(
            @PathVariable UUID cycleId,
            @RequestParam(required = false) JoinRequestStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(mgrService.listJoinRequests(cycleId, status)));
    }

    @PostMapping("/join-requests/{requestId}/approve")
    public ResponseEntity<ApiResponse<MgrJoinRequestDto>> approveJoinRequest(
            @PathVariable UUID requestId,
            @RequestBody(required = false) RespondJoinRequest req) {
        String notes = req != null ? req.adminNotes() : null;
        return ResponseEntity.ok(ApiResponse.ok("Join request approved",
                mgrService.approveJoinRequest(requestId, notes)));
    }

    @PostMapping("/join-requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<MgrJoinRequestDto>> rejectJoinRequest(
            @PathVariable UUID requestId,
            @RequestBody(required = false) RespondJoinRequest req) {
        String notes = req != null ? req.adminNotes() : null;
        return ResponseEntity.ok(ApiResponse.ok("Join request rejected",
                mgrService.rejectJoinRequest(requestId, notes)));
    }

    // ── Monthly Draw ──────────────────────────────────────────────────────────

    @PostMapping("/cycles/{cycleId}/draw")
    public ResponseEntity<ApiResponse<List<MgrSlotDto>>> runMonthlyDraw(
            @PathVariable UUID cycleId,
            @Valid @RequestBody RunMonthlyDrawRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Monthly draw completed",
                mgrService.runMonthlyDraw(cycleId, req.month(), req.year())));
    }

    @GetMapping("/cycles/{cycleId}/beneficiaries/{month}")
    public ResponseEntity<ApiResponse<List<MgrSlotDto>>> getBeneficiaries(
            @PathVariable UUID cycleId,
            @PathVariable int month) {
        return ResponseEntity.ok(ApiResponse.ok(mgrService.getCurrentBeneficiaries(cycleId, month)));
    }

    // ── Slots ─────────────────────────────────────────────────────────────────

    @PostMapping("/cycles/{cycleId}/slots")
    public ResponseEntity<ApiResponse<MgrSlotDto>> assignSlot(
            @PathVariable UUID cycleId,
            @Valid @RequestBody AssignSlotRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Slot assigned", mgrService.assignSlot(cycleId, req)));
    }

    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<ApiResponse<Void>> removeSlot(@PathVariable UUID slotId) {
        mgrService.removeSlot(slotId);
        return ResponseEntity.ok(ApiResponse.ok("Slot removed"));
    }

    @PostMapping("/slots/{slotId}/payout")
    public ResponseEntity<ApiResponse<MgrSlotDto>> recordPayout(
            @PathVariable UUID slotId,
            @Valid @RequestBody RecordPayoutRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Payout recorded", mgrService.recordPayout(slotId, req)));
    }

    // ── Contributions ─────────────────────────────────────────────────────────

    @PatchMapping("/contributions/{id}/waive")
    public ResponseEntity<ApiResponse<MgrContributionDto>> waiveContribution(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.ok("Contribution waived",
                mgrService.waiveContribution(id, reason)));
    }

    @GetMapping("/cycles/{cycleId}/contributions/month/{month}")
    public ResponseEntity<ApiResponse<List<MgrContributionDto>>> getMonthContributions(
            @PathVariable UUID cycleId,
            @PathVariable int month) {
        return ResponseEntity.ok(ApiResponse.ok(mgrService.getMonthContributions(cycleId, month)));
    }
}
