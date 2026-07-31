package com.mdau.ushirika.module.program.dto;

import com.mdau.ushirika.module.payment.enums.ContributionFrequency;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.enums.ProgramType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Public/onboarding-safe view — no admin assignment info, only ACTIVE programs are ever exposed via this DTO. */
public record PublicProgramDto(
        UUID id,
        String name,
        String slug,
        String shortDescription,
        ProgramType type,
        BigDecimal contributionAmount,
        ContributionFrequency contributionFrequency,
        String rules,
        List<String> benefits,
        Integer maxBeneficiaries
) {
    public static PublicProgramDto from(Program p) {
        return new PublicProgramDto(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getShortDescription(),
                p.getType(),
                p.getContributionAmount(),
                p.getContributionFrequency(),
                p.getRules(),
                p.getBenefits(),
                p.getMaxBeneficiaries()
        );
    }
}
