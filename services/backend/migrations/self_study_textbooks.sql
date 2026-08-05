-- v0.16.1 家长自学教材清单(dev 由 create_all 自动建表,prod 跑本迁移,幂等)
CREATE TABLE IF NOT EXISTS self_study_textbooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject VARCHAR(32) NOT NULL,
    textbook VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_self_study_textbooks_user
    ON self_study_textbooks (user_id, subject, textbook);
