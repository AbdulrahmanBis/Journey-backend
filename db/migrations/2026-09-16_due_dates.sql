-- ============================================================================
-- Due dates
--
-- A journey or package can say how long it should take ("finish within N days"). When it is
-- assigned, that becomes a due date on the assignment, which staff may change at assign time
-- or later. Both are optional: no target_days means no default due date.
--
--   journeys.target_days            expected duration, in days
--   packages.target_days            expected duration of the whole package, in days
--   learner_journeys.due_date       when this learner should finish this journey
--   package_assignments.due_date    when this learner should finish the package
--
-- Overdue = due_date before today and not completed or cancelled. The team dashboard and
-- reminders are built on it.
--
-- Re-runnable. Run BEFORE starting the backend.
-- ============================================================================

-- Read this file as UTF-8 whatever client runs it (the Docker init runner defaults to latin1).
SET NAMES utf8mb4;

SET @has := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'journeys' AND column_name = 'target_days');
SET @sql := IF(@has = 0, 'ALTER TABLE journeys ADD COLUMN target_days INT NULL AFTER tech_tag',
               'SELECT ''journeys.target_days already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'packages' AND column_name = 'target_days');
SET @sql := IF(@has = 0, 'ALTER TABLE packages ADD COLUMN target_days INT NULL AFTER description',
               'SELECT ''packages.target_days already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'learner_journeys' AND column_name = 'due_date');
SET @sql := IF(@has = 0, 'ALTER TABLE learner_journeys ADD COLUMN due_date DATE NULL AFTER assigned_at',
               'SELECT ''learner_journeys.due_date already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'package_assignments' AND column_name = 'due_date');
SET @sql := IF(@has = 0, 'ALTER TABLE package_assignments ADD COLUMN due_date DATE NULL AFTER assigned_at',
               'SELECT ''package_assignments.due_date already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT table_name, column_name, column_type FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name IN ('journeys', 'packages') AND column_name = 'target_days')
    OR (table_name IN ('learner_journeys', 'package_assignments') AND column_name = 'due_date'));
