-- v0.12.0: tasks 指派孩子 + 可完成时间段 + 周期任务；task_records 按天查重
-- 幂等：IF NOT EXISTS / DROP IF EXISTS

ALTER TABLE tasks ADD COLUMN IF NOT EXISTS assignee_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS available_start_date DATE;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS available_end_date DATE;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS available_start_time TIME;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS available_end_time TIME;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurrence_type VARCHAR(16) NOT NULL DEFAULT 'one_off';
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurrence_weekdays INTEGER[];

ALTER TABLE task_records ADD COLUMN IF NOT EXISTS completed_date DATE;
UPDATE task_records SET completed_date = created_at::date WHERE completed_date IS NULL;
ALTER TABLE task_records ALTER COLUMN completed_date SET NOT NULL;
ALTER TABLE task_records ALTER COLUMN completed_date SET DEFAULT CURRENT_DATE;

-- 历史数据若同一 (task_id, user_id, completed_date) 有多条，保留最早一条，否则新唯一索引建不上
DELETE FROM task_records a USING task_records b
WHERE a.task_id = b.task_id
  AND a.user_id = b.user_id
  AND a.completed_date = b.completed_date
  AND a.created_at > b.created_at;

DROP INDEX IF EXISTS ux_task_records_task_user;
CREATE UNIQUE INDEX IF NOT EXISTS ux_task_records_task_user_date
  ON task_records (task_id, user_id, completed_date);
