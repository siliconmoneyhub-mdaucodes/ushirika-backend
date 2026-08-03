-- QR-based attendance check-in (Attendance Phase A). See Meeting / AttendanceRecord entities.
ALTER TABLE meetings ADD COLUMN check_in_opens_at TIMESTAMP;
ALTER TABLE meetings ADD COLUMN seated_by_at TIMESTAMP;
ALTER TABLE meetings ADD COLUMN late_fine_amount NUMERIC(10,2);
ALTER TABLE meetings ADD COLUMN absent_fine_amount NUMERIC(10,2);
ALTER TABLE meetings ADD COLUMN check_in_secret VARCHAR(100);
ALTER TABLE meetings ADD COLUMN venue_latitude DOUBLE PRECISION;
ALTER TABLE meetings ADD COLUMN venue_longitude DOUBLE PRECISION;
ALTER TABLE meetings ADD COLUMN check_in_radius_meters INTEGER NOT NULL DEFAULT 150;

ALTER TABLE attendance_records ADD COLUMN fine_id UUID REFERENCES fines(id);
ALTER TABLE attendance_records ADD COLUMN check_in_latitude DOUBLE PRECISION;
ALTER TABLE attendance_records ADD COLUMN check_in_longitude DOUBLE PRECISION;
