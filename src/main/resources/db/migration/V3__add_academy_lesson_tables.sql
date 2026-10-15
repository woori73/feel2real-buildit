-- ============================================================
-- V3: Tutor Academy + Lesson Product foundation
--   · TUTOR가 공개 Academy를 만들고 수업 상품을 노출하기 위한 스키마
--   · 학생 검색/신청/결제는 후속 단계이므로 포함하지 않는다
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `academies` (
    `academy_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `tutor_user_id` BIGINT NOT NULL UNIQUE,
    `slug` VARCHAR(100) NOT NULL UNIQUE,
    `name` VARCHAR(100) NOT NULL,
    `logo_storage_key` VARCHAR(500) NOT NULL,
    `cover_storage_key` VARCHAR(500),
    `intro` TEXT NOT NULL,
    `status` ENUM('DRAFT', 'PUBLISHED') NOT NULL DEFAULT 'PUBLISHED',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    CONSTRAINT `fk_academies_tutor_user` FOREIGN KEY (`tutor_user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `academy_subjects` (
    `academy_id` BIGINT NOT NULL,
    `subject_id` INT NOT NULL,
    PRIMARY KEY (`academy_id`, `subject_id`),
    CONSTRAINT `fk_academy_subjects_academy` FOREIGN KEY (`academy_id`) REFERENCES `academies`(`academy_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_academy_subjects_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects`(`subject_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `academy_target_groups` (
    `academy_id` BIGINT NOT NULL,
    `target_group` ENUM('ELEMENTARY', 'MIDDLE', 'HIGH', 'UNIVERSITY', 'ADULT') NOT NULL,
    PRIMARY KEY (`academy_id`, `target_group`),
    CONSTRAINT `fk_academy_target_groups_academy` FOREIGN KEY (`academy_id`) REFERENCES `academies`(`academy_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `academy_regions` (
    `region_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `academy_id` BIGINT NOT NULL,
    `city` VARCHAR(50) NOT NULL,
    `district` VARCHAR(50) NOT NULL COMMENT 'ALL이면 해당 city 전 지역',
    `display_name` VARCHAR(100) NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    UNIQUE KEY `uk_academy_regions_city_district` (`academy_id`, `city`, `district`),
    CONSTRAINT `fk_academy_regions_academy` FOREIGN KEY (`academy_id`) REFERENCES `academies`(`academy_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `lesson_products` (
    `lesson_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `academy_id` BIGINT NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `description` TEXT,
    `lesson_type` ENUM('SUBSCRIPTION', 'SHORT_TERM') NOT NULL,
    `price_amount` INT,
    `price_unit` ENUM('MONTH', 'COURSE', 'NEGOTIABLE') NOT NULL,
    `price_summary` VARCHAR(100) NOT NULL,
    `max_capacity` INT NOT NULL,
    `current_count` INT NOT NULL DEFAULT 0,
    `schedule_type` ENUM('WEEKLY', 'PERIOD', 'CONSULTATION') NOT NULL,
    `weekly_count` INT,
    `start_date` DATE,
    `end_date` DATE,
    `duration_weeks` INT,
    `duration_months` INT,
    `schedule_summary` VARCHAR(200) NOT NULL,
    `status` ENUM('DRAFT', 'PUBLISHED', 'HIDDEN') NOT NULL DEFAULT 'PUBLISHED',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    INDEX `idx_lesson_products_academy_status` (`academy_id`, `status`),
    CONSTRAINT `fk_lesson_products_academy` FOREIGN KEY (`academy_id`) REFERENCES `academies`(`academy_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `lesson_weekdays` (
    `lesson_id` BIGINT NOT NULL,
    `weekday` ENUM('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY') NOT NULL,
    PRIMARY KEY (`lesson_id`, `weekday`),
    CONSTRAINT `fk_lesson_weekdays_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `lesson_products`(`lesson_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
