package com.mdau.ushirika.module.election.dto;

import com.mdau.ushirika.module.election.entity.ElectionSeat;

import java.util.List;
import java.util.UUID;

public record ElectionSeatDto(
        UUID id,
        String title,
        String description,
        int maxWinners,
        int sortOrder,
        boolean executiveTier,
        List<CandidacyDto> candidacies
) {
    public static ElectionSeatDto from(ElectionSeat seat, List<CandidacyDto> candidacies) {
        return new ElectionSeatDto(
                seat.getId(),
                seat.getTitle(),
                seat.getDescription(),
                seat.getMaxWinners(),
                seat.getSortOrder(),
                seat.isExecutiveTier(),
                candidacies
        );
    }
}
