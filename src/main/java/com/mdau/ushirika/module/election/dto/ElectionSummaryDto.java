package com.mdau.ushirika.module.election.dto;

import com.mdau.ushirika.module.election.entity.Election;
import com.mdau.ushirika.module.election.enums.ElectionStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ElectionSummaryDto(
        UUID id,
        String title,
        int year,
        LocalDate nominationsStart,
        LocalDate nominationsEnd,
        Instant votingStart,
        Instant votingEnd,
        ElectionStatus status,
        String videoUrl,
        int seatCount
) {
    public static ElectionSummaryDto from(Election e) {
        return new ElectionSummaryDto(
                e.getId(),
                e.getTitle(),
                e.getYear(),
                e.getNominationsStart(),
                e.getNominationsEnd(),
                AppClock.toInstant(e.getVotingStart()),
                AppClock.toInstant(e.getVotingEnd()),
                e.getStatus(),
                e.getVideoUrl(),
                e.getSeats().size()
        );
    }
}
