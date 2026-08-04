package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.member.enums.Gender;
import com.mdau.ushirika.module.member.enums.MaritalStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;
import java.util.List;

public record IdentityInfoRequest(

        @NotBlank(message = "National ID number is required")
        String idNumber,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @NotNull(message = "Gender is required")
        Gender gender,

        @NotNull(message = "Marital status is required")
        MaritalStatus maritalStatus,

        /** Required when maritalStatus is MARRIED. */
        String spouseName,

        List<ChildRecord> children,

        String occupation,

        String employer

) {
    @AssertTrue(message = "Spouse's name is required when marital status is Married")
    public boolean isSpouseNameProvidedWhenMarried() {
        if (maritalStatus != MaritalStatus.MARRIED) return true;
        return spouseName != null && !spouseName.isBlank();
    }

    public record ChildRecord(String name, LocalDate dateOfBirth) {}
}
