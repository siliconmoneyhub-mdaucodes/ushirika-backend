package com.mdau.ushirika.module.program.enums;

/**
 * PENDING_MEMBERSHIP — applicant selected this program during onboarding, but their
 *                      overall membership isn't approved yet. Invisible to the program
 *                      coordinator until it flips to PENDING_REVIEW.
 * PENDING_REVIEW     — membership just got approved; now visible to the program's
 *                      coordinator(s) awaiting their decision.
 * APPROVED / REJECTED — decided by a coordinator (see ProgramAdminAssignment).
 */
public enum ProgramApplicationStatus {
    PENDING_MEMBERSHIP,
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
