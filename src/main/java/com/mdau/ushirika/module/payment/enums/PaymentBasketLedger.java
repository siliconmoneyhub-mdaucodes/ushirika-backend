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
    GENERAL_CONTRIBUTION,
    /** An admin processing a cash payment on a member's behalf — the admin is the actual Stripe
     * payer, but the amount settles the named member's obligations via PaymentAllocationService,
     * same as any other pooled ledger. */
    CASH_PAYMENT,
    /** An admin entering the member's own card details directly (relayed by phone or in person)
     * via Stripe Elements — the member's card pays, not the admin's. Kept distinct from
     * CASH_PAYMENT so reports can tell the two apart; pooled the same way otherwise. */
    CARD_ENTERED_BY_ADMIN
}
