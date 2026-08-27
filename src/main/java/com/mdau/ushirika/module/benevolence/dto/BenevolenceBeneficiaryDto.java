package com.mdau.ushirika.module.benevolence.dto;

import com.mdau.ushirika.module.benevolence.entity.BenevolenceBeneficiary;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BenevolenceBeneficiaryDto(
        UUID id,
        String firstName,
        String lastName,
        String relationship,
        String phoneNumber,
        LocalDate dateOfBirth,
        boolean deceased,
        Instant deceasedAt,
        String adminNotes
) {
    public static BenevolenceBeneficiaryDto from(BenevolenceBeneficiary b) {
        return new BenevolenceBeneficiaryDto(
                b.getId(), b.getFirstName(), b.getLastName(), b.getRelationship(),
                b.getPhoneNumber(), b.getDateOfBirth(), b.isDeceased(),
                AppClock.serverInstant(b.getDeceasedAt()), b.getAdminNotes()
        );
    }
}
