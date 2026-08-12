package com.mdau.ushirika.module.benevolence.dto;

import jakarta.validation.constraints.Size;

public record RequestBenevolenceJoinRequest(
        @Size(max = 500) String memberNotes
) {}
