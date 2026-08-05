-- v0.10.4: 课程目录 2 层结构重构（subject → textbook → learning_method）
--
-- 改动：drop name 列，add textbook + learning_method 列，唯一索引改 (subject, textbook, learning_method)
-- 英语学科按新结构重写为 189 条（27 教材 × 7 方式），其他 12 学科保留旧 4 条作为占位（textbook='默认'）
--
-- 用户选项 (b)：CASCADE 删旧 tasks.course_id（set NULL），旧 task.title 保留可读，丢失历史关联
--
-- 执行顺序：
--   1. UPDATE tasks SET course_id=NULL — 干净切断引用（ON DELETE SET NULL 不会自动触发，因为是 DROP TABLE）
--   2. DROP TABLE courses CASCADE — 删表 + 删 FK 约束
--   3. 后端容器重启 — Base.metadata.create_all 重建新 schema 表 + seed_courses_if_empty 插入 226 条
--
-- 幂等：可重复执行（IF EXISTS），但 INSERT 由后端 lifespan 完成，不在本 SQL 内
-- 回滚：从 /opt/myhome-backup-*.sql 恢复（部署前 pg_dump）

BEGIN;

-- 1. 切断 tasks.course_id 引用（用户选 option b，干净处理）
UPDATE tasks SET course_id = NULL WHERE course_id IS NOT NULL;

-- 2. DROP courses 表（CASCADE 同时删除 tasks_course_id_fkey 约束）
DROP TABLE IF EXISTS courses CASCADE;

COMMIT;

-- 3. 重建 backend 容器：
--    docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml up -d --build backend
--    容器 lifespan 会跑 Base.metadata.create_all（重建 courses 表，新 schema）
--    + seed_courses_if_empty（INSERT 226 条：189 英语 + 37 占位）
--
-- 验证：
--    docker exec myhome-postgres psql -U myhome -c "SELECT subject, count(*) FROM courses GROUP BY subject ORDER BY subject;"
--    期望 13 行，英语 189 条，其他学科 2-5 条占位
--    curl -H "Authorization: Bearer $TOKEN" "http://localhost:8000/api/courses?subject=英语" | jq length  # 189
