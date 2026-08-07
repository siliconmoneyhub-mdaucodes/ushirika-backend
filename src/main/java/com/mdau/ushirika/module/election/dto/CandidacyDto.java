package com.mdau.ushirika.module.election.dto;

import com.mdau.ushirika.module.election.entity.ElectionCandidacy;
import com.mdau.ushirika.module.election.enums.CandidacyStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record CandidacyDto(
        UUID id,
        UUID electionId,
        UUID seatId,
        String seatTitle,
        UUID candidateUserId,
        String memberName,
        String memberId,
        String photoUrl,
        String videoUrl,
        String statement,
        CandidacyStatus status,
        String rejectionReason,
        String reviewedBy,
        LocalDateTime reviewedAt,
        long voteCount   // 0 until results released
) {
    public static CandidacyDto from(ElectionCandidacy c, long voteCount) {
        return new CandidacyDto(
                c.getId(),
                c.getElection().getId(),
                c.getSeat().getId(),
                c.getSeat().getTitle(),
                c.getCandidate().getId(),
                c.getMemberName(),
                c.getMemberId(),
                c.getPhotoUrl(),
                c.getVideoUrl(),
                c.getStatement(),
                c.getStatus(),
                c.getRejectionReason(),
                c.getReviewedBy(),
                c.getReviewedAt(),
                voteCount
        );
    }

    public static CandidacyDto from(ElectionCandidacy c) {
        return from(c, 0L);
    }
}
