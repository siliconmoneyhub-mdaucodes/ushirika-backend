package com.mdau.ushirika.module.member.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdditionalInfoRequest(

        /** Optional for now — document upload is disabled on the frontend pending a finalized spec. */
        List<String> documentUrls,

        @Size(max = 50, message = "Please pick how you heard about us")
        String heardAboutUs,

        @Valid
        List<BeneficiaryInfo> beneficiaries
) {}
