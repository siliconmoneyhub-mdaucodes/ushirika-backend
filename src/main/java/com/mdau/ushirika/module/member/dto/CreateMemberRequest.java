package com.mdau.ushirika.module.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateMemberRequest(
        @NotBlank(message = "First name is required") String firstName,
        @NotBlank(message = "Last name is required") String lastName,
        @Email(message = "A valid email address is required") @NotBlank String email,
        @NotBlank(message = "Phone number is required") String phone,
        String tier,
        boolean waiveRegistrationFee
) {}
