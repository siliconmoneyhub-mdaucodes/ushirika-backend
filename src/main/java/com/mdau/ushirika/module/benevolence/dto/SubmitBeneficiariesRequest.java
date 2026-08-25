package com.mdau.ushirika.module.benevolence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record SubmitBeneficiariesRequest(
        @NotNull @Size(min = 1, max = 6) List<BeneficiaryEntry> beneficiaries
) {
    public record BeneficiaryEntry(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String relationship,
            @NotBlank @Pattern(regexp = "^\\+?[0-9\\s\\-().]{7,20}$", message = "Enter a valid phone number") String phoneNumber,
            LocalDate dateOfBirth
    ) {}
}
