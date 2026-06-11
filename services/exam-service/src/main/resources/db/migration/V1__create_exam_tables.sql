-- ============================================================
-- Exam Service Schema — must match JPA entities exactly
-- ============================================================

CREATE TABLE IF NOT EXISTS exams (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(500)      NOT NULL,
    description      TEXT,
    exam_type        VARCHAR(100)      NOT NULL,
    created_by       UUID              NOT NULL,
    duration_mins    INTEGER           NOT NULL,
    total_marks      INTEGER           NOT NULL,
    negative_marks   DOUBLE PRECISION  NOT NULL DEFAULT 0,
    passing_marks    DOUBLE PRECISION,
    status           VARCHAR(20)       NOT NULL DEFAULT 'DRAFT',
    instructions     TEXT,
    start_time       TIMESTAMPTZ,
    end_time         TIMESTAMPTZ,
    shuffle_questions          BOOLEAN NOT NULL DEFAULT TRUE,
    show_result_immediately    BOOLEAN NOT NULL DEFAULT FALSE,
    category_code    VARCHAR(40),
    test_type        VARCHAR(40),
    total_questions  INTEGER           NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ       DEFAULT NOW(),
    updated_at       TIMESTAMPTZ       DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS exam_sections (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id        UUID             NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    name           VARCHAR(200)     NOT NULL,
    subject        VARCHAR(100),
    max_questions  INTEGER          NOT NULL,
    marks_per_q    DOUBLE PRECISION NOT NULL,
    negative_marks DOUBLE PRECISION NOT NULL DEFAULT 0,
    section_order  INTEGER          NOT NULL
);

CREATE TABLE IF NOT EXISTS exam_slots (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id        UUID         NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    slot_date      DATE         NOT NULL,
    start_time     TIMESTAMPTZ  NOT NULL,
    end_time       TIMESTAMPTZ  NOT NULL,
    capacity       INTEGER      NOT NULL DEFAULT 1000,
    enrolled_count INTEGER      NOT NULL DEFAULT 0,
    center_name    VARCHAR(500),
    center_city    VARCHAR(200),
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS enrollments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id   UUID         NOT NULL,
    exam_id      UUID         NOT NULL REFERENCES exams(id),
    slot_id      UUID         NOT NULL REFERENCES exam_slots(id),
    roll_number  VARCHAR(20)  NOT NULL UNIQUE,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ENROLLED',
    created_at   TIMESTAMPTZ  DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  DEFAULT NOW(),
    UNIQUE (student_id, exam_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_exams_status         ON exams(status);
CREATE INDEX IF NOT EXISTS idx_exams_start_time     ON exams(start_time);
CREATE INDEX IF NOT EXISTS idx_exams_created_by     ON exams(created_by);
CREATE INDEX IF NOT EXISTS idx_exam_sections_exam   ON exam_sections(exam_id);
CREATE INDEX IF NOT EXISTS idx_exam_slots_exam      ON exam_slots(exam_id);
CREATE INDEX IF NOT EXISTS idx_exam_slots_date      ON exam_slots(slot_date);
CREATE INDEX IF NOT EXISTS idx_enrollments_student  ON enrollments(student_id);
CREATE INDEX IF NOT EXISTS idx_enrollments_exam     ON enrollments(exam_id);
