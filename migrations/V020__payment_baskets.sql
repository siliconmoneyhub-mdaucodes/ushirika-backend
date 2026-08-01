-- Multi-line-item Stripe checkout baskets (card-only payment migration, Phase 0).
-- See com.mdau.ushirika.module.payment.entity.PaymentBasket / PaymentBasketLine.
CREATE TABLE payment_baskets (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id    UUID NOT NULL REFERENCES users(id),
    session_id   VARCHAR(100) NOT NULL UNIQUE,
    status       VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    paid_at      TIMESTAMP,
    version      BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(150),
    updated_by   VARCHAR(150)
);

CREATE INDEX idx_basket_member ON payment_baskets (member_id);

CREATE TABLE payment_basket_lines (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    basket_id    UUID NOT NULL REFERENCES payment_baskets(id),
    ledger       VARCHAR(30) NOT NULL
                     CHECK (ledger IN ('REGISTRATION_FEE', 'DUES', 'BENEVOLENCE_ENROLLMENT',
                                       'MGR_CONTRIBUTION', 'FINE', 'BENEVOLENCE_REPLENISHMENT')),
    target_id    UUID,
    description  VARCHAR(200) NOT NULL,
    amount       NUMERIC(12,2) NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(150),
    updated_by   VARCHAR(150)
);

CREATE INDEX idx_basket_line_basket ON payment_basket_lines (basket_id);

ALTER TABLE program_applications ADD COLUMN prepaid_amount NUMERIC(12,2) DEFAULT 0;
