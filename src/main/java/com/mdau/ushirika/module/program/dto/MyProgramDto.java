package com.mdau.ushirika.module.program.dto;

import com.mdau.ushirika.module.payment.enums.ContributionFrequency;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.entity.ProgramApplication;
import com.mdau.ushirika.module.program.enums.ProgramApplicationStatus;
import com.mdau.ushirika.module.program.enums.ProgramType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Member-portal view of a program plus this member's own membership state in it. */
public record MyProgramDto(
        UUID id,
        String name,
        String slug,
        String shortDescription,
        ProgramType type,
        BigDecimal contributionAmount,
        ContributionFrequency contributionFrequency,
        String rules,
        List<String> benefits,
        Integer maxBeneficiaries,

        // this member's relationship to the program — null myApplicationStatus means never applied
        UUID myApplicationId,
        ProgramApplicationStatus myApplicationStatus
) {
    public static MyProgramDto from(Program p, ProgramApplication myApplication) {
        return new MyProgramDto(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getShortDescription(),
                p.getType(),
                p.getContributionAmount(),
                p.getContributionFrequency(),
                p.getRules(),
                p.getBenefits(),
                p.getMaxBeneficiaries(),
                myApplication != null ? myApplication.getId() : null,
                myApplication != null ? myApplication.getStatus() : null
        );
    }
}
