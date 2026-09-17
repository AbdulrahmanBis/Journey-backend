-- ============================================================================
-- Announcements
--
-- A message from a Manager (their own department), HR or an Admin (everyone, or chosen departments).
-- It sits at the top of each recipient's dashboard until its show-until date or until they dismiss it,
-- and publishing it notifies the audience in-app and by email.
--
--   announcements              the message, its author and how long it stays on dashboards
--   announcement_departments   the departments it reaches (none when org_wide = 1)
--   announcement_dismissals    who has closed it on their dashboard
--
-- Re-runnable. Run BEFORE starting the backend.
-- ============================================================================

-- Read this file as UTF-8 whatever client runs it (the Docker init runner defaults to latin1).
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS announcements (
  id          VARCHAR(36)  NOT NULL PRIMARY KEY,
  title       VARCHAR(200) NOT NULL,
  body        MEDIUMTEXT   NOT NULL,
  org_wide    TINYINT(1)   NOT NULL DEFAULT 0,
  show_until  DATE         NOT NULL,
  author_id   VARCHAR(36)  NULL,
  author_name VARCHAR(255) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_announcements_show_until (show_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS announcement_departments (
  announcement_id VARCHAR(36) NOT NULL,
  department_id   VARCHAR(36) NOT NULL,
  PRIMARY KEY (announcement_id, department_id),
  CONSTRAINT fk_ad_announcement FOREIGN KEY (announcement_id) REFERENCES announcements (id) ON DELETE CASCADE,
  CONSTRAINT fk_ad_department   FOREIGN KEY (department_id)   REFERENCES departments (id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS announcement_dismissals (
  announcement_id VARCHAR(36) NOT NULL,
  user_id         VARCHAR(36) NOT NULL,
  dismissed_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (announcement_id, user_id),
  CONSTRAINT fk_adis_announcement FOREIGN KEY (announcement_id) REFERENCES announcements (id) ON DELETE CASCADE,
  CONSTRAINT fk_adis_user         FOREIGN KEY (user_id)         REFERENCES users (id)         ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SELECT COUNT(*) AS announcements FROM announcements;
