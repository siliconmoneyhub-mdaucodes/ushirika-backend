package com.mdau.ushirika.module.program.dto;

import com.mdau.ushirika.module.member.dto.BeneficiaryInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/** A verified member applying to join a program directly from the portal. */
public record ApplyToProgramRequest(
        @Valid List<BeneficiaryInfo> beneficiaries,
        @Size(max = 1000) String notes
) {}
