-- ============================================================================
-- Departments
--
-- Every person belongs to exactly one department, managed in the app. Departments
-- scope PEOPLE only: a manager sees the users of their own department, HR and Admin
-- see all. Journeys are company-wide and deliberately have no department.
--
-- Existing users all move into one "Information Technology" department — the seeded
-- content is IT onboarding. An admin can create more departments and move people.
--
-- The HR role (code 1005) needs no schema change: roles are an integer code on
-- users.role, and the enum lives in the backend.
--
-- Re-runnable: every step checks whether it has already been applied.
-- Run BEFORE starting the backend, so Hibernate's ddl-auto finds the column already
-- there instead of adding a nullable one of its own.
-- ============================================================================

-- Arabic text below: read this file as UTF-8 whatever client runs it.
SET NAMES utf8mb4;

-- 1. Table ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS departments (
  id          VARCHAR(36)  NOT NULL,
  name_en     VARCHAR(120) NOT NULL,
  name_ar     VARCHAR(120) NOT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_departments_name_en (name_en),
  UNIQUE KEY uk_departments_name_ar (name_ar)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. The department everyone starts in ---------------------------------------------
INSERT IGNORE INTO departments (id, name_en, name_ar)
VALUES ('dep-1', 'Information Technology', 'تقنية المعلومات');

-- 3. users.department_id, added nullable so existing rows can be filled -------------
SET @has_col := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'department_id');
SET @sql := IF(@has_col = 0,
               'ALTER TABLE users ADD COLUMN department_id VARCHAR(36) NULL AFTER role',
               'SELECT ''department_id already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. Fill it -----------------------------------------------------------------------
UPDATE users SET department_id = 'dep-1' WHERE department_id IS NULL;

-- 5. Now make it required ---------------------------------------------------------------
ALTER TABLE users MODIFY COLUMN department_id VARCHAR(36) NOT NULL;

-- 6. Foreign key. RESTRICT: a department with people in it cannot be deleted by accident ----
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'users'
                  AND constraint_name = 'fk_users_department');
SET @sql := IF(@has_fk = 0,
               'ALTER TABLE users ADD CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT',
               'SELECT ''fk_users_department already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7. Check --------------------------------------------------------------------------
SELECT d.id, d.name_en, COUNT(u.id) AS people
FROM departments d LEFT JOIN users u ON u.department_id = d.id
GROUP BY d.id, d.name_en;
