-- ============================================================================
-- Certificates
--
-- One certificate per completed learner journey (with its exam passed, when it has one) and per completed
-- package assignment (every journey in it that was not cancelled is completed). The backend issues them as
-- work completes and removes one if the completion is undone. This migration creates the table and issues
-- certificates for everything already completed.
--
--   certificates.type   1001 journey, 1002 package (CatalogItemType)
--   certificates.code   reference shown on the certificate, e.g. JRN-7K2F-9QXA
--
-- Re-runnable. Run BEFORE starting the backend.
-- ============================================================================

-- Read this file as UTF-8 whatever client runs it (the Docker init runner defaults to latin1).
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS certificates (
  id                    VARCHAR(36)  NOT NULL PRIMARY KEY,
  code                  VARCHAR(20)  NOT NULL,
  type                  INT          NOT NULL,
  learner_id            VARCHAR(36)  NOT NULL,
  learner_journey_id    VARCHAR(36)  NULL,
  package_assignment_id VARCHAR(36)  NULL,
  title                 VARCHAR(255) NOT NULL,
  exam_score_percent    INT          NULL,
  completed_at          DATETIME     NOT NULL,
  issued_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_certificates_code (code),
  UNIQUE KEY uq_certificates_lj (learner_journey_id),
  UNIQUE KEY uq_certificates_pa (package_assignment_id),
  INDEX idx_certificates_learner (learner_id),
  CONSTRAINT fk_cert_learner FOREIGN KEY (learner_id)            REFERENCES users (id)               ON DELETE CASCADE,
  CONSTRAINT fk_cert_lj      FOREIGN KEY (learner_journey_id)    REFERENCES learner_journeys (id)    ON DELETE CASCADE,
  CONSTRAINT fk_cert_pa      FOREIGN KEY (package_assignment_id) REFERENCES package_assignments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── Backfill: completed journeys whose exam (if any) was passed ────────────────
SET @n := (SELECT IFNULL(MAX(CAST(SUBSTRING(id, 6) AS UNSIGNED)), 0) FROM certificates WHERE id REGEXP '^cert-[0-9]+$');

INSERT INTO certificates (id, code, type, learner_id, learner_journey_id, title, exam_score_percent, completed_at, issued_at)
SELECT CONCAT('cert-', (@n := @n + 1)),
       CONCAT('JRN-', UPPER(SUBSTRING(MD5(CONCAT('journey:', done.id)), 1, 4)), '-', UPPER(SUBSTRING(MD5(CONCAT('journey:', done.id)), 5, 4))),
       1001, done.learner_id, done.id, done.title, done.score, done.completed, done.completed
FROM (
  SELECT lj.id, lj.learner_id, j.title, ea.score_percent AS score, COALESCE(lj.completed_at, NOW()) AS completed
  FROM learner_journeys lj
  JOIN journeys j ON j.id = lj.journey_id
  LEFT JOIN exams e ON e.journey_id = j.id
  LEFT JOIN exam_attempts ea ON ea.learner_journey_id = lj.id AND ea.exam_id = e.id
  WHERE lj.status = 1004
    AND (e.id IS NULL OR ea.passed = 1)
    AND NOT EXISTS (SELECT 1 FROM certificates c WHERE c.learner_journey_id = lj.id)
  ORDER BY completed
) done;

-- ── Backfill: package assignments whose live journeys are all completed ────────
INSERT INTO certificates (id, code, type, learner_id, package_assignment_id, title, completed_at, issued_at)
SELECT CONCAT('cert-', (@n := @n + 1)),
       CONCAT('PKG-', UPPER(SUBSTRING(MD5(CONCAT('package:', done.id)), 1, 4)), '-', UPPER(SUBSTRING(MD5(CONCAT('package:', done.id)), 5, 4))),
       1002, done.learner_id, done.id, done.title, done.completed, done.completed
FROM (
  SELECT pa.id, pa.learner_id, p.title,
         (SELECT COALESCE(MAX(lj.completed_at), NOW())
            FROM package_assignment_journeys paj JOIN learner_journeys lj ON lj.id = paj.learner_journey_id
           WHERE paj.package_assignment_id = pa.id AND lj.status = 1004) AS completed
  FROM package_assignments pa
  JOIN packages p ON p.id = pa.package_id
  WHERE pa.cancelled_at IS NULL
    AND EXISTS (SELECT 1 FROM package_assignment_journeys paj JOIN learner_journeys lj ON lj.id = paj.learner_journey_id
                WHERE paj.package_assignment_id = pa.id AND lj.status <> 1005)
    AND NOT EXISTS (SELECT 1 FROM package_assignment_journeys paj JOIN learner_journeys lj ON lj.id = paj.learner_journey_id
                    WHERE paj.package_assignment_id = pa.id AND lj.status NOT IN (1004, 1005))
    AND NOT EXISTS (SELECT 1 FROM certificates c WHERE c.package_assignment_id = pa.id)
  ORDER BY completed
) done;

SELECT type, COUNT(*) AS certificates FROM certificates GROUP BY type;
