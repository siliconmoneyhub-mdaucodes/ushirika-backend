package com.mdau.ushirika.module.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmergencyContactDto(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 20) @Pattern(regexp = "^\\+?[0-9\\s\\-().]{7,20}$", message = "Enter a valid phone number") String phone,
        @NotBlank @Size(max = 50)  String relationship
) {}
