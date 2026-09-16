-- ============================================================================
-- Self-enrollment (catalog)
--
-- A learner can enroll themselves in a journey or a package from the catalog. The row
-- still records a reviewer in assigned_by_* (their senior, or a manager of their
-- department when they have no senior), because every notification and review route
-- reads the assigner: exam submitted, journey completed, a learner's note. Pointing it at
-- the learner themselves would route those back to the learner and nobody would review.
--
-- self_enrolled tells the UI to say "Self-enrolled" instead of "Assigned by".
--
-- Re-runnable. Run BEFORE starting the backend.
-- ============================================================================

-- Read this file as UTF-8 whatever client runs it (the Docker init runner defaults to latin1).
SET NAMES utf8mb4;

SET @has := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'learner_journeys' AND column_name = 'self_enrolled');
SET @sql := IF(@has = 0,
               'ALTER TABLE learner_journeys ADD COLUMN self_enrolled BOOLEAN NOT NULL DEFAULT FALSE AFTER assigned_at',
               'SELECT ''learner_journeys.self_enrolled already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'package_assignments' AND column_name = 'self_enrolled');
SET @sql := IF(@has = 0,
               'ALTER TABLE package_assignments ADD COLUMN self_enrolled BOOLEAN NOT NULL DEFAULT FALSE AFTER assigned_at',
               'SELECT ''package_assignments.self_enrolled already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT 'learner_journeys' AS tbl, SUM(self_enrolled) AS self_enrolled, COUNT(*) AS total FROM learner_journeys
UNION ALL SELECT 'package_assignments', SUM(self_enrolled), COUNT(*) FROM package_assignments;
