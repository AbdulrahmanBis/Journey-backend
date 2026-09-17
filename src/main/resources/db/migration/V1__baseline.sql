-- Journey schema baseline (2026-09-17).
--
-- Replaces the hand-run files that used to live in Backend/db/migrations plus the tables Hibernate created on
-- its own. Existing databases are not re-created: Flyway marks them as already at version 1 (see
-- spring.flyway.baseline-on-migrate) and only runs the versions after it.
--
-- Every later change is a new file V<n>__what_changed.sql in this folder. Never edit a file that has
-- already run anywhere — Flyway checks a checksum and refuses to start.

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `announcement_departments` (
  `announcement_id` varchar(36) NOT NULL,
  `department_id` varchar(36) NOT NULL,
  PRIMARY KEY (`announcement_id`,`department_id`),
  UNIQUE KEY `UKe0e99pr9781kd7tdekeyq7ta2` (`announcement_id`,`department_id`),
  KEY `fk_ad_department` (`department_id`),
  CONSTRAINT `fk_ad_announcement` FOREIGN KEY (`announcement_id`) REFERENCES `announcements` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ad_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `announcement_dismissals` (
  `announcement_id` varchar(36) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `dismissed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`announcement_id`,`user_id`),
  KEY `fk_adis_user` (`user_id`),
  CONSTRAINT `fk_adis_announcement` FOREIGN KEY (`announcement_id`) REFERENCES `announcements` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_adis_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `announcements` (
  `id` varchar(36) NOT NULL,
  `title` varchar(200) NOT NULL,
  `body` mediumtext NOT NULL,
  `org_wide` tinyint(1) NOT NULL DEFAULT '0',
  `show_until` date NOT NULL,
  `author_id` varchar(36) DEFAULT NULL,
  `author_name` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_announcements_show_until` (`show_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `certificates` (
  `id` varchar(36) NOT NULL,
  `code` varchar(20) NOT NULL,
  `type` int NOT NULL,
  `learner_id` varchar(36) NOT NULL,
  `learner_journey_id` varchar(36) DEFAULT NULL,
  `package_assignment_id` varchar(36) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `exam_score_percent` int DEFAULT NULL,
  `completed_at` datetime NOT NULL,
  `issued_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_certificates_code` (`code`),
  UNIQUE KEY `uq_certificates_lj` (`learner_journey_id`),
  UNIQUE KEY `uq_certificates_pa` (`package_assignment_id`),
  KEY `idx_certificates_learner` (`learner_id`),
  CONSTRAINT `fk_cert_learner` FOREIGN KEY (`learner_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cert_lj` FOREIGN KEY (`learner_journey_id`) REFERENCES `learner_journeys` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cert_pa` FOREIGN KEY (`package_assignment_id`) REFERENCES `package_assignments` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `departments` (
  `id` varchar(36) NOT NULL,
  `name_en` varchar(120) NOT NULL,
  `name_ar` varchar(120) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_departments_name_en` (`name_en`),
  UNIQUE KEY `uk_departments_name_ar` (`name_ar`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `exam_answers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempt_id` varchar(36) NOT NULL,
  `question_id` varchar(36) NOT NULL,
  `selected_option_index` int DEFAULT NULL,
  `bool_answer` tinyint(1) DEFAULT NULL,
  `open_text` text,
  `marked_correct` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_answer_attempt` (`attempt_id`),
  KEY `fk_answer_question` (`question_id`),
  CONSTRAINT `fk_answer_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `exam_attempts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_answer_question` FOREIGN KEY (`question_id`) REFERENCES `exam_questions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `exam_attempts` (
  `id` varchar(36) NOT NULL,
  `learner_journey_id` varchar(255) NOT NULL,
  `exam_id` varchar(36) NOT NULL,
  `status` int NOT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `graded_at` datetime DEFAULT NULL,
  `graded_by_id` varchar(255) DEFAULT NULL,
  `graded_by_name` varchar(255) DEFAULT NULL,
  `score_percent` int DEFAULT NULL,
  `passed` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_attempt_exam` (`exam_id`),
  CONSTRAINT `fk_attempt_exam` FOREIGN KEY (`exam_id`) REFERENCES `exams` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `exam_questions` (
  `id` varchar(36) NOT NULL,
  `exam_id` varchar(36) NOT NULL,
  `question_order` int DEFAULT NULL,
  `question_type` int NOT NULL,
  `prompt` text NOT NULL,
  `option_1` varchar(255) DEFAULT NULL,
  `option_2` varchar(255) DEFAULT NULL,
  `option_3` varchar(255) DEFAULT NULL,
  `option_4` varchar(255) DEFAULT NULL,
  `correct_option_index` int DEFAULT NULL,
  `correct_bool_answer` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_question_exam` (`exam_id`),
  CONSTRAINT `fk_question_exam` FOREIGN KEY (`exam_id`) REFERENCES `exams` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `exams` (
  `id` varchar(36) NOT NULL,
  `journey_id` varchar(36) NOT NULL,
  `title` varchar(255) NOT NULL,
  `passing_score_percent` int NOT NULL,
  `created_by_id` varchar(255) NOT NULL,
  `created_by_name` varchar(255) NOT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_exam_journey` (`journey_id`),
  CONSTRAINT `fk_exam_journey` FOREIGN KEY (`journey_id`) REFERENCES `journeys` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `journey_item_attachments` (
  `id` varchar(36) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `journey_item_id` varchar(36) NOT NULL,
  `kind` int NOT NULL,
  `label` varchar(255) DEFAULT NULL,
  `mime_type` varchar(127) DEFAULT NULL,
  `item_order` int DEFAULT NULL,
  `original_name` varchar(255) DEFAULT NULL,
  `size_bytes` bigint DEFAULT NULL,
  `storage_key` varchar(255) DEFAULT NULL,
  `url` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `journey_items` (
  `id` varchar(36) NOT NULL,
  `journey_id` varchar(36) NOT NULL,
  `unit_id` varchar(36) NOT NULL,
  `title` varchar(255) NOT NULL,
  `description` text,
  `item_order` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_journey_items_journey` (`journey_id`),
  KEY `fk_ji_unit` (`unit_id`),
  CONSTRAINT `fk_ji_unit` FOREIGN KEY (`unit_id`) REFERENCES `journey_units` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_journey_items_journey` FOREIGN KEY (`journey_id`) REFERENCES `journeys` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `journey_notes` (
  `id` varchar(36) NOT NULL,
  `learner_journey_item_id` varchar(36) DEFAULT NULL,
  `learner_journey_unit_id` varchar(36) DEFAULT NULL,
  `actor_id` varchar(36) NOT NULL,
  `actor_name` varchar(255) DEFAULT NULL,
  `actor_role` int DEFAULT NULL,
  `message` text NOT NULL,
  `created_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_note_item` (`learner_journey_item_id`),
  KEY `fk_note_unit` (`learner_journey_unit_id`),
  CONSTRAINT `fk_note_item` FOREIGN KEY (`learner_journey_item_id`) REFERENCES `learner_journey_items` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_note_unit` FOREIGN KEY (`learner_journey_unit_id`) REFERENCES `learner_journey_units` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `journey_units` (
  `id` varchar(36) NOT NULL,
  `journey_id` varchar(36) NOT NULL,
  `title` varchar(255) NOT NULL,
  `description` text,
  `unit_order` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_journey_units_journey` (`journey_id`),
  CONSTRAINT `fk_ju_journey` FOREIGN KEY (`journey_id`) REFERENCES `journeys` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `journeys` (
  `id` varchar(36) NOT NULL,
  `title` varchar(255) NOT NULL,
  `description` text,
  `tech_tag` varchar(255) DEFAULT NULL,
  `target_days` int DEFAULT NULL,
  `created_by_id` varchar(255) DEFAULT NULL,
  `created_by_name` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learner_journey_items` (
  `id` varchar(36) NOT NULL,
  `learner_journey_id` varchar(36) NOT NULL,
  `journey_item_id` varchar(36) NOT NULL,
  `status` int NOT NULL,
  `time_spent_hours` double DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_lji_lj` (`learner_journey_id`),
  KEY `fk_lji_item` (`journey_item_id`),
  CONSTRAINT `fk_lji_item` FOREIGN KEY (`journey_item_id`) REFERENCES `journey_items` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lji_lj` FOREIGN KEY (`learner_journey_id`) REFERENCES `learner_journeys` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learner_journey_units` (
  `id` varchar(36) NOT NULL,
  `learner_journey_id` varchar(36) NOT NULL,
  `unit_id` varchar(36) NOT NULL,
  `status` int NOT NULL,
  `quiz_answers` text,
  `quiz_score_percent` int DEFAULT NULL,
  `quiz_submitted_at` datetime DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lju_journey_unit` (`learner_journey_id`,`unit_id`),
  KEY `idx_lju_unit` (`unit_id`),
  CONSTRAINT `fk_lju_lj` FOREIGN KEY (`learner_journey_id`) REFERENCES `learner_journeys` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lju_unit` FOREIGN KEY (`unit_id`) REFERENCES `journey_units` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learner_journeys` (
  `id` varchar(36) NOT NULL,
  `journey_id` varchar(36) NOT NULL,
  `learner_id` varchar(36) NOT NULL,
  `assigned_by_id` varchar(36) NOT NULL,
  `assigned_by_name` varchar(255) DEFAULT NULL,
  `assigned_at` datetime NOT NULL,
  `due_date` date DEFAULT NULL,
  `due_soon_notified_at` datetime DEFAULT NULL,
  `overdue_notified_at` datetime DEFAULT NULL,
  `self_enrolled` tinyint(1) NOT NULL DEFAULT '0',
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `status` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_lj_journey` (`journey_id`),
  KEY `fk_lj_learner` (`learner_id`),
  CONSTRAINT `fk_lj_journey` FOREIGN KEY (`journey_id`) REFERENCES `journeys` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lj_learner` FOREIGN KEY (`learner_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notifications` (
  `id` varchar(36) NOT NULL,
  `channel` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `link` varchar(500) DEFAULT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `recipient_id` varchar(36) NOT NULL,
  `template_id` varchar(64) NOT NULL,
  `variables_json` text,
  PRIMARY KEY (`id`),
  KEY `idx_notifications_recipient` (`recipient_id`,`created_at`),
  KEY `idx_notifications_unread` (`recipient_id`,`read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `package_assignment_journeys` (
  `package_assignment_id` varchar(36) NOT NULL,
  `learner_journey_id` varchar(36) NOT NULL,
  `position` int NOT NULL,
  `created_by_package` tinyint(1) NOT NULL,
  PRIMARY KEY (`package_assignment_id`,`learner_journey_id`),
  KEY `idx_paj_learner_journey` (`learner_journey_id`),
  CONSTRAINT `fk_paj_assignment` FOREIGN KEY (`package_assignment_id`) REFERENCES `package_assignments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_paj_learner_journey` FOREIGN KEY (`learner_journey_id`) REFERENCES `learner_journeys` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `package_assignments` (
  `id` varchar(36) NOT NULL,
  `package_id` varchar(36) NOT NULL,
  `learner_id` varchar(36) NOT NULL,
  `assigned_by_id` varchar(36) DEFAULT NULL,
  `assigned_by_name` varchar(255) DEFAULT NULL,
  `assigned_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `due_date` date DEFAULT NULL,
  `self_enrolled` tinyint(1) NOT NULL DEFAULT '0',
  `cancelled_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_package_assignments_learner` (`learner_id`),
  KEY `fk_pa_package` (`package_id`),
  CONSTRAINT `fk_pa_learner` FOREIGN KEY (`learner_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pa_package` FOREIGN KEY (`package_id`) REFERENCES `packages` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `package_journeys` (
  `package_id` varchar(36) NOT NULL,
  `journey_id` varchar(36) NOT NULL,
  `position` int NOT NULL,
  PRIMARY KEY (`package_id`,`journey_id`),
  KEY `idx_package_journeys_journey` (`journey_id`),
  CONSTRAINT `fk_pj_journey` FOREIGN KEY (`journey_id`) REFERENCES `journeys` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pj_package` FOREIGN KEY (`package_id`) REFERENCES `packages` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `packages` (
  `id` varchar(36) NOT NULL,
  `title` varchar(255) NOT NULL,
  `description` text,
  `target_days` int DEFAULT NULL,
  `created_by_id` varchar(36) DEFAULT NULL,
  `created_by_name` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `unit_quiz_questions` (
  `id` varchar(36) NOT NULL,
  `unit_id` varchar(36) NOT NULL,
  `question_order` int NOT NULL,
  `question_type` int NOT NULL,
  `prompt` text NOT NULL,
  `option_1` varchar(255) DEFAULT NULL,
  `option_2` varchar(255) DEFAULT NULL,
  `option_3` varchar(255) DEFAULT NULL,
  `option_4` varchar(255) DEFAULT NULL,
  `correct_option_index` int DEFAULT NULL,
  `correct_bool_answer` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_uqq_unit` (`unit_id`),
  CONSTRAINT `fk_uqq_unit` FOREIGN KEY (`unit_id`) REFERENCES `journey_units` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `id` varchar(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `email` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` int NOT NULL,
  `department_id` varchar(36) NOT NULL,
  `senior_id` varchar(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `preferred_language` varchar(8) DEFAULT NULL,
  `intro_seen_version` int DEFAULT NULL,
  `getting_started_dismissed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`),
  KEY `fk_users_senior` (`senior_id`),
  KEY `fk_users_department` (`department_id`),
  CONSTRAINT `fk_users_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_users_senior` FOREIGN KEY (`senior_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
