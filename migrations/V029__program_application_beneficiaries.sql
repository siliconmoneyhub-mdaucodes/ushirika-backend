ALTER TABLE program_applications ADD COLUMN beneficiaries JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE program_applications ADD COLUMN notes VARCHAR(1000);
