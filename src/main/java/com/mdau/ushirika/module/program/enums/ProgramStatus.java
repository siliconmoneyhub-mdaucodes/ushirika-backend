package com.mdau.ushirika.module.program.enums;

/**
 * DRAFT      — created by admin, not yet visible to applicants/members.
 * ACTIVE     — visible and joinable in onboarding and the member portal.
 * CLOSED     — visible (existing members stay enrolled) but not joinable by new applicants.
 * ARCHIVED   — hidden everywhere; soft-delete.
 */
public enum ProgramStatus {
    DRAFT,
    ACTIVE,
    CLOSED,
    ARCHIVED
}
