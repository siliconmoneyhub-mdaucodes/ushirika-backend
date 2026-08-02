package com.mdau.ushirika.module.payment.enums;

/**
 * Which internal ledger a PaymentBasketLine credits once its Stripe checkout session completes.
 * FINE and BENEVOLENCE_REPLENISHMENT target a specific row (PaymentBasketLine.targetId); the
 * others are resolved per-member (dues year, enrollment, earliest-pending contribution month).
 */
public enum PaymentBasketLedger {
    REGISTRATION_FEE,
    DUES,
    BENEVOLENCE_ENROLLMENT,
    MGR_CONTRIBUTION,
    FINE,
    BENEVOLENCE_REPLENISHMENT,
    /** Onboarding-only: credits ProgramApplication.prepaidAmount instead of a real
     * BenevolenceEnrollment, since enrollment can't exist until a coordinator approves. */
    PROGRAM_APPLICATION_PREPAY,
    /** General/discretionary giving, optionally tied to a ContributionPlan — folded into the
     * "pay my balances" basket instead of the old standalone Contribute flow. */
    GENERAL_CONTRIBUTION
}
