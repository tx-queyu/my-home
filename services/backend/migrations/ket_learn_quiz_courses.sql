-- v0.15.0: KET 学习课 + 测评课
-- 幂等：可重复执行。
-- 1. 新建「英语·KET·学习」「英语·KET·测评」两门课
-- 2. 从「英语·KET·朗读」复制 150 词到新课（共享 lexeme_id，能力跨课程互通）

-- Step 1: 两门课程
INSERT INTO courses (id, subject, textbook, learning_method, default_points, is_active, sort_order, created_at, updated_at)
SELECT gen_random_uuid(), '英语', 'KET', v.m, v.p, TRUE, 170, now(), now()
FROM (VALUES ('学习', 5), ('测评', 15)) AS v(m, p)
WHERE NOT EXISTS (
    SELECT 1 FROM courses
    WHERE subject = '英语' AND textbook = 'KET' AND learning_method = v.m
);

-- Step 2: 复制词库（同 lexeme_id）
INSERT INTO words (id, course_id, lexeme_id, spelling, syllables, meaning_cn, phonetic,
                   sample_sentence, sample_sentence_translation, sort_order, is_active, created_at, updated_at)
SELECT gen_random_uuid(), c2.id, w.lexeme_id, w.spelling, w.syllables, w.meaning_cn, w.phonetic,
       w.sample_sentence, w.sample_sentence_translation, w.sort_order, TRUE, now(), now()
FROM words w
JOIN courses c1 ON c1.id = w.course_id
  AND c1.subject = '英语' AND c1.textbook = 'KET' AND c1.learning_method = '朗读'
JOIN courses c2 ON c2.subject = '英语' AND c2.textbook = 'KET'
  AND c2.learning_method IN ('学习', '测评')
WHERE w.is_active = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM words x WHERE x.course_id = c2.id AND x.spelling = w.spelling
  );
