package com.mdau.ushirika.module.benevolence.dto;

import jakarta.validation.constraints.Size;

/** Shared payload for send-form/approve/reject on a BenevolenceJoinRequest -- adminNotes is
 * optional in every case (a required reason for rejection specifically wasn't asked for, unlike
 * program-application rejections elsewhere, so this stays a single flexible notes field). */
public record DecideBenevolenceJoinRequest(
        @Size(max = 500) String adminNotes
) {}
