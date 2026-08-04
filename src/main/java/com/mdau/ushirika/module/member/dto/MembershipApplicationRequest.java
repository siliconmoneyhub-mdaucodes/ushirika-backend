package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.member.enums.Gender;
import com.mdau.ushirika.module.member.enums.MaritalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;
import java.util.List;

public record MembershipApplicationRequest(

        // ── Identity ──────────────────────────────────────────────────────────

        @NotBlank(message = "National ID number is required")
        String idNumber,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @NotNull(message = "Gender is required")
        Gender gender,

        // ── Family ────────────────────────────────────────────────────────────

        MaritalStatus maritalStatus,

        /** Required when maritalStatus is MARRIED. */
        String spouseName,

        List<ChildRecord> children,

        // ── Occupation ────────────────────────────────────────────────────────

        String occupation,

        String employer,

        // ── References ────────────────────────────────────────────────────────

        String reference1Name,

        /** Member ID of first reference, format UW-YYYY-XXXX. */
        String reference1MemberId,

        String reference2Name,

        /** Member ID of second reference, format UW-YYYY-XXXX. */
        String reference2MemberId,

        // ── Discovery ─────────────────────────────────────────────────────────

        String heardAboutUs,

        // ── Agreements ────────────────────────────────────────────────────────

        boolean agreedToConstitution,
        boolean agreedToDues,
        boolean certifiedAccurate,

        // ── Documents ─────────────────────────────────────────────────────────

        /** Cloudinary URLs for supporting documents uploaded before submission. */
        List<String> documentUrls

) {
    public record ChildRecord(String name, LocalDate dateOfBirth) {}
}
