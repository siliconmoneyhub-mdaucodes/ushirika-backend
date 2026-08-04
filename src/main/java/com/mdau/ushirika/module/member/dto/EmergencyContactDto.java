package com.mdau.ushirika.module.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmergencyContactDto(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 20)  String phone,
        @NotBlank @Size(max = 50)  String relationship
) {}
