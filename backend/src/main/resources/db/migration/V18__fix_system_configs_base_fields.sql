-- Add missing BaseEntity fields to system_configs
ALTER TABLE system_configs ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE system_configs ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
