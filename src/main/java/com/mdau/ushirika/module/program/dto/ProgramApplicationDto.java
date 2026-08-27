package com.mdau.ushirika.module.program.dto;

import com.mdau.ushirika.module.member.dto.BeneficiaryInfo;
import com.mdau.ushirika.module.program.entity.ProgramApplication;
import com.mdau.ushirika.module.program.enums.ProgramApplicationStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProgramApplicationDto(
        UUID id,
        UUID programId,
        String programName,
        UUID applicantId,
        String applicantName,
        String applicantEmail,
        ProgramApplicationStatus status,
        Instant appliedAt,
        Instant reviewedAt,
        String reviewedByName,
        String rejectionReason,
        List<BeneficiaryInfo> beneficiaries,
        String notes
) {
    public static ProgramApplicationDto from(ProgramApplication a) {
        return new ProgramApplicationDto(
                a.getId(),
                a.getProgram().getId(),
                a.getProgram().getName(),
                a.getApplicant().getId(),
                a.getApplicant().getFullName(),
                a.getApplicant().getEmail(),
                a.getStatus(),
                AppClock.serverInstant(a.getAppliedAt()),
                AppClock.serverInstant(a.getReviewedAt()),
                a.getReviewedBy() != null ? a.getReviewedBy().getFullName() : null,
                a.getRejectionReason(),
                a.getBeneficiaries(),
                a.getNotes()
        );
    }
}
