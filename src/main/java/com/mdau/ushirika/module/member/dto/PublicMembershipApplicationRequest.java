package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.member.enums.UsState;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PublicMembershipApplicationRequest(
        @NotBlank(message = "First name is required") String firstName,
        @Size(max = 100) String middleName,
        @NotBlank(message = "Last name is required")  String lastName,
        @Email(message = "Valid email is required") @NotBlank String email,
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9\\s\\-().]{7,20}$", message = "Enter a valid phone number") String phone,
        @NotBlank(message = "Street address is required") String street,
        @NotBlank(message = "City is required") String city,
        @NotNull(message = "State is required") UsState state,
        @NotBlank(message = "Zip code is required")
        @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "Enter a valid US zip code, e.g. 75201 or 75201-1234") String zipCode,
        @NotBlank(message = "Kenya county is required") String kenyaCounty,
        @NotBlank(message = "Sub-tribe is required") String subtribe,
        @NotBlank(message = "Eligibility is required") String eligibility,
        String captchaToken,
        String captchaNonce,
        String honeypot
) {}
