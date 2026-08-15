package com.mdau.ushirika.module.payment.enums;

/** Which single Stripe payment method a checkout session should be scoped to — lets the
 * frontend offer distinct "Pay with Card" / "Pay with Cash App" buttons that each open Stripe
 * Checkout showing only that one method, rather than a combined page the payer has to search. */
public enum PreferredPaymentMethod {
    CARD,
    CASH_APP
}
