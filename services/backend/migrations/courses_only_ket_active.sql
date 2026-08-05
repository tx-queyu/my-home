-- v0.12.5: 课程一门门开发,除「英语 · KET · 朗读」外全部 is_active=false
-- 幂等:可重复执行
-- 验证:SELECT subject, textbook, learning_method, is_active FROM courses WHERE is_active;

UPDATE courses
SET is_active = FALSE,
    updated_at = NOW()
WHERE NOT (
    subject = '英语'
    AND textbook = 'KET'
    AND learning_method = '朗读'
);

-- 健康检查:应该只有 1 条 active
SELECT COUNT(*) AS active_count FROM courses WHERE is_active;
SELECT subject, textbook, learning_method FROM courses WHERE is_active;
