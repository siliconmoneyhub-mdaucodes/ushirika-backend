package com.mdau.ushirika.module.election.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.election.dto.CandidacyDto;
import com.mdau.ushirika.module.election.dto.CastVoteRequest;
import com.mdau.ushirika.module.election.dto.DeclareCandidacyRequest;
import com.mdau.ushirika.module.election.dto.ElectionDto;
import com.mdau.ushirika.module.election.service.ElectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/elections")
@RequiredArgsConstructor
public class PortalElectionController {

    private final ElectionService electionService;

    /** Member's own candidacy history across all elections. */
    @GetMapping("/my/candidacies")
    public ResponseEntity<ApiResponse<List<CandidacyDto>>> myCandidacies(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(electionService.getMyCandidacies(user.getId())));
    }

    /** Returns seat IDs for which the member has already voted in this election. */
    @GetMapping("/{electionId}/my/votes")
    public ResponseEntity<ApiResponse<List<UUID>>> myVotedSeats(
            @PathVariable UUID electionId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(electionService.getMyVotedSeats(electionId, user.getId())));
    }

    /** Declare candidacy for a seat during NOMINATIONS_OPEN. */
    @PostMapping("/{electionId}/candidacies")
    public ResponseEntity<ApiResponse<CandidacyDto>> declare(
            @PathVariable UUID electionId,
            @Valid @RequestBody DeclareCandidacyRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                ApiResponse.ok("Candidacy submitted for review.", electionService.declareCandidacy(electionId, user.getId(), req)));
    }

    /** Withdraw own candidacy (only during NOMINATIONS_OPEN). */
    @DeleteMapping("/candidacies/{candidacyId}")
    public ResponseEntity<ApiResponse<CandidacyDto>> withdraw(
            @PathVariable UUID candidacyId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                ApiResponse.ok("Candidacy withdrawn.", electionService.withdrawCandidacy(candidacyId, user.getId())));
    }

    /** Cast a vote. Receipt + tally updated atomically. */
    @PostMapping("/{electionId}/vote")
    public ResponseEntity<ApiResponse<String>> vote(
            @PathVariable UUID electionId,
            @Valid @RequestBody CastVoteRequest req,
            @AuthenticationPrincipal User user) {
        String msg = electionService.castVote(electionId, user.getId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Vote recorded", msg));
    }

    /**
     * Live running vote counts per candidate, for the members-only live-results feed.
     * Individual ballots stay secret throughout -- only aggregate counts are ever exposed.
     */
    @GetMapping("/{electionId}/live")
    public ResponseEntity<ApiResponse<ElectionDto>> live(@PathVariable UUID electionId) {
        return ResponseEntity.ok(ApiResponse.ok(electionService.getLiveResults(electionId)));
    }
}
