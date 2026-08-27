package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.List;

/** Applicant-facing view of onboarding progress — drives the onboarding wizard's step state. */
public record OnboardingStatusDto(
        String referenceNumber,
        ApplicationStatus status,
        boolean emailVerified,
        boolean identitySubmitted,
        boolean addressSubmitted,
        boolean kinContactsSubmitted,
        boolean additionalInfoSubmitted,
        String heardAboutUs,
        List<BeneficiaryInfo> beneficiaries,
        boolean constitutionAccepted,
        boolean bylawsAccepted,
        boolean registrationSubmitted,
        Instant formSentAt,
        boolean registrationFeeWaived
) {
    public static OnboardingStatusDto from(MembershipApplication app) {
        return new OnboardingStatusDto(
                app.getReferenceNumber(),
                app.getStatus(),
                app.getEmailReverifiedAt() != null,
                app.getIdentityInfoSubmittedAt() != null,
                app.getAddressInfoSubmittedAt() != null,
                app.getKinContactsSubmittedAt() != null,
                app.getAdditionalInfoSubmittedAt() != null,
                app.getHeardAboutUs(),
                app.getBeneficiaries(),
                app.getConstitutionAcceptedAt() != null,
                app.getBylawsAcceptedAt() != null,
                app.getRegistrationSubmittedAt() != null,
                AppClock.serverInstant(app.getFormSentAt()),
                app.isRegistrationFeeWaived()
        );
    }
}
