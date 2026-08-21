package com.mdau.ushirika.module.contact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactMessageRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 150)
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 150)
        String email,

        @Size(max = 30)
        String phone,

        @NotBlank(message = "Subject is required")
        @Size(max = 200)
        String subject,

        @NotBlank(message = "Message body is required")
        @Size(max = 5000)
        String body,

        String captchaToken
) {}
