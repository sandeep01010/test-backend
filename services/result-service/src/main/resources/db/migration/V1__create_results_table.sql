-- ============================================================
-- Result Service Schema — must match Result entity exactly
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id   UUID             UNIQUE,
    student_id      UUID             NOT NULL,
    exam_id         UUID             NOT NULL,
    total_score     DOUBLE PRECISION,
    section_scores  JSONB,
    correct_count   INTEGER,
    wrong_count     INTEGER,
    skipped_count   INTEGER,
    time_taken_secs INTEGER,
    rank            INTEGER,
    percentile      DOUBLE PRECISION,
    status          VARCHAR(20)      NOT NULL DEFAULT 'PENDING',
    evaluated_at    TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ      DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_results_exam_id       ON results(exam_id);
CREATE INDEX IF NOT EXISTS idx_results_student_id    ON results(student_id);
CREATE INDEX IF NOT EXISTS idx_results_exam_rank     ON results(exam_id, rank);
CREATE INDEX IF NOT EXISTS idx_results_enrollment    ON results(enrollment_id);
CREATE INDEX IF NOT EXISTS idx_results_status        ON results(status);
