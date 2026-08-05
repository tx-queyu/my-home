-- v0.13.0: 孩子能力模型 —— 单词维度
-- 部署前备份: docker exec myhome-postgres-prod pg_dump -U myhome myhome > /tmp/myhome-backup-$(date +%Y%m%d).sql
-- 幂等:可重复执行
-- 验证: SELECT COUNT(*) FROM child_word_mastery;

CREATE TABLE IF NOT EXISTS child_word_mastery (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    word_id UUID NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    family_id UUID REFERENCES families(id) ON DELETE CASCADE,
    mastery NUMERIC(5, 2) NOT NULL DEFAULT 0,
    attempts SMALLINT NOT NULL DEFAULT 0,
    passed_count SMALLINT NOT NULL DEFAULT 0,
    best_score SMALLINT NOT NULL DEFAULT 0,
    last_score SMALLINT,
    last_assessed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_child_word_mastery_user_word
    ON child_word_mastery(user_id, word_id);
CREATE INDEX IF NOT EXISTS ix_child_word_mastery_user ON child_word_mastery(user_id);
CREATE INDEX IF NOT EXISTS ix_child_word_mastery_family ON child_word_mastery(family_id);
CREATE INDEX IF NOT EXISTS ix_child_word_mastery_word ON child_word_mastery(word_id);

-- 验证表结构
SELECT 'child_word_mastery' AS tbl, COUNT(*) FROM information_schema.tables WHERE table_name = 'child_word_mastery'
UNION ALL
SELECT 'indexes', COUNT(*) FROM pg_indexes WHERE tablename = 'child_word_mastery';
