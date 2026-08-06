package com.mdau.ushirika.module.member.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkApplicationActionRequest(
        @NotEmpty List<UUID> applicationIds
) {
}
