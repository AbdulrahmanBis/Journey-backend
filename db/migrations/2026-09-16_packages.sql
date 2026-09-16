-- ============================================================================
-- Packages
--
-- A package is a named, ordered bundle of existing journeys. Journeys stay exactly as
-- they are: assigning a package creates (or reuses) ordinary learner_journeys rows and
-- only records which of them belong to that package assignment.
--
--   packages                    the bundle itself
--   package_journeys            which journeys it holds, in order (edits affect future
--                               assignments only)
--   package_assignments         a package given to a learner
--   package_assignment_journeys the learner_journeys that assignment covers, frozen in
--                               order at assign time. created_by_package is false when
--                               the learner already had that journey and it was reused —
--                               cancelling the package leaves those alone.
--
-- Nothing is added to learner_journeys, so a journey assigned on its own is untouched,
-- and one learner journey can belong to more than one package.
--
-- Deletes follow the existing tables: removing a journey or a learner cascades.
-- A package cannot be deleted while any assignment of it exists (RESTRICT).
--
-- Re-runnable. Run BEFORE starting the backend.
-- ============================================================================

-- Read this file as UTF-8 whatever client runs it (the Docker init runner defaults to latin1).
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS packages (
  id               VARCHAR(36)  NOT NULL,
  title            VARCHAR(255) NOT NULL,
  description      TEXT         NULL,
  created_by_id    VARCHAR(36)  NULL,
  created_by_name  VARCHAR(255) NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS package_journeys (
  package_id  VARCHAR(36) NOT NULL,
  journey_id  VARCHAR(36) NOT NULL,
  position    INT         NOT NULL,
  PRIMARY KEY (package_id, journey_id),
  KEY idx_package_journeys_journey (journey_id),
  CONSTRAINT fk_pj_package FOREIGN KEY (package_id) REFERENCES packages (id) ON DELETE CASCADE,
  CONSTRAINT fk_pj_journey FOREIGN KEY (journey_id) REFERENCES journeys (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS package_assignments (
  id                VARCHAR(36)  NOT NULL,
  package_id        VARCHAR(36)  NOT NULL,
  learner_id        VARCHAR(36)  NOT NULL,
  assigned_by_id    VARCHAR(36)  NULL,
  assigned_by_name  VARCHAR(255) NULL,
  assigned_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  cancelled_at      DATETIME     NULL,
  PRIMARY KEY (id),
  KEY idx_package_assignments_learner (learner_id),
  CONSTRAINT fk_pa_package FOREIGN KEY (package_id) REFERENCES packages (id) ON DELETE RESTRICT,
  CONSTRAINT fk_pa_learner FOREIGN KEY (learner_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS package_assignment_journeys (
  package_assignment_id  VARCHAR(36) NOT NULL,
  learner_journey_id     VARCHAR(36) NOT NULL,
  position               INT         NOT NULL,
  created_by_package     BOOLEAN     NOT NULL,
  PRIMARY KEY (package_assignment_id, learner_journey_id),
  KEY idx_paj_learner_journey (learner_journey_id),
  CONSTRAINT fk_paj_assignment FOREIGN KEY (package_assignment_id) REFERENCES package_assignments (id) ON DELETE CASCADE,
  CONSTRAINT fk_paj_learner_journey FOREIGN KEY (learner_journey_id) REFERENCES learner_journeys (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SELECT 'packages' AS tbl, COUNT(*) AS n FROM packages
UNION ALL SELECT 'package_journeys', COUNT(*) FROM package_journeys
UNION ALL SELECT 'package_assignments', COUNT(*) FROM package_assignments
UNION ALL SELECT 'package_assignment_journeys', COUNT(*) FROM package_assignment_journeys;
