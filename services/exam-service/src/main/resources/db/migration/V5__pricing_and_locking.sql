-- Category/group pricing (1-year bundle access) and per-exam manual paywall toggle.
-- Both default to "free"/"unlocked" so existing data is unaffected until an admin opts in.

ALTER TABLE exam_categories  ADD COLUMN IF NOT EXISTS price_in_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE category_groups  ADD COLUMN IF NOT EXISTS price_in_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE exams            ADD COLUMN IF NOT EXISTS locked         BOOLEAN NOT NULL DEFAULT FALSE;
