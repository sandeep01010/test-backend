-- ============================================================
-- Data-driven exam categories + classify exams by category/test-type
-- Adding a new category = INSERT a row here (or via Admin UI) — no code change.
-- ============================================================

CREATE TABLE IF NOT EXISTS exam_categories (
    code          VARCHAR(40)  PRIMARY KEY,      -- e.g. JEE_MAIN
    title         VARCHAR(120) NOT NULL,         -- "JEE Main"
    tag           VARCHAR(80),                   -- "Engineering"
    color         VARCHAR(20),                   -- "#1a8fe3"
    description   VARCHAR(500),
    display_order INTEGER      NOT NULL DEFAULT 100,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  DEFAULT NOW()
);

-- Columns category_code, test_type, total_questions are already in V1 CREATE TABLE.
-- FK is intentionally soft (no hard constraint) so legacy rows with NULL category survive.
CREATE INDEX IF NOT EXISTS idx_exams_category   ON exams(category_code);
CREATE INDEX IF NOT EXISTS idx_exams_test_type  ON exams(test_type);
CREATE INDEX IF NOT EXISTS idx_exams_cat_type   ON exams(category_code, test_type, status);

-- Seed the initial categories
INSERT INTO exam_categories (code, title, tag, color, display_order) VALUES
    ('JEE_MAIN',     'JEE Main',     'Engineering', '#1a8fe3', 10),
    ('JEE_ADVANCED', 'JEE Advanced', 'Engineering', '#0f3460', 20),
    ('NEET',         'NEET',         'Medical',     '#27ae60', 30),
    ('CUET',         'CUET',         'University',  '#8e44ad', 40)
ON CONFLICT (code) DO NOTHING;
