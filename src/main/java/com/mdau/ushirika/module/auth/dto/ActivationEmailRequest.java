package com.mdau.ushirika.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ActivationEmailRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        String email
) {}
