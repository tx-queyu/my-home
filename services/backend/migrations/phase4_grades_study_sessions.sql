-- v0.17.0 学科成绩 + 学习时长会话
-- dev 由 create_all 自动建表;prod lifespan 也跑 create_all,本迁移为惯例保险 + schema 文档化。
-- 幂等:无 ALTER TYPE,可安全重跑。

CREATE TABLE IF NOT EXISTS grades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    subject VARCHAR(32) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    score_full DOUBLE PRECISION NOT NULL DEFAULT 100,
    exam_name VARCHAR(128),
    exam_date DATE NOT NULL,
    note VARCHAR(256),
    assignee_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_grades_family_id ON grades (family_id);
CREATE INDEX IF NOT EXISTS ix_grades_assignee ON grades (assignee_user_id);
CREATE INDEX IF NOT EXISTS ix_grades_family_subject ON grades (family_id, subject);

CREATE TABLE IF NOT EXISTS study_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject VARCHAR(32) NOT NULL,
    textbook VARCHAR(64) NOT NULL,
    learning_method VARCHAR(32) NOT NULL,
    session_type VARCHAR(16) NOT NULL,
    source VARCHAR(16) NOT NULL,
    duration_seconds INTEGER NOT NULL,
    session_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_study_sessions_user_date ON study_sessions (user_id, session_date);
