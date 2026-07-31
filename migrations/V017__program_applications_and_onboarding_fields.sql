-- Program applications submitted during onboarding — invisible to the program's
-- coordinator until the applicant's overall membership is approved.
CREATE TABLE program_applications (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_id       UUID NOT NULL REFERENCES programs(id),
    applicant_id     UUID NOT NULL REFERENCES users(id),
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING_MEMBERSHIP'
                         CHECK (status IN ('PENDING_MEMBERSHIP', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    applied_at       TIMESTAMP NOT NULL,
    reviewed_at      TIMESTAMP,
    reviewed_by_id   UUID REFERENCES users(id),
    rejection_reason VARCHAR(500),
    version          BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(150),
    updated_by       VARCHAR(150),
    CONSTRAINT uq_program_application UNIQUE (program_id, applicant_id)
);

CREATE INDEX idx_pa_program        ON program_applications (program_id);
CREATE INDEX idx_pa_applicant      ON program_applications (applicant_id);
CREATE INDEX idx_pa_program_status ON program_applications (program_id, status);

-- Additional-info step: document upload is now optional, beneficiaries + heard-about-us added.
ALTER TABLE membership_applications ADD COLUMN additional_info_submitted_at TIMESTAMP;
ALTER TABLE membership_applications ADD COLUMN heard_about_us VARCHAR(50);
ALTER TABLE membership_applications ADD COLUMN beneficiaries JSONB NOT NULL DEFAULT '[]';

-- Backfill: any application that already has additional-info documents was, by definition, already submitted.
UPDATE membership_applications
SET additional_info_submitted_at = updated_at
WHERE additional_info_document_urls IS NOT NULL
  AND additional_info_document_urls::text != '[]';

-- Peer payments: optional screenshot proof.
ALTER TABLE peer_payments ADD COLUMN proof_image_url VARCHAR(1000);
