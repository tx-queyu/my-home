-- v0.14.0 — Lexicon 全局词表 + ChildWordMastery 全局化(word_id → lexeme_id)
--
-- 目标:孩子的能力模型从「per-course」切换到「per-lexeme」,实现「掌握了 500 词就是 500 词」。
-- 同一个 spelling 的词在多个课程(KET/PET/...)共享同一条 lexicon + 同一条 mastery 记录。
--
-- 幂等:所有语句 IF NOT EXISTS / ON CONFLICT DO NOTHING,可重复跑。
-- prod 跑前建议:pg_dump -U myhome myhome > /opt/myhome-backup-$(date +%Y%m%d-%H%M%S).sql

BEGIN;

-- ============================================================
-- Step 1: 建 lexicon 全局词表
-- ============================================================
CREATE TABLE IF NOT EXISTS lexicon (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    spelling VARCHAR(64) NOT NULL,
    phonetic VARCHAR(128),
    meaning_cn VARCHAR(512),
    first_letter VARCHAR(1) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_lexicon_spelling ON lexicon(spelling);
CREATE INDEX IF NOT EXISTS ix_lexicon_first_letter ON lexicon(first_letter);

-- ============================================================
-- Step 2: 从 words 回填 lexicon(按 LOWER(spelling) 去重)
-- 同一个 spelling 在多个课程出现时,DISTINCT ON 取第一条
-- ============================================================
INSERT INTO lexicon (spelling, phonetic, meaning_cn, first_letter)
SELECT DISTINCT ON (LOWER(spelling))
    LOWER(spelling),
    phonetic,
    meaning_cn,
    LOWER(SUBSTRING(spelling FROM 1 FOR 1))
FROM words
WHERE is_active = TRUE
  AND spelling IS NOT NULL
  AND LENGTH(spelling) > 0
ON CONFLICT (spelling) DO NOTHING;

-- ============================================================
-- Step 3: words 加 lexeme_id 列 + 回填 + 加索引
-- ============================================================
ALTER TABLE words ADD COLUMN IF NOT EXISTS lexeme_id UUID REFERENCES lexicon(id) ON DELETE SET NULL;

UPDATE words w SET lexeme_id = l.id
FROM lexicon l
WHERE LOWER(w.spelling) = l.spelling
  AND w.lexeme_id IS NULL;

CREATE INDEX IF NOT EXISTS ix_words_lexeme ON words(lexeme_id);

-- ============================================================
-- Step 4: child_word_mastery 加 lexeme_id 列 + 回填
-- ============================================================
ALTER TABLE child_word_mastery ADD COLUMN IF NOT EXISTS lexeme_id UUID REFERENCES lexicon(id) ON DELETE CASCADE;

UPDATE child_word_mastery m SET lexeme_id = w.lexeme_id
FROM words w
WHERE m.word_id = w.id
  AND m.lexeme_id IS NULL;

-- 删除 lexeme_id 仍为 NULL 的行(对应的 word 没有 lexeme,通常是脏数据)
DELETE FROM child_word_mastery WHERE lexeme_id IS NULL;

-- ============================================================
-- Step 5: 同一 (user_id, lexeme_id) 多条 → 去重保留最高 mastery
-- (v0.13.0 时同一个词在不同课程各有 mastery 记录,全局化后需合并)
-- ============================================================
DELETE FROM child_word_mastery a
USING child_word_mastery b
WHERE a.lexeme_id = b.lexeme_id
  AND a.user_id = b.user_id
  AND a.id <> b.id
  AND (a.mastery < b.mastery OR (a.mastery = b.mastery AND a.id < b.id));

-- ============================================================
-- Step 6: 约束切换(删旧 word_id 列 + 旧索引,加新唯一索引)
-- ============================================================
DROP INDEX IF EXISTS ux_child_word_mastery_user_word;
DROP INDEX IF EXISTS ix_child_word_mastery_word;
ALTER TABLE child_word_mastery DROP COLUMN IF EXISTS word_id;

ALTER TABLE child_word_mastery ALTER COLUMN lexeme_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_child_word_mastery_user_lexeme
    ON child_word_mastery(user_id, lexeme_id);
CREATE INDEX IF NOT EXISTS ix_child_word_mastery_lexeme
    ON child_word_mastery(lexeme_id);

COMMIT;

-- 验证
-- SELECT COUNT(*) FROM lexicon;                             -- 应等于 active words 的 distinct LOWER(spelling) 数
-- SELECT COUNT(*) FROM words WHERE lexeme_id IS NOT NULL;   -- 应等于 active words 总数
-- SELECT COUNT(*) FROM child_word_mastery;                  -- 应 <= 迁移前(同 user 同 lexeme 去重)
