package com.mdau.ushirika.module.member.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** {@code waiveRegistrationFee} applies to the whole batch — marks every application fee-exempt
 *  right now, at send-form time, for a group of (typically pre-existing, real-world) members. */
public record BulkApplicationActionRequest(
        @NotEmpty List<UUID> applicationIds,
        boolean waiveRegistrationFee
) {
}
