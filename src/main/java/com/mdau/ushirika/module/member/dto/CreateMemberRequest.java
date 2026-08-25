package com.mdau.ushirika.module.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateMemberRequest(
        @NotBlank(message = "First name is required") String firstName,
        @NotBlank(message = "Last name is required") String lastName,
        @Email(message = "A valid email address is required") @NotBlank String email,
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9\\s\\-().]{7,20}$", message = "Enter a valid phone number") String phone,
        String tier,
        boolean waiveRegistrationFee
) {}
