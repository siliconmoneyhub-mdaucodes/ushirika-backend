package com.mdau.ushirika.module.mgr.dto;

import jakarta.validation.constraints.NotNull;

public record CycleInviteResponseRequest(
        @NotNull
        Boolean joining
) {}
