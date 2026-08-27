package com.mdau.ushirika.module.scholarship.dto;

import com.mdau.ushirika.module.auth.dto.UserDto;
import com.mdau.ushirika.module.member.enums.ApprovalDecision;
import com.mdau.ushirika.module.scholarship.entity.ScholarshipApplication;
import com.mdau.ushirika.module.scholarship.entity.ScholarshipApproval;
import com.mdau.ushirika.module.scholarship.enums.ScholarshipApplicationStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full admin/superadmin view — includes internal notes and per-admin votes. */
public record AdminScholarshipApplicationDto(
        UUID id,
        String referenceNumber,
        UserDto member,
        ScholarshipProgramDto program,
        ScholarshipApplicationStatus status,
        String beneficiaryName,
        String institutionName,
        String courseOfStudy,
        String academicYear,
        String personalStatement,
        List<String> documentUrls,
        String rejectionReason,
        String adminNotes,
        Instant submittedAt,
        Instant reviewedAt,
        Instant approvedAt,
        List<ApprovalSummary> approvals,
        ScholarshipApplicationTrackDto.AwardSummary award
) {

    public record ApprovalSummary(
            UUID id,
            String adminName,
            ApprovalDecision decision,
            String comment,
            Instant decidedAt
    ) {
        public static ApprovalSummary forSuperAdmin(ScholarshipApproval a) {
            return new ApprovalSummary(a.getId(), a.getAdmin().getFullName(),
                    a.getDecision(), a.getComment(), AppClock.serverInstant(a.getDecidedAt()));
        }

        public static ApprovalSummary forAdmin(ScholarshipApproval a) {
            return new ApprovalSummary(a.getId(), null,
                    a.getDecision(), null, AppClock.serverInstant(a.getDecidedAt()));
        }
    }

    public static AdminScholarshipApplicationDto from(ScholarshipApplication a, boolean isSuperAdmin) {
        List<ApprovalSummary> approvalSummaries = a.getApprovals().stream()
                .map(ap -> isSuperAdmin
                        ? ApprovalSummary.forSuperAdmin(ap)
                        : ApprovalSummary.forAdmin(ap))
                .toList();

        ScholarshipApplicationTrackDto.AwardSummary awardSummary = null;
        if (a.getAward() != null) {
            var aw = a.getAward();
            awardSummary = new ScholarshipApplicationTrackDto.AwardSummary(
                    aw.getAmountAwarded(), aw.getCurrency(),
                    aw.getMethod().name(), AppClock.serverInstant(aw.getAwardedAt())
            );
        }

        return new AdminScholarshipApplicationDto(
                a.getId(), a.getReferenceNumber(),
                UserDto.from(a.getMember()),
                ScholarshipProgramDto.from(a.getProgram()),
                a.getStatus(), a.getBeneficiaryName(),
                a.getInstitutionName(), a.getCourseOfStudy(), a.getAcademicYear(),
                a.getPersonalStatement(), a.getDocumentUrls(),
                a.getRejectionReason(),
                isSuperAdmin ? a.getAdminNotes() : null,
                AppClock.serverInstant(a.getSubmittedAt()), AppClock.serverInstant(a.getReviewedAt()), AppClock.serverInstant(a.getApprovedAt()),
                approvalSummaries, awardSummary
        );
    }
}
