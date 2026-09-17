-- ============================================================================
-- Getting-started checklist
--
-- Managers, seniors and HR see a short checklist on their dashboard; its steps tick themselves from data.
-- This column records that the person hid it.
--
--   users.getting_started_dismissed_at
--
-- Re-runnable. Run BEFORE starting the backend.
-- ============================================================================

-- Read this file as UTF-8 whatever client runs it (the Docker init runner defaults to latin1).
SET NAMES utf8mb4;

SET @has := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'getting_started_dismissed_at');
SET @sql := IF(@has = 0, 'ALTER TABLE users ADD COLUMN getting_started_dismissed_at DATETIME NULL',
               'SELECT ''users.getting_started_dismissed_at already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
