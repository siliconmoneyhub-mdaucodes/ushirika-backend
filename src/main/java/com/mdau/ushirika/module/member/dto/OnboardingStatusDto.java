package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;

import java.time.LocalDateTime;
import java.util.List;

/** Applicant-facing view of onboarding progress — drives the onboarding wizard's step state. */
public record OnboardingStatusDto(
        String referenceNumber,
        ApplicationStatus status,
        boolean emailVerified,
        boolean additionalInfoSubmitted,
        String heardAboutUs,
        List<BeneficiaryInfo> beneficiaries,
        boolean bylawsAccepted,
        boolean registrationSubmitted,
        LocalDateTime formSentAt
) {
    public static OnboardingStatusDto from(MembershipApplication app) {
        return new OnboardingStatusDto(
                app.getReferenceNumber(),
                app.getStatus(),
                app.getEmailReverifiedAt() != null,
                app.getAdditionalInfoSubmittedAt() != null,
                app.getHeardAboutUs(),
                app.getBeneficiaries(),
                app.getBylawsAcceptedAt() != null,
                app.getRegistrationSubmittedAt() != null,
                app.getFormSentAt()
        );
    }
}
