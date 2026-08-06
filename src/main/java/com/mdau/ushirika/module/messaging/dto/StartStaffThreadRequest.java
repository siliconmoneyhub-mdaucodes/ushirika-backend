package com.mdau.ushirika.module.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Staff-initiated conversation — admin or program coordinator messaging a specific member first. */
public record StartStaffThreadRequest(
        @NotNull UUID memberId,
        @NotBlank @Size(max = 2000) String body
) {}
