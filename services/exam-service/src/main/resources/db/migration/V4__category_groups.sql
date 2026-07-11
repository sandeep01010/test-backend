-- Combined category groups (e.g. "JEE Mains + Advanced" = JEE_MAIN + JEE_ADVANCED).
-- A group's papers are the UNION of its member categories' papers, by test type — purely
-- a browsing/aggregation layer over existing exams. No exam ever changes its own
-- category_code; membership here only controls what students see grouped together.
-- A category can belong to multiple groups at once (many-to-many).

CREATE TABLE IF NOT EXISTS category_groups (
    code          VARCHAR(40)  PRIMARY KEY,
    title         VARCHAR(120) NOT NULL,
    tag           VARCHAR(80),
    color         VARCHAR(20),
    description   VARCHAR(500),
    display_order INTEGER      NOT NULL DEFAULT 100,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS category_group_members (
    group_code    VARCHAR(40) NOT NULL REFERENCES category_groups(code) ON DELETE CASCADE,
    category_code VARCHAR(40) NOT NULL REFERENCES exam_categories(code) ON DELETE CASCADE,
    PRIMARY KEY (group_code, category_code)
);

CREATE INDEX IF NOT EXISTS idx_group_members_category ON category_group_members(category_code);
