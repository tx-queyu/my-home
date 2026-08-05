-- v0.9.2: Device 表加 OS 信息字段（os_type, os_version, manufacturer, model）
-- 兼容历史数据：os_type 默认 'android'（之前所有设备都是 Android APK 上报的）

ALTER TABLE devices ADD COLUMN IF NOT EXISTS os_type VARCHAR(16) NOT NULL DEFAULT 'android';
ALTER TABLE devices ADD COLUMN IF NOT EXISTS os_version VARCHAR(64);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS manufacturer VARCHAR(64);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS model VARCHAR(128);
