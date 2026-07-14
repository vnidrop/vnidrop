ALTER TABLE bugs ADD COLUMN occurred_at INTEGER;

-- Existing reports predate this field; their receipt time is the best available value.
UPDATE bugs SET occurred_at = received_at WHERE occurred_at IS NULL;
