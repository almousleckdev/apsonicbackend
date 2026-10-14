-- V12__add_failed_login_attempts_to_users.sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS lock_time TIMESTAMP WITH TIME ZONE;
