package com.mdau.ushirika.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ActivationTokenRequest(
        @NotBlank(message = "Setup link is required")
        String token
) {}
