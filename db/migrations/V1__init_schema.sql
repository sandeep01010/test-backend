-- ============================================================
-- V1: Initial Schema — Exam Platform
-- ============================================================

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    phone           VARCHAR(20) UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    aadhaar_hash    VARCHAR(255),
    role            VARCHAR(20) NOT NULL DEFAULT 'STUDENT'
                        CHECK (role IN ('STUDENT','ADMIN','SUPER_ADMIN')),
    is_verified     BOOLEAN DEFAULT FALSE,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_users_email     ON users(email);
CREATE INDEX idx_users_phone     ON users(phone) WHERE phone IS NOT NULL;
CREATE INDEX idx_users_role      ON users(role);
CREATE INDEX idx_users_active    ON users(is_active) WHERE is_active = TRUE;
-- Trigram index for name search
CREATE INDEX idx_users_name_trgm ON users USING GIN ((first_name || ' ' || last_name) gin_trgm_ops);

-- ============================================================
-- EXAMS
-- ============================================================
CREATE TABLE exams (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    exam_type       VARCHAR(50) NOT NULL DEFAULT 'CUSTOM',
    created_by      UUID NOT NULL REFERENCES users(id),
    duration_mins   INT NOT NULL CHECK (duration_mins > 0),
    total_marks     INT NOT NULL CHECK (total_marks > 0),
    negative_marks  NUMERIC(4,2) DEFAULT 0.0,
    passing_marks   NUMERIC(8,2),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT','PUBLISHED','LIVE','COMPLETED','CANCELLED')),
    instructions    TEXT,
    start_time      TIMESTAMPTZ,
    end_time        TIMESTAMPTZ,
    max_attempts    INT DEFAULT 1,
    shuffle_questions BOOLEAN DEFAULT TRUE,
    show_result_immediately BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT valid_times CHECK (end_time > start_time)
);

CREATE INDEX idx_exams_status     ON exams(status);
CREATE INDEX idx_exams_start_time ON exams(start_time);
CREATE INDEX idx_exams_created_by ON exams(created_by);
CREATE INDEX idx_exams_type       ON exams(exam_type);

-- ============================================================
-- EXAM SECTIONS
-- ============================================================
CREATE TABLE exam_sections (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id         UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    subject         VARCHAR(100),
    max_questions   INT NOT NULL,
    marks_per_q     NUMERIC(4,2) NOT NULL,
    negative_marks  NUMERIC(4,2) DEFAULT 0.0,
    section_order   INT NOT NULL,
    UNIQUE(exam_id, section_order)
);

CREATE INDEX idx_sections_exam_id ON exam_sections(exam_id);

-- ============================================================
-- EXAM SLOTS
-- ============================================================
CREATE TABLE exam_slots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id         UUID NOT NULL REFERENCES exams(id),
    slot_date       DATE NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    capacity        INT NOT NULL CHECK (capacity > 0),
    enrolled_count  INT DEFAULT 0 CHECK (enrolled_count >= 0),
    center_code     VARCHAR(50),
    center_name     VARCHAR(200),
    city            VARCHAR(100),
    state           VARCHAR(100),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT capacity_check CHECK (enrolled_count <= capacity)
);

CREATE INDEX idx_slots_exam_id   ON exam_slots(exam_id);
CREATE INDEX idx_slots_date      ON exam_slots(slot_date);
CREATE INDEX idx_slots_active    ON exam_slots(exam_id, is_active) WHERE is_active = TRUE;

-- ============================================================
-- ENROLLMENTS
-- ============================================================
CREATE TABLE enrollments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL REFERENCES users(id),
    exam_id         UUID NOT NULL REFERENCES exams(id),
    slot_id         UUID NOT NULL REFERENCES exam_slots(id),
    roll_number     VARCHAR(50) UNIQUE,
    hall_ticket_url VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'ENROLLED'
                        CHECK (status IN ('ENROLLED','APPEARED','ABSENT','DISQUALIFIED','CANCELLED')),
    payment_status  VARCHAR(20) DEFAULT 'PENDING'
                        CHECK (payment_status IN ('PENDING','PAID','REFUNDED','WAIVED')),
    enrolled_at     TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(student_id, exam_id)
);

CREATE INDEX idx_enrollments_student  ON enrollments(student_id);
CREATE INDEX idx_enrollments_exam     ON enrollments(exam_id);
CREATE INDEX idx_enrollments_slot     ON enrollments(slot_id);
CREATE INDEX idx_enrollments_status   ON enrollments(status);

-- ============================================================
-- RESULTS (partitioned by exam_id hash for performance)
-- ============================================================
CREATE TABLE results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id   UUID NOT NULL REFERENCES enrollments(id),
    student_id      UUID NOT NULL REFERENCES users(id),
    exam_id         UUID NOT NULL REFERENCES exams(id),
    total_score     NUMERIC(8,2),
    section_scores  JSONB DEFAULT '{}',
    correct_count   INT DEFAULT 0,
    wrong_count     INT DEFAULT 0,
    skipped_count   INT DEFAULT 0,
    time_taken_secs INT,
    rank            INT,
    percentile      NUMERIC(6,3),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','EVALUATED','PUBLISHED','WITHHELD')),
    evaluated_at    TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(enrollment_id)
);

CREATE INDEX idx_results_exam_id    ON results(exam_id);
CREATE INDEX idx_results_student_id ON results(student_id);
CREATE INDEX idx_results_rank       ON results(exam_id, rank) WHERE rank IS NOT NULL;
CREATE INDEX idx_results_score      ON results(exam_id, total_score DESC NULLS LAST);
CREATE INDEX idx_results_status     ON results(exam_id, status);

-- ============================================================
-- SESSIONS (active exam sessions)
-- ============================================================
CREATE TABLE exam_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id   UUID NOT NULL REFERENCES enrollments(id),
    student_id      UUID NOT NULL REFERENCES users(id),
    exam_id         UUID NOT NULL REFERENCES exams(id),
    started_at      TIMESTAMPTZ DEFAULT NOW(),
    submitted_at    TIMESTAMPTZ,
    last_active_at  TIMESTAMPTZ DEFAULT NOW(),
    ip_address      INET,
    user_agent      TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','SUBMITTED','TIMED_OUT','TERMINATED')),
    UNIQUE(enrollment_id)
);

CREATE INDEX idx_sessions_student ON exam_sessions(student_id);
CREATE INDEX idx_sessions_exam    ON exam_sessions(exam_id);
CREATE INDEX idx_sessions_status  ON exam_sessions(status);

-- ============================================================
-- AUDIT LOG (monthly partitioned)
-- ============================================================
CREATE TABLE audit_log (
    id              BIGSERIAL,
    user_id         UUID,
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(50),
    resource_id     VARCHAR(100),
    ip_address      INET,
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY(id, created_at)
) PARTITION BY RANGE (created_at);

-- Create partitions for current + next 2 months
CREATE TABLE audit_log_default PARTITION OF audit_log DEFAULT;
CREATE INDEX idx_audit_user_id    ON audit_log(user_id, created_at);
CREATE INDEX idx_audit_action     ON audit_log(action, created_at);
CREATE INDEX idx_audit_created_at ON audit_log(created_at);

-- ============================================================
-- NOTIFICATIONS
-- ============================================================
CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    type            VARCHAR(50) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    message         TEXT,
    is_read         BOOLEAN DEFAULT FALSE,
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_notif_user     ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_notif_unread   ON notifications(user_id) WHERE is_read = FALSE;

-- ============================================================
-- UPDATED_AT trigger function
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_exams_updated_at
    BEFORE UPDATE ON exams
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_enrollments_updated_at
    BEFORE UPDATE ON enrollments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
