package com.mdau.ushirika.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record MagicLoginRequest(

        @NotBlank(message = "Token is required")
        String token
) {}
