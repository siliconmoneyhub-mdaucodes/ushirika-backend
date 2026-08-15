package com.mdau.ushirika.module.mgr.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.mgr.dto.*;
import com.mdau.ushirika.module.mgr.service.MgrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/mgr")
@RequiredArgsConstructor
public class MemberMgrController {

    private final MgrService mgrService;

    // ── My slot / portal ──────────────────────────────────────────────────────

    /** Get my slot in the active cycle (or a specific cycle). */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<MgrSlotDto>> getMySlot(
            @RequestParam(required = false) UUID cycleId) {
        return ResponseEntity.ok(ApiResponse.ok(mgrService.getMySlot(cycleId)));
    }

    // ── Join Requests ─────────────────────────────────────────────────────────
    // Applications are accepted at any time -- not gated by whether any cycle is DRAFT or ACTIVE.

    @PostMapping("/join-requests")
    public ResponseEntity<ApiResponse<MgrJoinRequestDto>> requestJoin(
            @Valid @RequestBody JoinMgrRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Join request submitted",
                mgrService.requestJoin(req.notes())));
    }

    @GetMapping("/join-requests")
    public ResponseEntity<ApiResponse<List<MgrJoinRequestDto>>> getMyJoinRequests() {
        return ResponseEntity.ok(ApiResponse.ok(mgrService.getMyJoinRequests()));
    }

    /** Member confirms they want to join the waitlist after reviewing the coordinator's form. */
    @PostMapping("/join-requests/{requestId}/confirm")
    public ResponseEntity<ApiResponse<MgrJoinRequestDto>> confirmJoinWaitlist(
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(ApiResponse.ok("You're on the waitlist",
                mgrService.confirmJoinWaitlist(requestId)));
    }

    /** Member responds to the automated "join this cycle or keep waiting?" ask. */
    @PostMapping("/join-requests/{requestId}/cycle-response")
    public ResponseEntity<ApiResponse<MgrJoinRequestDto>> respondToCycleInvite(
            @PathVariable UUID requestId,
            @Valid @RequestBody CycleInviteResponseRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Response recorded",
                mgrService.respondToCycleInvite(requestId, req.joining())));
    }

    // ── Beneficiaries (slot machine data) ────────────────────────────────────

    /** Returns beneficiaries for a given month — public view (name + photo, no contact info). */
    @GetMapping("/cycles/{cycleId}/beneficiaries/{month}")
    public ResponseEntity<ApiResponse<List<MgrSlotDto>>> getCurrentBeneficiaries(
            @PathVariable UUID cycleId,
            @PathVariable int month) {
        return ResponseEntity.ok(ApiResponse.ok(mgrService.getCurrentBeneficiaries(cycleId, month)));
    }

    /** Returns all members in a cycle for the slot machine name pool. */
    @GetMapping("/cycles/{cycleId}/members")
    public ResponseEntity<ApiResponse<List<MgrSlotDto>>> getCycleMembers(
            @PathVariable UUID cycleId) {
        return ResponseEntity.ok(ApiResponse.ok(mgrService.getCycleMembers(cycleId)));
    }

    // ── Receipt confirmation ──────────────────────────────────────────────────

    @PostMapping("/slots/{slotId}/confirm-receipt")
    public ResponseEntity<ApiResponse<MgrSlotDto>> confirmReceipt(
            @PathVariable UUID slotId,
            @Valid @RequestBody ConfirmReceiptRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Receipt confirmed",
                mgrService.confirmReceipt(slotId, req.notes())));
    }
}
