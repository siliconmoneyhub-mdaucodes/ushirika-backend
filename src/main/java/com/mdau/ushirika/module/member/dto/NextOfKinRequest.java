package com.mdau.ushirika.module.member.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NextOfKinRequest(
        @NotNull @Size(min = 2, max = 2, message = "Exactly two next-of-kin entries are required")
        List<@Valid NextOfKinDto> nextOfKin
) {}
