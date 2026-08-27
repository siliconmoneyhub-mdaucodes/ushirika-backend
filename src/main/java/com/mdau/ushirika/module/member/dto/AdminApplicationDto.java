package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.member.entity.ApplicationApproval;
import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.module.member.enums.ApprovalDecision;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full admin/superadmin view — includes internal notes and approval records. */
public record AdminApplicationDto(
        UUID id,
        String referenceNumber,
        ApplicantInfo applicant,
        ApplicationStatus status,
        List<String> documentUrls,
        String rejectionReason,
        String adminNotes,
        Instant submittedAt,
        Instant reviewedAt,
        Instant approvedAt,
        List<ApprovalSummary> approvals,
        boolean onboardingComplete,
        boolean registrationFeeWaived,
        Instant registrationFeeWaivedAt,
        String registrationFeeWaivedBy
) {

    /** Unified applicant info — works for both authenticated and public submissions. */
    public record ApplicantInfo(
            String id,
            String fullName,
            String email,
            String phone,
            String memberId
    ) {
        public static ApplicantInfo fromUser(User user, String memberId) {
            return new ApplicantInfo(
                    user.getId().toString(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getPhone(),
                    memberId
            );
        }

        public static ApplicantInfo fromPublicApplication(MembershipApplication app) {
            return new ApplicantInfo(
                    null,
                    app.getApplicantName(),
                    app.getApplicantEmail(),
                    app.getApplicantPhone(),
                    null
            );
        }
    }

    /**
     * SUPERADMIN sees full detail (admin name + comment).
     * Regular ADMIN sees vote outcome only — no peer names, no comments (anonymity).
     */
    public record ApprovalSummary(
            UUID id,
            String adminName,
            ApprovalDecision decision,
            String comment,
            Instant decidedAt
    ) {
        public static ApprovalSummary forSuperAdmin(ApplicationApproval a) {
            return new ApprovalSummary(
                    a.getId(),
                    a.getAdmin().getFullName(),
                    a.getDecision(),
                    a.getComment(),
                    AppClock.serverInstant(a.getDecidedAt())
            );
        }

        public static ApprovalSummary forAdmin(ApplicationApproval a) {
            return new ApprovalSummary(
                    a.getId(),
                    null,
                    a.getDecision(),
                    null,
                    AppClock.serverInstant(a.getDecidedAt())
            );
        }
    }

    public static AdminApplicationDto from(MembershipApplication app, boolean isSuperAdmin) {
        List<ApprovalSummary> approvalSummaries = app.getApprovals().stream()
                .map(a -> isSuperAdmin
                        ? ApprovalSummary.forSuperAdmin(a)
                        : ApprovalSummary.forAdmin(a))
                .toList();

        ApplicantInfo applicantInfo = app.getUser() != null
                ? ApplicantInfo.fromUser(app.getUser(), null)
                : ApplicantInfo.fromPublicApplication(app);

        boolean onboardingComplete = app.getEmailReverifiedAt() != null
                && app.getIdentityInfoSubmittedAt() != null
                && app.getAddressInfoSubmittedAt() != null
                && app.getKinContactsSubmittedAt() != null
                && app.getConstitutionAcceptedAt() != null
                && app.getBylawsAcceptedAt() != null;

        return new AdminApplicationDto(
                app.getId(),
                app.getReferenceNumber(),
                applicantInfo,
                app.getStatus(),
                app.getDocumentUrls(),
                app.getRejectionReason(),
                isSuperAdmin ? app.getAdminNotes() : null,
                AppClock.serverInstant(app.getSubmittedAt()),
                AppClock.serverInstant(app.getReviewedAt()),
                AppClock.serverInstant(app.getApprovedAt()),
                approvalSummaries,
                onboardingComplete,
                app.isRegistrationFeeWaived(),
                AppClock.serverInstant(app.getRegistrationFeeWaivedAt()),
                app.getRegistrationFeeWaivedBy()
        );
    }
}
