package com.mdau.ushirika.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivationVerifyRequest(
        @NotBlank(message = "Setup link is required")
        String token,

        @NotBlank(message = "Confirmation code is required")
        @Size(min = 6, max = 6, message = "The confirmation code is 6 digits")
        String otp
) {}
