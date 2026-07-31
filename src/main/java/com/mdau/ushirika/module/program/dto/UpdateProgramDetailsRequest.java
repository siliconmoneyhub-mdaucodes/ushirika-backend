package com.mdau.ushirika.module.program.dto;

import com.mdau.ushirika.module.payment.enums.ContributionFrequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Submitted by whoever is allowed to edit a program's details — see ProgramService authorization checks. */
public record UpdateProgramDetailsRequest(

        @DecimalMin(value = "0.00", message = "Contribution amount cannot be negative")
        BigDecimal contributionAmount,

        ContributionFrequency contributionFrequency,

        @Size(max = 20000)
        String rules,

        List<@Size(max = 300) String> benefits,

        @Min(value = 0, message = "Max beneficiaries cannot be negative")
        Integer maxBeneficiaries
) {}
