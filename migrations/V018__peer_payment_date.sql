-- What the member claims as the payment date — compared against createdAt (server-recorded
-- submission time) and the screenshot's own visible date during admin verification.
ALTER TABLE peer_payments ADD COLUMN payment_date DATE;
