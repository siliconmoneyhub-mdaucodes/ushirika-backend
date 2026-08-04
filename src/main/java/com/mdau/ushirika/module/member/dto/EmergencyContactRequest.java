package com.mdau.ushirika.module.member.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EmergencyContactRequest(
        @NotNull @Size(min = 2, max = 2, message = "Exactly two emergency contacts are required")
        List<@Valid EmergencyContactDto> emergencyContacts
) {}
