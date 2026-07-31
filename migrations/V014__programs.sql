-- Joinable member programs (MGR, Benevolence, and future additions) and their
-- delegated program-admin assignments. See com.mdau.ushirika.module.program.
CREATE TABLE programs (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                     VARCHAR(150) NOT NULL,
    slug                     VARCHAR(160) NOT NULL UNIQUE,
    short_description        VARCHAR(500) NOT NULL,
    type                     VARCHAR(20)  NOT NULL
                                 CHECK (type IN ('MGR', 'BENEVOLENCE', 'CUSTOM')),
    status                   VARCHAR(10)  NOT NULL DEFAULT 'DRAFT'
                                 CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED')),
    contribution_amount      NUMERIC(12,2),
    contribution_frequency   VARCHAR(15)
                                 CHECK (contribution_frequency IN ('MONTHLY', 'QUARTERLY', 'BIANNUAL', 'ANNUAL', 'ONCE_OFF')),
    rules                    TEXT,
    benefits                 JSONB NOT NULL DEFAULT '[]',
    max_beneficiaries        INTEGER,
    created_by_id            UUID REFERENCES users(id),
    version                  BIGINT NOT NULL DEFAULT 0,
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by               VARCHAR(150),
    updated_by               VARCHAR(150)
);

CREATE INDEX idx_program_status ON programs (status);

CREATE TABLE program_admin_assignments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_id      UUID NOT NULL REFERENCES programs(id),
    user_id         UUID NOT NULL REFERENCES users(id),
    assigned_by_id  UUID REFERENCES users(id),
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(150),
    updated_by      VARCHAR(150),
    CONSTRAINT uq_program_admin UNIQUE (program_id, user_id)
);

CREATE INDEX idx_paa_program ON program_admin_assignments (program_id);
CREATE INDEX idx_paa_user    ON program_admin_assignments (user_id);
