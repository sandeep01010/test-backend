-- Practice tests (mocks, subject/chapter-wise) have no scheduled slot.
-- Allow slot-less enrollments and track re-attempts.

ALTER TABLE enrollments ALTER COLUMN slot_id DROP NOT NULL;

ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;
