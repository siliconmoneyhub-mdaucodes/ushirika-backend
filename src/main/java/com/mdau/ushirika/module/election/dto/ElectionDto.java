package com.mdau.ushirika.module.election.dto;

import com.mdau.ushirika.module.election.entity.Election;
import com.mdau.ushirika.module.election.enums.ElectionStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ElectionDto(
        UUID id,
        String title,
        int year,
        LocalDate nominationsStart,
        LocalDate nominationsEnd,
        Instant votingStart,
        Instant votingEnd,
        ElectionStatus status,
        String videoUrl,
        String notes,
        Instant resultsDeclaredAt,
        Instant completedAt,
        List<ElectionSeatDto> seats,
        List<ElectionResultDto> results
) {
    public static ElectionDto from(Election e, List<ElectionSeatDto> seats, List<ElectionResultDto> results) {
        return new ElectionDto(
                e.getId(),
                e.getTitle(),
                e.getYear(),
                e.getNominationsStart(),
                e.getNominationsEnd(),
                AppClock.toInstant(e.getVotingStart()),
                AppClock.toInstant(e.getVotingEnd()),
                e.getStatus(),
                e.getVideoUrl(),
                e.getNotes(),
                AppClock.serverInstant(e.getResultsDeclaredAt()),
                AppClock.serverInstant(e.getCompletedAt()),
                seats,
                results
        );
    }
}
