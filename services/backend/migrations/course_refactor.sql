-- v0.10.0 教育模块重构：Subject → Course（系统预置课程）
-- 在 prod 执行前务必备份 DB:
--   docker exec myhome-postgres-prod pg_dump -U myhome myhome > /tmp/myhome-backup-$(date +%Y%m%d).sql
-- 或者通过 ECS: docker compose -f /opt/myhome/deploy/docker-compose.prod.yml exec -T postgres pg_dump ...

-- 1. CREATE courses 表
CREATE TABLE IF NOT EXISTS courses (
    id UUID NOT NULL PRIMARY KEY,
    subject VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    default_points SMALLINT NOT NULL DEFAULT 10,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_courses_subject_name ON courses(subject, name);
CREATE INDEX IF NOT EXISTS ix_courses_subject ON courses(subject);

-- 2. INSERT 41 条种子（与 services/backend/app/core/seed_courses.py 的 SEED_COURSES 保持一致）
--    使用 ON CONFLICT DO NOTHING 保证重跑幂等
INSERT INTO courses (id, subject, name, description, default_points, is_active, sort_order) VALUES
(gen_random_uuid(), '数学', '完成20道口算题', NULL, 10, TRUE, 1),
(gen_random_uuid(), '数学', '做一张数学试卷', NULL, 20, TRUE, 2),
(gen_random_uuid(), '数学', '看15分钟数学教学视频', NULL, 5, TRUE, 3),
(gen_random_uuid(), '数学', '做5道应用题', NULL, 8, TRUE, 4),
(gen_random_uuid(), '语文', '背诵古诗一首', NULL, 8, TRUE, 1),
(gen_random_uuid(), '语文', '阅读30分钟课外书', NULL, 10, TRUE, 2),
(gen_random_uuid(), '语文', '写一篇日记', NULL, 15, TRUE, 3),
(gen_random_uuid(), '语文', '抄写生字词20个', NULL, 5, TRUE, 4),
(gen_random_uuid(), '语文', '完成一篇阅读理解', NULL, 10, TRUE, 5),
(gen_random_uuid(), '英语', '背20个英语单词', NULL, 10, TRUE, 1),
(gen_random_uuid(), '英语', '做一篇英语阅读', NULL, 10, TRUE, 2),
(gen_random_uuid(), '英语', '听英语听力15分钟', NULL, 5, TRUE, 3),
(gen_random_uuid(), '英语', '看15分钟英语动画', NULL, 5, TRUE, 4),
(gen_random_uuid(), '物理', '做10道物理题', NULL, 10, TRUE, 1),
(gen_random_uuid(), '物理', '做一个物理实验', NULL, 15, TRUE, 2),
(gen_random_uuid(), '物理', '看物理教学视频15分钟', NULL, 5, TRUE, 3),
(gen_random_uuid(), '化学', '做10道化学题', NULL, 10, TRUE, 1),
(gen_random_uuid(), '化学', '做一个化学实验', NULL, 15, TRUE, 2),
(gen_random_uuid(), '生物', '做10道生物题', NULL, 10, TRUE, 1),
(gen_random_uuid(), '生物', '看生物纪录片15分钟', NULL, 5, TRUE, 2),
(gen_random_uuid(), '历史', '做历史练习题', NULL, 8, TRUE, 1),
(gen_random_uuid(), '历史', '读历史读物30分钟', NULL, 10, TRUE, 2),
(gen_random_uuid(), '历史', '看历史纪录片15分钟', NULL, 5, TRUE, 3),
(gen_random_uuid(), '地理', '做地理练习题', NULL, 8, TRUE, 1),
(gen_random_uuid(), '地理', '看地理纪录片15分钟', NULL, 5, TRUE, 2),
(gen_random_uuid(), '地理', '读地理读物30分钟', NULL, 10, TRUE, 3),
(gen_random_uuid(), '体育', '跑步30分钟', NULL, 15, TRUE, 1),
(gen_random_uuid(), '体育', '跳绳200个', NULL, 10, TRUE, 2),
(gen_random_uuid(), '体育', '做50个仰卧起坐', NULL, 10, TRUE, 3),
(gen_random_uuid(), '体育', '做5分钟拉伸', NULL, 5, TRUE, 4),
(gen_random_uuid(), '音乐', '练习乐器30分钟', NULL, 15, TRUE, 1),
(gen_random_uuid(), '音乐', '唱一首歌', NULL, 10, TRUE, 2),
(gen_random_uuid(), '音乐', '听古典音乐15分钟', NULL, 5, TRUE, 3),
(gen_random_uuid(), '美术', '绘画30分钟', NULL, 10, TRUE, 1),
(gen_random_uuid(), '美术', '练习书法30分钟', NULL, 10, TRUE, 2),
(gen_random_uuid(), '美术', '完成一幅水彩画', NULL, 15, TRUE, 3),
(gen_random_uuid(), '课外', '阅读课外书30分钟', NULL, 10, TRUE, 1),
(gen_random_uuid(), '课外', '写一篇读书笔记', NULL, 15, TRUE, 2),
(gen_random_uuid(), '实践', '做家务', NULL, 10, TRUE, 1),
(gen_random_uuid(), '实践', '烹饪一道菜', NULL, 15, TRUE, 2),
(gen_random_uuid(), '实践', '整理房间', NULL, 10, TRUE, 3)
ON CONFLICT (subject, name) DO NOTHING;

-- 3. tasks 加 course_id（FK courses.id, ON DELETE SET NULL）
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS course_id UUID REFERENCES courses(id) ON DELETE SET NULL;

-- 4. tasks 删 subject_id（先 drop FK 约束，再 drop column）
--    注意：DROP COLUMN IF EXISTS 在 pg 14+ 会自动 cascade 约束
ALTER TABLE tasks DROP COLUMN IF EXISTS subject_id;

-- 5. DROP subjects 表
DROP TABLE IF EXISTS subjects;

-- 验证
SELECT 'courses' AS tbl, COUNT(*) FROM courses
UNION ALL
SELECT 'tasks_with_course', COUNT(*) FROM tasks WHERE course_id IS NOT NULL
UNION ALL
SELECT 'tasks_total', COUNT(*) FROM tasks;
