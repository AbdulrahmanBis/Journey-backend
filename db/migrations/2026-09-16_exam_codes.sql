-- ============================================================================
-- Exam codes → 1001-based
--
-- journey.sql predates the switch of exam values to the 1001-based code convention used
-- everywhere else. Databases restored from it (including a fresh Docker volume) still hold:
--   exam_questions.question_type      1..3  (multiple choice, yes/no, open)
--   exam_attempts.status              1..3  (draft, submitted, graded)
--   exam_questions.correct_option_index  0-based option index
--   exam_answers.selected_option_index   0-based option index
-- and the API fails on the first exam it reads ("Unknown exam question type code: 1").
--
-- Only values below 1000 are converted, so this is a no-op on an already converted
-- database and safe to run more than once. Run it right after restoring journey.sql,
-- before the other migrations and before starting the backend.
-- ============================================================================

-- Read this file as UTF-8 whatever client runs it (the Docker init runner defaults to latin1).
SET NAMES utf8mb4;

UPDATE exam_questions SET question_type = question_type + 1000 WHERE question_type < 1000;
UPDATE exam_questions SET correct_option_index = correct_option_index + 1001
 WHERE correct_option_index IS NOT NULL AND correct_option_index < 1000;

UPDATE exam_attempts SET status = status + 1000 WHERE status < 1000;

UPDATE exam_answers SET selected_option_index = selected_option_index + 1001
 WHERE selected_option_index IS NOT NULL AND selected_option_index < 1000;

SELECT 'exam_questions' AS tbl, MIN(question_type) AS min_code, MAX(question_type) AS max_code FROM exam_questions
UNION ALL SELECT 'exam_attempts', MIN(status), MAX(status) FROM exam_attempts;
