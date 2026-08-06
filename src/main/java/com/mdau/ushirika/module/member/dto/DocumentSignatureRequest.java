package com.mdau.ushirika.module.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Manually-typed signature accompanying Constitution/Bylaws acceptance -- treated as the
 * applicant's actual signature, not just a click. */
public record DocumentSignatureRequest(

        @NotBlank(message = "Please type your full name to sign")
        String fullName,

        @NotBlank(message = "Please type your initials to sign")
        String initials,

        @NotNull(message = "Please enter today's date to sign")
        LocalDate date
) {}
