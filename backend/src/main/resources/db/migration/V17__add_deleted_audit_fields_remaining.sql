-- Add soft delete fields to remaining base tables
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);

ALTER TABLE roles ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE roles ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);

ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);

ALTER TABLE system_configs ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE system_configs ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);
