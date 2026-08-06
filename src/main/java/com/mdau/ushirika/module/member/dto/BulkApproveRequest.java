package com.mdau.ushirika.module.member.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** {@code waiveRegistrationFee} applies to the whole batch — an admin decides once whether
 * this group of (typically pre-existing, real-world) members should skip Stripe checkout. */
public record BulkApproveRequest(
        @NotEmpty List<UUID> applicationIds,
        boolean waiveRegistrationFee
) {
}
