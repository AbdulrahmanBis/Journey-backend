-- MySQL dump 10.13  Distrib 8.0.38, for Win64 (x86_64)
--
-- Host: localhost    Database: journey_db
-- ------------------------------------------------------
-- Server version	8.0.38

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `exam_answers`
--

DROP TABLE IF EXISTS `exam_answers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exam_answers`
--

LOCK TABLES `exam_answers` WRITE;
/*!40000 ALTER TABLE `exam_answers` DISABLE KEYS */;
INSERT INTO `exam_answers` VALUES (1,'att-seed-1','eq-ng-1',1,NULL,NULL,1),(2,'att-seed-1','eq-ng-2',NULL,0,NULL,1),(3,'att-seed-1','eq-ng-3',NULL,NULL,'The Angular service calls HttpClient, Express receives the request, the route handler reads params, queries data, and sends back JSON which the service\'s subscriber uses to update the component.',NULL),(4,'att-seed-2','eq-ops-1',0,NULL,NULL,1),(5,'att-seed-2','eq-ops-2',NULL,0,NULL,1),(6,'att-seed-2','eq-ops-3',NULL,NULL,'We checked the alert, correlated logs in Splunk, identified the failing service, rolled back the bad deploy, and confirmed recovery before closing the incident.',1);
/*!40000 ALTER TABLE `exam_answers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exam_attempts`
--

DROP TABLE IF EXISTS `exam_attempts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exam_attempts`
--

LOCK TABLES `exam_attempts` WRITE;
/*!40000 ALTER TABLE `exam_attempts` DISABLE KEYS */;
INSERT INTO `exam_attempts` VALUES ('att-seed-1','lj-3','ex-ng',2,'2024-06-10 10:00:00',NULL,NULL,NULL,NULL,NULL),('att-seed-2','lj-4','ex-ops',3,'2024-05-03 08:30:00','2024-05-03 09:00:00','u-sr-1','Ali Tarek',100,1);
/*!40000 ALTER TABLE `exam_attempts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exam_questions`
--

DROP TABLE IF EXISTS `exam_questions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exam_questions`
--

LOCK TABLES `exam_questions` WRITE;
/*!40000 ALTER TABLE `exam_questions` DISABLE KEYS */;
INSERT INTO `exam_questions` VALUES ('eq-aws-1','ex-aws',1,1,'Which AWS service should you use to manage least-privilege access?','S3','IAM','CloudFront','Route 53',1,NULL),('eq-aws-2','ex-aws',2,2,'Should production deployments skip the staging approval step to save time?',NULL,NULL,NULL,NULL,NULL,0),('eq-aws-3','ex-aws',3,3,'Briefly describe what an Auto Scaling group is for, in your own words.',NULL,NULL,NULL,NULL,NULL,NULL),('eq-ng-1','ex-ng',1,1,'Which RxJS type holds a current value you can read synchronously?','Subject','BehaviorSubject','ReplaySubject','AsyncSubject',1,NULL),('eq-ng-2','ex-ng',2,2,'Is it safe to store a JWT in plain localStorage with no other mitigations for a production app?',NULL,NULL,NULL,NULL,NULL,0),('eq-ng-3','ex-ng',3,3,'Describe how a request flows from the Angular service to the Express route and back.',NULL,NULL,NULL,NULL,NULL,NULL),('eq-ops-1','ex-ops',1,1,'Which SPL command produces aggregated statistics?','stats','sort','rename','dedup',0,NULL),('eq-ops-2','ex-ops',2,2,'Should you silence a critical alert without investigating it first?',NULL,NULL,NULL,NULL,NULL,0),('eq-ops-3','ex-ops',3,3,'Summarize the on-call triage steps you shadowed.',NULL,NULL,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `exam_questions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exams`
--

DROP TABLE IF EXISTS `exams`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exams`
--

LOCK TABLES `exams` WRITE;
/*!40000 ALTER TABLE `exams` DISABLE KEYS */;
INSERT INTO `exams` VALUES ('ex-aws','j-aws','AWS DevOps Fundamentals — checkpoint',70,'u-sr-1','Ali Tarek',NULL,'2024-02-10 09:00:00'),('ex-ng','j-ng','Angular & Express Full-Stack — checkpoint',70,'u-sr-2','Lina Youssef',NULL,'2024-02-12 09:00:00'),('ex-ops','j-ops','Operations & Monitoring — checkpoint',70,'u-sr-1','Ali Tarek',NULL,'2024-02-14 09:00:00');
/*!40000 ALTER TABLE `exams` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `journey_items`
--

DROP TABLE IF EXISTS `journey_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `journey_items` (
  `id` varchar(36) NOT NULL,
  `journey_id` varchar(36) NOT NULL,
  `title` varchar(255) NOT NULL,
  `description` text,
  `item_order` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_journey_items_journey` (`journey_id`),
  CONSTRAINT `fk_journey_items_journey` FOREIGN KEY (`journey_id`) REFERENCES `journeys` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `journey_items`
--

LOCK TABLES `journey_items` WRITE;
/*!40000 ALTER TABLE `journey_items` DISABLE KEYS */;
INSERT INTO `journey_items` VALUES ('5dcdf78a-f5d1-4f1d-9094-eac06b1c9bdc','f7c570c2-7f13-4d7f-967a-5c263973b5cc','j','j',1),('ji-aws-1','j-aws','IAM basics','Understand users, roles and policies.\nSet up MFA on your sandbox account.\nReview the principle of least privilege.',1),('ji-aws-2','j-aws','EC2 & Auto Scaling','Launch an instance from our base AMI.\nConfigure an Auto Scaling group with a simple health check.',2),('ji-aws-3','j-aws','CI/CD with CodePipeline','Trace a deployment through CodePipeline and CodeBuild.\nIdentify where approvals happen in staging vs production.',3),('ji-aws-4','j-aws','CloudWatch monitoring','Find the dashboards for the team\'s main service.\nSet up one custom alarm on an error-rate metric.',4),('ji-aws-5','j-aws','Hands-on deploy lab','Ship a trivial change to the sandbox environment end-to-end with your assigned senior watching.',5),('ji-ng-1','j-ng','Angular component architecture','Read through the shared component library.\nBuild one small standalone component with an input and an output.',1),('ji-ng-2','j-ng','RxJS & state basics','Understand Observables vs Promises in our codebase.\nTrace one BehaviorSubject from service to template.',2),('ji-ng-3','j-ng','Express REST API basics','Add a new GET route to a sample Express service.\nUnderstand our middleware stack (auth, logging, error handling).',3),('ji-ng-4','j-ng','Auth & JWT','Walk through how a request gets authenticated end-to-end.\nIdentify where tokens are refreshed.',4),('ji-ng-5','j-ng','Connect Angular to the API','Wire a real HTTP call from an Angular service into a component, including loading and error states.',5),('ji-ops-1','j-ops','Splunk SPL basics','Write a search that filters last 24h of logs for one service.\nLearn stats, table and timechart commands.',1),('ji-ops-2','j-ops','Building dashboards','Clone an existing dashboard and adapt one panel to a different service.',2),('ji-ops-3','j-ops','Alerting & reporting','Set up a scheduled report.\nConfigure a threshold alert and understand who it notifies.',3),('ji-ops-4','j-ops','On-call runbook walkthrough','Read the current runbook with your senior.\nShadow the triage steps for one resolved incident.',4);
/*!40000 ALTER TABLE `journey_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `journey_notes`
--

DROP TABLE IF EXISTS `journey_notes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `journey_notes` (
  `id` varchar(36) NOT NULL,
  `learner_journey_item_id` varchar(36) NOT NULL,
  `actor_id` varchar(36) NOT NULL,
  `actor_name` varchar(255) DEFAULT NULL,
  `actor_role` int DEFAULT NULL,
  `message` text NOT NULL,
  `created_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_note_item` (`learner_journey_item_id`),
  CONSTRAINT `fk_note_item` FOREIGN KEY (`learner_journey_item_id`) REFERENCES `learner_journey_items` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `journey_notes`
--

LOCK TABLES `journey_notes` WRITE;
/*!40000 ALTER TABLE `journey_notes` DISABLE KEYS */;
INSERT INTO `journey_notes` VALUES ('2c97bd6b-ae9a-4a7f-91e1-7e18f4a7ce92','lji-2-4','u-sr-1','Ali Tarek',1003,'test','2026-07-30 11:49:59'),('6ff119cb-8df2-43fb-a0a5-f71f643c8d31','lji-2-4','u-sr-1','Ali Tarek',1003,'f','2026-07-30 11:58:31'),('8f43a765-dcdf-4f5b-920f-f1a54e0e6a7c','lji-2-3','u-ln-1','Omar Saeed',1004,'j','2026-07-30 12:05:34'),('fbdf57dc-ed69-4696-b4a2-66628b4df896','b679c1f6-fff4-4ef7-a44d-fb0917e62a59','u-sr-1','Ali Tarek',1003,'ي','2026-07-30 15:00:08'),('n-1','lji-1-1','u-ln-1','Omar Saeed',1004,'Set up MFA and reviewed the policy docs, all clear.','2026-07-03 10:50:00'),('n-2','lji-1-2','u-sr-1','Ali Tarek',1003,'Good first ASG setup — remember to set a cooldown next time.','2026-07-06 13:05:00'),('n-3','lji-1-3','u-ln-1','Omar Saeed',1004,'Not sure where the staging approval step happens — can we pair on this?','2026-07-10 09:00:00'),('n-4','lji-3-1','u-ln-2','Huda Nasser',1004,'Built a small badge component with an @Input status — felt smooth.','2026-07-07 14:50:00'),('n-5','lji-3-2','u-sr-2','Lina Youssef',1003,'Take your time on this one, RxJS clicks better after a second pass.','2026-07-12 10:05:00'),('n-6','lji-4-4','u-sr-1','Ali Tarek',1003,'Shadowed the SEV2 triage well — ready to join the on-call rotation.','2024-05-02 16:00:00');
/*!40000 ALTER TABLE `journey_notes` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `journeys`
--

DROP TABLE IF EXISTS `journeys`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `journeys` (
  `id` varchar(36) NOT NULL,
  `title` varchar(255) NOT NULL,
  `description` text,
  `tech_tag` varchar(255) DEFAULT NULL,
  `created_by_id` varchar(255) DEFAULT NULL,
  `created_by_name` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `journeys`
--

LOCK TABLES `journeys` WRITE;
/*!40000 ALTER TABLE `journeys` DISABLE KEYS */;
INSERT INTO `journeys` VALUES ('f7c570c2-7f13-4d7f-967a-5c263973b5cc','test','j','General','u-sr-1','Ali Tarek','2026-07-30 09:34:35','2026-07-30 09:34:35'),('j-aws','AWS DevOps Fundamentals','Core skills for operating and deploying our services on AWS: IAM, compute, CI/CD and observability.','Amazon DevOps','u-sr-1','Ali Tarek','2026-07-26 10:00:00','2026-07-26 10:00:00'),('j-ng','Angular & Express Full-Stack','Build and ship a feature end-to-end across our Angular frontend and Express API.','Angular / Express','u-sr-2','Lina Youssef','2026-07-26 10:00:00','2026-07-26 10:00:00'),('j-ops','Operations & Monitoring with Splunk','Read production signal with confidence: SPL queries, dashboards, alerting and the on-call runbook.','Splunk & Reporting','u-sr-1','Ali Tarek','2026-07-26 10:00:00','2026-07-26 10:00:00');
/*!40000 ALTER TABLE `journeys` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `learner_journey_items`
--

DROP TABLE IF EXISTS `learner_journey_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `learner_journey_items`
--

LOCK TABLES `learner_journey_items` WRITE;
/*!40000 ALTER TABLE `learner_journey_items` DISABLE KEYS */;
INSERT INTO `learner_journey_items` VALUES ('0139d40e-1d71-4084-849f-2868c1a284e0','1790aff0-7d97-4575-a4ef-b83718e1bb62','ji-ng-4',1001,NULL,'2026-07-30 09:33:20'),('1050d580-dddb-480f-85ad-5987a03c6131','1790aff0-7d97-4575-a4ef-b83718e1bb62','ji-ng-1',1001,NULL,'2026-07-30 09:33:20'),('1cf9c4bd-e928-41b7-9326-90cff96647c7','5efed059-0443-4709-affb-3bbc685e0491','ji-aws-4',1001,NULL,'2026-07-30 09:32:57'),('2d79ffea-01ce-48a9-8ef4-eeef0133904a','5efed059-0443-4709-affb-3bbc685e0491','ji-aws-5',1001,NULL,'2026-07-30 09:32:57'),('4a3401c0-bd02-4791-a5f5-a610cf9056e0','1790aff0-7d97-4575-a4ef-b83718e1bb62','ji-ng-5',1001,NULL,'2026-07-30 09:33:20'),('4f02c85f-c0bf-4b26-b742-9d7677a163c9','5efed059-0443-4709-affb-3bbc685e0491','ji-aws-3',1001,NULL,'2026-07-30 09:32:57'),('6bc7325e-aac5-4ab1-bc41-83ddfb30687e','1790aff0-7d97-4575-a4ef-b83718e1bb62','ji-ng-2',1001,NULL,'2026-07-30 09:33:20'),('8efd2010-6da9-400e-9a90-4f4020158866','abffe9e2-7ade-4a56-b7fd-7888a3663abc','5dcdf78a-f5d1-4f1d-9094-eac06b1c9bdc',1001,NULL,'2026-07-30 09:34:57'),('9d3b59e7-dd5f-4f13-ac85-561bd64c1a10','5efed059-0443-4709-affb-3bbc685e0491','ji-aws-2',1001,NULL,'2026-07-30 09:32:57'),('a5627612-fbc6-4c65-b177-9ed6c18ce80c','1790aff0-7d97-4575-a4ef-b83718e1bb62','ji-ng-3',1001,NULL,'2026-07-30 09:33:20'),('b679c1f6-fff4-4ef7-a44d-fb0917e62a59','5efed059-0443-4709-affb-3bbc685e0491','ji-aws-1',1003,NULL,'2026-07-30 15:00:13'),('lji-1-1','lj-1','ji-aws-1',1004,2,'2026-07-03 11:00:00'),('lji-1-2','lj-1','ji-aws-2',1004,3,'2026-07-06 13:00:00'),('lji-1-3','lj-1','ji-aws-3',1001,NULL,'2026-07-10 09:00:00'),('lji-1-4','lj-1','ji-aws-4',1001,NULL,'2026-07-02 09:30:00'),('lji-1-5','lj-1','ji-aws-5',1001,NULL,'2026-07-02 09:30:00'),('lji-2-1','lj-2','ji-ops-1',1004,7,'2026-07-30 12:05:56'),('lji-2-2','lj-2','ji-ops-2',1005,NULL,'2026-07-30 11:17:45'),('lji-2-3','lj-2','ji-ops-3',1002,NULL,'2026-07-30 11:17:50'),('lji-2-4','lj-2','ji-ops-4',1001,NULL,'2026-07-20 09:00:00'),('lji-3-1','lj-3','ji-ng-1',1004,1.5,'2026-07-07 15:00:00'),('lji-3-2','lj-3','ji-ng-2',1004,2,'2024-05-20 10:00:00'),('lji-3-3','lj-3','ji-ng-3',1004,1,'2024-05-28 10:00:00'),('lji-3-4','lj-3','ji-ng-4',1004,1.5,'2024-06-03 10:00:00'),('lji-3-5','lj-3','ji-ng-5',1004,2,'2024-06-09 15:00:00'),('lji-4-1','lj-4','ji-ops-1',1004,2,'2024-04-18 09:00:00'),('lji-4-2','lj-4','ji-ops-2',1004,2.5,'2024-04-22 09:00:00'),('lji-4-3','lj-4','ji-ops-3',1004,1,'2024-04-26 09:00:00'),('lji-4-4','lj-4','ji-ops-4',1004,3,'2024-05-02 16:00:00');
/*!40000 ALTER TABLE `learner_journey_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `learner_journeys`
--

DROP TABLE IF EXISTS `learner_journeys`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learner_journeys` (
  `id` varchar(36) NOT NULL,
  `journey_id` varchar(36) NOT NULL,
  `learner_id` varchar(36) NOT NULL,
  `assigned_by_id` varchar(36) NOT NULL,
  `assigned_by_name` varchar(255) DEFAULT NULL,
  `assigned_at` datetime NOT NULL,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `status` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_lj_journey` (`journey_id`),
  KEY `fk_lj_learner` (`learner_id`),
  CONSTRAINT `fk_lj_journey` FOREIGN KEY (`journey_id`) REFERENCES `journeys` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lj_learner` FOREIGN KEY (`learner_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `learner_journeys`
--

LOCK TABLES `learner_journeys` WRITE;
/*!40000 ALTER TABLE `learner_journeys` DISABLE KEYS */;
INSERT INTO `learner_journeys` VALUES ('1790aff0-7d97-4575-a4ef-b83718e1bb62','j-ng','u-ln-3','u-sr-1','Ali Tarek','2026-07-30 09:33:19',NULL,NULL,1001),('5efed059-0443-4709-affb-3bbc685e0491','j-aws','u-ln-3','u-sr-1','Ali Tarek','2026-07-30 09:32:57','2026-07-30 15:00:13',NULL,1002),('abffe9e2-7ade-4a56-b7fd-7888a3663abc','f7c570c2-7f13-4d7f-967a-5c263973b5cc','u-ln-1','u-sr-1','Ali Tarek','2026-07-30 09:34:57',NULL,NULL,1005),('lj-1','j-aws','u-ln-1','u-sr-1','Ali Tarek','2026-07-02 09:00:00','2026-07-02 09:30:00',NULL,1001),('lj-2','j-ops','u-ln-1','u-sr-1','Ali Tarek','2026-07-20 09:00:00','2026-07-30 11:17:01',NULL,1002),('lj-3','j-ng','u-ln-2','u-sr-2','Lina Youssef','2026-07-05 09:00:00','2026-07-05 10:00:00','2024-06-09 15:00:00',1004),('lj-4','j-ops','u-ln-3','u-sr-1','Ali Tarek','2024-04-16 09:00:00','2024-04-16 09:30:00','2024-05-02 16:00:00',1004);
/*!40000 ALTER TABLE `learner_journeys` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notes`
--

DROP TABLE IF EXISTS `notes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notes` (
  `id` varchar(36) NOT NULL,
  `actor_id` varchar(36) DEFAULT NULL,
  `actor_name` varchar(255) DEFAULT NULL,
  `actor_role` enum('ADMIN','LEARNER','MANAGER','SENIOR') DEFAULT NULL,
  `learner_journey_item_id` varchar(36) NOT NULL,
  `message` text NOT NULL,
  `timestamp` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdnd6cih5olp89aeqg7u6c9e0b` (`learner_journey_item_id`),
  CONSTRAINT `FKdnd6cih5olp89aeqg7u6c9e0b` FOREIGN KEY (`learner_journey_item_id`) REFERENCES `learner_journey_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notes`
--

LOCK TABLES `notes` WRITE;
/*!40000 ALTER TABLE `notes` DISABLE KEYS */;
/*!40000 ALTER TABLE `notes` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` varchar(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `email` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` int NOT NULL,
  `senior_id` varchar(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`),
  KEY `fk_users_senior` (`senior_id`),
  CONSTRAINT `fk_users_senior` FOREIGN KEY (`senior_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES ('u-admin','Sara Hassan','admin@company.io','$2a$10$tBvZB3MuWW697AcYEMW4ru0WZlWVOT4OsWOTatUXCXHmcWKQNvrfe',1001,NULL,'2024-01-08 09:00:00'),('u-ln-1','Omar Saeed','learner1@company.io','$2a$10$75vNkaWiKQQYLyfuQvmJJe88GaWFHJ4ub8NyZFKds2k8To0WFiqmG',1004,'u-sr-1','2024-03-01 09:00:00'),('u-ln-2','Huda Nasser','learner2@company.io','$2a$10$75vNkaWiKQQYLyfuQvmJJe88GaWFHJ4ub8NyZFKds2k8To0WFiqmG',1004,'u-sr-2','2024-03-04 09:00:00'),('u-ln-3','Yousef Adel','learner3@company.io','$2a$10$75vNkaWiKQQYLyfuQvmJJe88GaWFHJ4ub8NyZFKds2k8To0WFiqmG',1004,'u-sr-1','2024-04-15 09:00:00'),('u-mgr','Mona Karim','manager@company.io','$2a$10$K264HYLySRZIK5G4sj5Y..aibbeUkgDHKLHL4dq5o/iHUMm33c2Z6',1002,NULL,'2024-01-08 09:00:00'),('u-sr-1','Ali Tarek','senior1@company.io','$2a$10$dKsaSqgdMm2EZo7sUdWqfeOG4h8akyyHogqH3tNwHA3CQ0D2/m4Iu',1003,NULL,'2024-01-10 09:00:00'),('u-sr-2','Lina Youssef','senior2@company.io','$2a$10$dKsaSqgdMm2EZo7sUdWqfeOG4h8akyyHogqH3tNwHA3CQ0D2/m4Iu',1003,NULL,'2024-01-10 09:00:00');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-03 15:05:52
