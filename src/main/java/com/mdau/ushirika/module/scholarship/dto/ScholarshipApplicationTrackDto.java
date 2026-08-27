package com.mdau.ushirika.module.scholarship.dto;

import com.mdau.ushirika.module.scholarship.entity.ScholarshipApplication;
import com.mdau.ushirika.module.scholarship.enums.ScholarshipApplicationStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Safe member-facing view — no admin names, no internal notes. */
public record ScholarshipApplicationTrackDto(
        UUID id,
        String referenceNumber,
        ScholarshipProgramDto program,
        ScholarshipApplicationStatus status,
        String beneficiaryName,
        String institutionName,
        String courseOfStudy,
        String academicYear,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewedAt,
        Instant approvedAt,
        AwardSummary award
) {

    public record AwardSummary(
            BigDecimal amountAwarded,
            String currency,
            String method,
            Instant awardedAt
    ) {}

    public static ScholarshipApplicationTrackDto from(ScholarshipApplication a) {
        AwardSummary awardSummary = null;
        if (a.getAward() != null) {
            var aw = a.getAward();
            awardSummary = new AwardSummary(
                    aw.getAmountAwarded(), aw.getCurrency(),
                    aw.getMethod().name(), AppClock.serverInstant(aw.getAwardedAt())
            );
        }
        return new ScholarshipApplicationTrackDto(
                a.getId(), a.getReferenceNumber(),
                ScholarshipProgramDto.from(a.getProgram()),
                a.getStatus(), a.getBeneficiaryName(),
                a.getInstitutionName(), a.getCourseOfStudy(), a.getAcademicYear(),
                a.getRejectionReason(),
                AppClock.serverInstant(a.getSubmittedAt()), AppClock.serverInstant(a.getReviewedAt()), AppClock.serverInstant(a.getApprovedAt()),
                awardSummary
        );
    }
}
