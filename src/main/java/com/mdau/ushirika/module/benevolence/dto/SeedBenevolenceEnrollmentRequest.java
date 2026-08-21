package com.mdau.ushirika.module.benevolence.dto;

import com.mdau.ushirika.module.benevolence.enums.EnrollmentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Admin-only seeding of a member who was already an active Benevolence participant before this
 * platform existed -- see BenevolenceEnrollmentService#seedEnrollment(). status/amountPaid/
 * probationEndsAt describe where their real-world enrollment already stands; beneficiaries are
 * locked immediately on creation, exactly as if the member had gone through the normal
 * submit-then-lock flow themselves. */
public record SeedBenevolenceEnrollmentRequest(
        @NotBlank @Email String memberEmail,
        @NotNull @DecimalMin(value = "0.00", message = "Amount paid cannot be negative") BigDecimal amountPaid,
        @NotNull EnrollmentStatus status,
        LocalDate probationEndsAt,
        @NotNull @Size(min = 1, max = 6, message = "Between 1 and 6 beneficiaries required")
        @Valid List<SubmitBeneficiariesRequest.BeneficiaryEntry> beneficiaries
) {}
