-- v0.17.0 习惯打卡(dev 新表由 create_all 建,但 enum 值必须本迁移;prod 全靠本迁移,幂等)
-- ⚠️ ALTER TYPE 事务坑:ALTER TYPE ... ADD VALUE 不能在事务块内使用新值。
--    本文件不包 BEGIN/COMMIT;psql 执行时不要加 --single-transaction/-1(默认逐句执行,安全)。
-- ⚠️ 部署顺序:先跑本迁移,再部署新后端代码(否则 INSERT source='checkin' 报 invalid input value)。
ALTER TYPE point_source ADD VALUE IF NOT EXISTS 'checkin';

CREATE TABLE IF NOT EXISTS habits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    name VARCHAR(64) NOT NULL,
    points SMALLINT NOT NULL DEFAULT 1,
    streak_cap SMALLINT NOT NULL DEFAULT 7,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS habit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    habit_id UUID NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    streak_count SMALLINT NOT NULL,
    points_earned SMALLINT NOT NULL,
    checkin_date DATE NOT NULL DEFAULT CURRENT_DATE,
    note VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 清历史重复(保留最早一条),否则唯一索引建不上
DELETE FROM habit_logs a USING habit_logs b
WHERE a.habit_id = b.habit_id
  AND a.user_id = b.user_id
  AND a.checkin_date = b.checkin_date
  AND a.created_at > b.created_at;
CREATE UNIQUE INDEX IF NOT EXISTS ux_habit_logs_habit_user_date
    ON habit_logs (habit_id, user_id, checkin_date);
CREATE UNIQUE INDEX IF NOT EXISTS ux_habits_family_name
    ON habits (family_id, name);
CREATE INDEX IF NOT EXISTS ix_habits_family_id ON habits (family_id);
CREATE INDEX IF NOT EXISTS ix_habit_logs_user_id ON habit_logs (user_id);
CREATE INDEX IF NOT EXISTS ix_habit_logs_checkin_date ON habit_logs (checkin_date);

-- 验证:应输出 4 行,含 checkin
SELECT unnest(enum_range(NULL::point_source)) AS source;
