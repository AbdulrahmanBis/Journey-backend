-- ============================================================================
-- Journey units
--
-- A journey is now made of units; each unit holds items and may end with a short quiz.
-- Items move themselves along as the learner reads (opened → in progress, Next →
-- completed). The review workflow — waiting for review, completed, sent back — moves up to
-- the unit.
--
--   journey_units           a journey's units, in order
--   journey_items.unit_id   the unit an item belongs to
--   unit_quiz_questions     1–2 auto-graded questions (multiple choice / yes-no) per unit
--   learner_journey_units   a learner's status and quiz result per unit
--   journey_notes.learner_journey_unit_id   notes can belong to a unit (review feedback)
--                                            instead of an item
--
-- Existing data: every journey gets one unit, "Unit 1", holding all its items. Each learner's
-- unit status is derived from their item statuses, and items that were "waiting for review"
-- become completed — that state now lives on the unit.
--
-- Re-runnable. Run BEFORE starting the backend.
-- ============================================================================

-- Read this file as UTF-8 whatever client runs it (the Docker init runner defaults to latin1).
SET NAMES utf8mb4;

-- 1. Units --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS journey_units (
  id           VARCHAR(36)  NOT NULL,
  journey_id   VARCHAR(36)  NOT NULL,
  title        VARCHAR(255) NOT NULL,
  description  TEXT         NULL,
  unit_order   INT          NOT NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_journey_units_journey (journey_id),
  CONSTRAINT fk_ju_journey FOREIGN KEY (journey_id) REFERENCES journeys (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Journeys without units get "Unit 1".
INSERT INTO journey_units (id, journey_id, title, unit_order)
SELECT UUID(), j.id, 'Unit 1', 1
FROM journeys j
WHERE NOT EXISTS (SELECT 1 FROM journey_units u WHERE u.journey_id = j.id);

-- 2. Items belong to a unit -----------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'journey_items' AND column_name = 'unit_id');
SET @sql := IF(@has = 0, 'ALTER TABLE journey_items ADD COLUMN unit_id VARCHAR(36) NULL AFTER journey_id',
               'SELECT ''journey_items.unit_id already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- An item without a unit goes into its journey's first unit.
UPDATE journey_items ji
JOIN (SELECT u.journey_id, u.id
      FROM journey_units u
      WHERE u.unit_order = (SELECT MIN(u2.unit_order) FROM journey_units u2 WHERE u2.journey_id = u.journey_id)) first_unit
  ON first_unit.journey_id = ji.journey_id
SET ji.unit_id = first_unit.id
WHERE ji.unit_id IS NULL;

ALTER TABLE journey_items MODIFY COLUMN unit_id VARCHAR(36) NOT NULL;

SET @has := (SELECT COUNT(*) FROM information_schema.table_constraints
             WHERE table_schema = DATABASE() AND table_name = 'journey_items' AND constraint_name = 'fk_ji_unit');
SET @sql := IF(@has = 0,
               'ALTER TABLE journey_items ADD CONSTRAINT fk_ji_unit FOREIGN KEY (unit_id) REFERENCES journey_units (id) ON DELETE CASCADE',
               'SELECT ''fk_ji_unit already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. Unit quizzes -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS unit_quiz_questions (
  id                    VARCHAR(36)  NOT NULL,
  unit_id               VARCHAR(36)  NOT NULL,
  question_order        INT          NOT NULL,
  question_type         INT          NOT NULL,
  prompt                TEXT         NOT NULL,
  option_1              VARCHAR(255) NULL,
  option_2              VARCHAR(255) NULL,
  option_3              VARCHAR(255) NULL,
  option_4              VARCHAR(255) NULL,
  correct_option_index  INT          NULL,
  correct_bool_answer   BOOLEAN      NULL,
  PRIMARY KEY (id),
  KEY idx_uqq_unit (unit_id),
  CONSTRAINT fk_uqq_unit FOREIGN KEY (unit_id) REFERENCES journey_units (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4. A learner's progress per unit ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS learner_journey_units (
  id                  VARCHAR(36) NOT NULL,
  learner_journey_id  VARCHAR(36) NOT NULL,
  unit_id             VARCHAR(36) NOT NULL,
  status              INT         NOT NULL,
  quiz_answers        TEXT        NULL,
  quiz_score_percent  INT         NULL,
  quiz_submitted_at   DATETIME    NULL,
  updated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_lju_journey_unit (learner_journey_id, unit_id),
  KEY idx_lju_unit (unit_id),
  CONSTRAINT fk_lju_lj FOREIGN KEY (learner_journey_id) REFERENCES learner_journeys (id) ON DELETE CASCADE,
  CONSTRAINT fk_lju_unit FOREIGN KEY (unit_id) REFERENCES journey_units (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Status per learner journey × unit, from the items (codes: 1001 new, 1002 in progress,
-- 1003 waiting for review, 1004 completed):
--   every item completed                              → completed
--   every item completed or waiting, at least one waiting → waiting for review
--   anything started                                  → in progress
--   otherwise                                         → new
INSERT INTO learner_journey_units (id, learner_journey_id, unit_id, status, updated_at)
SELECT UUID(), lj.id, u.id,
       CASE
         WHEN SUM(lji.status = 1004) = COUNT(lji.id) AND COUNT(lji.id) > 0 THEN 1004
         WHEN SUM(lji.status IN (1003, 1004)) = COUNT(lji.id) AND SUM(lji.status = 1003) > 0 THEN 1003
         WHEN SUM(lji.status <> 1001) > 0 THEN 1002
         ELSE 1001
       END,
       COALESCE(MAX(lji.updated_at), lj.assigned_at)
FROM learner_journeys lj
JOIN journey_units u ON u.journey_id = lj.journey_id
LEFT JOIN journey_items ji ON ji.unit_id = u.id
LEFT JOIN learner_journey_items lji ON lji.learner_journey_id = lj.id AND lji.journey_item_id = ji.id
WHERE NOT EXISTS (SELECT 1 FROM learner_journey_units x WHERE x.learner_journey_id = lj.id AND x.unit_id = u.id)
GROUP BY lj.id, u.id, lj.assigned_at;

-- "Waiting for review" moves to the unit; the items themselves are done.
UPDATE learner_journey_items SET status = 1004 WHERE status = 1003;

-- 5. Notes can belong to a unit ---------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'journey_notes' AND column_name = 'learner_journey_unit_id');
SET @sql := IF(@has = 0,
               'ALTER TABLE journey_notes ADD COLUMN learner_journey_unit_id VARCHAR(36) NULL AFTER learner_journey_item_id',
               'SELECT ''journey_notes.learner_journey_unit_id already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE journey_notes MODIFY COLUMN learner_journey_item_id VARCHAR(36) NULL;

SET @has := (SELECT COUNT(*) FROM information_schema.table_constraints
             WHERE table_schema = DATABASE() AND table_name = 'journey_notes' AND constraint_name = 'fk_note_unit');
SET @sql := IF(@has = 0,
               'ALTER TABLE journey_notes ADD CONSTRAINT fk_note_unit FOREIGN KEY (learner_journey_unit_id) REFERENCES learner_journey_units (id) ON DELETE CASCADE',
               'SELECT ''fk_note_unit already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6. Check ---------------------------------------------------------------------------------------
SELECT (SELECT COUNT(*) FROM journey_units) AS units,
       (SELECT COUNT(*) FROM journey_items WHERE unit_id IS NULL) AS items_without_unit,
       (SELECT COUNT(*) FROM learner_journey_units) AS learner_units,
       (SELECT COUNT(*) FROM learner_journey_items WHERE status = 1003) AS items_still_waiting;
