-- Add multi-attempt support fields to results table
ALTER TABLE results
    ADD COLUMN IF NOT EXISTS session_id      UUID,
    ADD COLUMN IF NOT EXISTS total_marks     DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS attempt_number  INTEGER DEFAULT 1,
    ADD COLUMN IF NOT EXISTS submitted_at    TIMESTAMPTZ;

-- Drop unique constraint on enrollment_id to allow multiple attempts
ALTER TABLE results
    DROP CONSTRAINT IF EXISTS results_enrollment_id_key;

CREATE INDEX IF NOT EXISTS idx_results_session_id ON results(session_id);
CREATE INDEX IF NOT EXISTS idx_results_student_exam ON results(student_id, exam_id);
