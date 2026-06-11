CREATE TABLE IF NOT EXISTS exam_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(255) NOT NULL,
    student_id UUID NOT NULL,
    exam_id UUID NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR(50) NOT NULL DEFAULT 'SUBMITTED'
);

CREATE INDEX IF NOT EXISTS idx_submissions_student_exam ON exam_submissions(student_id, exam_id);
CREATE INDEX IF NOT EXISTS idx_submissions_session ON exam_submissions(session_id);
