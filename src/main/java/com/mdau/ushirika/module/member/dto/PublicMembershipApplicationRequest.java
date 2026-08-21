package com.mdau.ushirika.module.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PublicMembershipApplicationRequest(
        @NotBlank(message = "First name is required") String firstName,
        @NotBlank(message = "Last name is required")  String lastName,
        @Email(message = "Valid email is required") @NotBlank String email,
        @NotBlank(message = "Phone number is required") String phone,
        @NotBlank(message = "Street address is required") String street,
        @NotBlank(message = "City is required") String city,
        @NotBlank(message = "State is required") String state,
        @NotBlank(message = "Zip code is required") String zipCode,
        @NotBlank(message = "Kenya county is required") String kenyaCounty,
        @NotBlank(message = "Sub-tribe is required") String subtribe,
        @NotBlank(message = "Eligibility is required") String eligibility,
        String captchaToken,
        Integer captchaAnswer,
        String honeypot
) {}
