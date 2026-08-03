-- Member apology workflow for LATE/ABSENT attendance (Attendance Phase C).
-- See AttendanceExcuse entity / AttendanceExcuseService.
CREATE TABLE attendance_excuses (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attendance_record_id  UUID NOT NULL REFERENCES attendance_records(id),
    reason                TEXT NOT NULL,
    evidence_url          VARCHAR(500),
    status                VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    admin_notes           TEXT,
    decided_by_id         UUID REFERENCES users(id),
    decided_at            TIMESTAMP,
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(150),
    updated_by            VARCHAR(150)
);

CREATE INDEX idx_excuse_record ON attendance_excuses (attendance_record_id);
CREATE INDEX idx_excuse_status ON attendance_excuses (status);
