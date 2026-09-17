-- ============================================================================
-- Intro guide
--
-- users.intro_seen_version: the version of the in-app intro guide this person chose not to see
-- again. NULL means never dismissed. The frontend shows the guide when its current version is
-- higher, so a reworked guide is shown once more to everyone.
--
-- No AFTER clause: on a fresh database preferred_language is added later, by Hibernate.
--
-- Re-runnable. Run BEFORE starting the backend.
-- ============================================================================

-- Read this file as UTF-8 whatever client runs it (the Docker init runner defaults to latin1).
SET NAMES utf8mb4;

SET @has := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'intro_seen_version');
SET @sql := IF(@has = 0, 'ALTER TABLE users ADD COLUMN intro_seen_version INT NULL',
               'SELECT ''users.intro_seen_version already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) AS users, SUM(intro_seen_version IS NOT NULL) AS dismissed FROM users;
