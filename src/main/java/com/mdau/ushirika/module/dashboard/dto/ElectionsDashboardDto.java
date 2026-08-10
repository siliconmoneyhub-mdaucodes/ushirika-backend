package com.mdau.ushirika.module.dashboard.dto;

import com.mdau.ushirika.module.election.enums.ElectionStatus;

/** Elections-capability dashboard — the current (most recent non-archived) election, if any. */
public record ElectionsDashboardDto(
        long totalElections,
        Integer activeElectionYear,
        ElectionStatus activeElectionStatus,
        long pendingCandidacies
) {
}
