-- Adds GENERAL_CONTRIBUTION to the payment_basket_lines.ledger check constraint
-- (Phase 2 of the card-only payment migration — folds the standalone Contribute
-- button into the "pay my balances" basket).
ALTER TABLE payment_basket_lines DROP CONSTRAINT payment_basket_lines_ledger_check

ALTER TABLE payment_basket_lines ADD CONSTRAINT payment_basket_lines_ledger_check
    CHECK (ledger IN ('REGISTRATION_FEE', 'DUES', 'BENEVOLENCE_ENROLLMENT',
                       'MGR_CONTRIBUTION', 'FINE', 'BENEVOLENCE_REPLENISHMENT',
                       'PROGRAM_APPLICATION_PREPAY', 'GENERAL_CONTRIBUTION'))
