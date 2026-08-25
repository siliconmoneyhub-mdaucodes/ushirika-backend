package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.member.enums.Country;
import com.mdau.ushirika.module.member.enums.Gender;
import com.mdau.ushirika.module.member.enums.MaritalStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record UpdateProfileRequest(

        // ── Auth user fields ──────────────────────────────────────────────────
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,

        @NotBlank
        @Pattern(regexp = "^\\+?[0-9\\s\\-().]{7,20}$", message = "Invalid phone number format")
        String phone,

        // ── Identity ──────────────────────────────────────────────────────────
        @Size(max = 30) String idNumber,
        @NotNull Gender gender,
        @NotNull LocalDate dateOfBirth,

        // ── Address ───────────────────────────────────────────────────────────
        @NotBlank @Size(max = 300) String street,
        @NotBlank @Size(max = 150) String city,
        @NotBlank @Size(max = 20)  String zipCode,
        @NotNull Country country,
        @Size(max = 100) String kenyaCounty,
        @Size(max = 100) String kenyaSubCounty,
        @Size(max = 100) String kenyaLocation,
        @Size(max = 100) String kenyaVillage,
        @Size(max = 100) String ugandaProvince,
        @Size(max = 100) String ugandaCounty,
        @Size(max = 100) String ugandaLocation,
        @Size(max = 100) String ugandaVillage,

        // ── Family ────────────────────────────────────────────────────────────
        MaritalStatus maritalStatus,
        @Size(max = 150) String spouseName,

        // ── Next of Kin / Emergency Contact — exactly two of each ───────────────
        @NotNull @Size(min = 2, max = 2, message = "Exactly two next-of-kin entries are required")
        List<@Valid NextOfKinDto> nextOfKin,

        @NotNull @Size(min = 2, max = 2, message = "Exactly two emergency contacts are required")
        List<@Valid EmergencyContactDto> emergencyContacts,

        // ── Occupation ────────────────────────────────────────────────────────
        @Size(max = 150) String occupation,
        @Size(max = 200) String employer
) {
    @AssertTrue(message = "County, sub-county, location/village, and subdivision are required for a Kenyan address")
    public boolean isKenyaRegionComplete() {
        if (country != Country.KENYA) return true;
        return notBlank(kenyaCounty) && notBlank(kenyaSubCounty) && notBlank(kenyaLocation) && notBlank(kenyaVillage);
    }

    @AssertTrue(message = "Province, county, location/village, and subdivision are required for a Ugandan address")
    public boolean isUgandaRegionComplete() {
        if (country != Country.UGANDA) return true;
        return notBlank(ugandaProvince) && notBlank(ugandaCounty) && notBlank(ugandaLocation) && notBlank(ugandaVillage);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
