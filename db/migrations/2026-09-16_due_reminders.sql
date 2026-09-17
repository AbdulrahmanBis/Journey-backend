-- ============================================================================
-- Due-date reminders
--
-- An hourly job reminds the learner when a journey is due within two days, and the learner and
-- their assigner once it is overdue. These columns record that each reminder went out, so it is
-- sent once per due date; changing the due date clears them.
--
--   learner_journeys.due_soon_notified_at
--   learner_journeys.overdue_notified_at
--
-- Re-runnable. Run BEFORE starting the backend.
-- ============================================================================

-- Read this file as UTF-8 whatever client runs it (the Docker init runner defaults to latin1).
SET NAMES utf8mb4;

SET @has := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'learner_journeys' AND column_name = 'due_soon_notified_at');
SET @sql := IF(@has = 0, 'ALTER TABLE learner_journeys ADD COLUMN due_soon_notified_at DATETIME NULL AFTER due_date',
               'SELECT ''learner_journeys.due_soon_notified_at already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'learner_journeys' AND column_name = 'overdue_notified_at');
SET @sql := IF(@has = 0, 'ALTER TABLE learner_journeys ADD COLUMN overdue_notified_at DATETIME NULL AFTER due_soon_notified_at',
               'SELECT ''learner_journeys.overdue_notified_at already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) AS with_due_date FROM learner_journeys WHERE due_date IS NOT NULL;
