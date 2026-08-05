-- v0.7.0 验证码功能 prod 迁移 SQL
-- 手动在 psql 执行（连接 myhome 库）：
--   docker exec -it myhome-postgres psql -U myhome -d myhome -f /path/to/verification_phase.sql
-- 或直接复制粘贴到 psql 终端。
-- 幂等：IF NOT EXISTS 守护，多次执行安全。

-- 1. users 加 phone/email + verified 字段
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(32);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- partial unique index（已验证的 phone/email 全局唯一）
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_phone
  ON users(phone) WHERE phone IS NOT NULL AND phone_verified = TRUE;
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email
  ON users(email) WHERE email IS NOT NULL AND email_verified = TRUE;

-- 2. sms_configs
CREATE TABLE IF NOT EXISTS sms_configs (
    id UUID PRIMARY KEY,
    provider VARCHAR(16) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    sign_name VARCHAR(64),
    template_code VARCHAR(64),
    access_key_id_encrypted TEXT,
    access_key_secret_encrypted TEXT,
    sdk_app_id VARCHAR(128),
    region VARCHAR(64),
    daily_limit INTEGER NOT NULL DEFAULT 1000,
    interval_seconds INTEGER NOT NULL DEFAULT 60,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- 全局仅一行 is_active=true
CREATE UNIQUE INDEX IF NOT EXISTS ix_sms_configs_active
  ON sms_configs(is_active) WHERE is_active = TRUE;
CREATE UNIQUE INDEX IF NOT EXISTS ux_sms_configs_provider ON sms_configs(provider);

-- 3. email_configs
CREATE TABLE IF NOT EXISTS email_configs (
    id UUID PRIMARY KEY,
    provider VARCHAR(16) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    smtp_host VARCHAR(255),
    smtp_port INTEGER DEFAULT 465,
    encryption VARCHAR(16) DEFAULT 'ssl',
    username VARCHAR(255),
    password_encrypted TEXT,
    access_key_id_encrypted TEXT,
    access_key_secret_encrypted TEXT,
    region VARCHAR(64),
    from_email VARCHAR(255),
    from_name VARCHAR(128),
    daily_limit INTEGER NOT NULL DEFAULT 200,
    interval_seconds INTEGER NOT NULL DEFAULT 60,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS ix_email_configs_active
  ON email_configs(is_active) WHERE is_active = TRUE;
CREATE UNIQUE INDEX IF NOT EXISTS ux_email_configs_provider ON email_configs(provider);

-- 4. verification_codes
CREATE TABLE IF NOT EXISTS verification_codes (
    id UUID PRIMARY KEY,
    channel VARCHAR(8) NOT NULL,
    target VARCHAR(256) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    code_hash VARCHAR(256) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    ip VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_verification_codes_lookup
  ON verification_codes(target, purpose, created_at);

-- 完成提示
SELECT 'verification_phase migration applied' AS status;
