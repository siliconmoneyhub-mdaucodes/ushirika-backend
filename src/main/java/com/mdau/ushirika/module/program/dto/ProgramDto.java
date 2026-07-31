package com.mdau.ushirika.module.program.dto;

import com.mdau.ushirika.module.payment.enums.ContributionFrequency;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.enums.ProgramStatus;
import com.mdau.ushirika.module.program.enums.ProgramType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Full admin view of a Program, including fields not yet public-safe (e.g. DRAFT programs). */
public record ProgramDto(
        UUID id,
        String name,
        String slug,
        String shortDescription,
        ProgramType type,
        ProgramStatus status,
        BigDecimal contributionAmount,
        ContributionFrequency contributionFrequency,
        String rules,
        List<String> benefits,
        Integer maxBeneficiaries,
        List<ProgramAdminDto> admins
) {
    public static ProgramDto from(Program p, List<ProgramAdminDto> admins) {
        return new ProgramDto(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getShortDescription(),
                p.getType(),
                p.getStatus(),
                p.getContributionAmount(),
                p.getContributionFrequency(),
                p.getRules(),
                p.getBenefits(),
                p.getMaxBeneficiaries(),
                admins
        );
    }
}
