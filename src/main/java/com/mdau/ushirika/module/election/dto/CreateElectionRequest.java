package com.mdau.ushirika.module.election.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CreateElectionRequest(
        @NotBlank String title,
        @NotNull Integer year,
        LocalDate nominationsStart,
        LocalDate nominationsEnd,
        LocalDateTime votingStart,
        LocalDateTime votingEnd,
        String notes,
        @NotNull List<SeatRequest> seats
) {
    public record SeatRequest(
            @NotBlank String title,
            String description,
            int maxWinners,
            int sortOrder,
            boolean executiveTier
    ) {}
}
