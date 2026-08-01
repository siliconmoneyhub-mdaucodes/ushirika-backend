-- Member <-> admin / member <-> program-coordinator conversation threads.
-- See com.mdau.ushirika.module.messaging.
CREATE TABLE conversation_threads (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id            UUID NOT NULL REFERENCES users(id),
    program_id           UUID REFERENCES programs(id),
    member_last_read_at  TIMESTAMP,
    staff_last_read_at   TIMESTAMP,
    last_message_at      TIMESTAMP,
    version              BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(150),
    updated_by           VARCHAR(150),
    CONSTRAINT uq_thread_member_program UNIQUE (member_id, program_id)
);

CREATE INDEX idx_thread_member ON conversation_threads (member_id);

CREATE INDEX idx_thread_program ON conversation_threads (program_id);

CREATE TABLE conversation_messages (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id     UUID NOT NULL REFERENCES conversation_threads(id),
    sender_id     UUID NOT NULL REFERENCES users(id),
    from_member   BOOLEAN NOT NULL,
    body          VARCHAR(2000) NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(150),
    updated_by    VARCHAR(150)
);

CREATE INDEX idx_message_thread ON conversation_messages (thread_id);
