package com.mdau.ushirika.module.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * General-purpose beneficiary captured once during onboarding — shape mirrors
 * BenevolenceBeneficiary so program-specific beneficiary forms can offer to
 * reuse these instead of re-asking the same info.
 */
public record BeneficiaryInfo(

        @NotBlank(message = "First name is required")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "Relationship is required")
        @Size(max = 50)
        String relationship,

        @Size(max = 30)
        String phoneNumber,

        LocalDate dateOfBirth
) {}
