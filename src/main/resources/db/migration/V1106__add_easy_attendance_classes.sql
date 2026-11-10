-- ============================================================
-- V1106: F2R 출결부 — 반·반 배정·출결 기록 (AcademyCraft 반별 출결부를 F2R 안으로)
--   · 학생은 기존 easy_students(장부 학생 원장)를 그대로 쓴다.
--   · additive only: 기존 테이블·행 변경 없음.
--   · 삭제 정책: 출결은 지우지 않고 정정 행 + 사유로 바꾼다(이전 행은 superseded=TRUE로 보존).
--     반은 지우지 않고 종료(ENDED)한다. 반 배정은 released_on으로 해제 이력을 남긴다.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `easy_classes` (
    `class_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL COMMENT '학원 사업체(easy_businesses)',
    `name` VARCHAR(60) NOT NULL,
    `teacher_name` VARCHAR(30) NULL COMMENT '담당 강사 이름(표시용)',
    `description` VARCHAR(300) NULL,
    `weekdays` VARCHAR(30) NULL COMMENT '수업 요일 MON,WED 형식',
    `start_time` TIME NULL,
    `end_time` TIME NULL,
    `room` VARCHAR(30) NULL,
    `capacity` INT NULL COMMENT '정원(NULL=제한 없음)',
    `status` ENUM('ACTIVE', 'ENDED') NOT NULL DEFAULT 'ACTIVE',
    `ended_at` TIMESTAMP NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    INDEX `idx_easy_classes_business_status` (`business_id`, `status`),
    CONSTRAINT `fk_easy_classes_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `easy_class_members` (
    `member_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `class_id` BIGINT NOT NULL,
    `student_id` BIGINT NOT NULL,
    `assigned_on` DATE NOT NULL,
    `released_on` DATE NULL COMMENT 'NULL=현재 배정 중',
    `release_reason` VARCHAR(20) NULL COMMENT 'REMOVED / MOVED / CLASS_ENDED',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    INDEX `idx_easy_class_members_class` (`class_id`, `released_on`),
    INDEX `idx_easy_class_members_student` (`student_id`),
    CONSTRAINT `fk_easy_class_members_class` FOREIGN KEY (`class_id`) REFERENCES `easy_classes` (`class_id`),
    CONSTRAINT `fk_easy_class_members_student` FOREIGN KEY (`student_id`) REFERENCES `easy_students` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `easy_attendance_events` (
    `event_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `business_id` BIGINT NOT NULL,
    `class_id` BIGINT NULL COMMENT '반 없이 들어온 기록(진행표 가져오기 등)은 NULL',
    `student_id` BIGINT NOT NULL,
    `occurred_on` DATE NOT NULL,
    `occurred_at` DATETIME NOT NULL COMMENT '한국 시각(KST) 그대로 저장',
    `event_type` ENUM('CHECK_IN', 'CHECK_OUT', 'ABSENT', 'LATE', 'EARLY_LEAVE') NOT NULL,
    `note` VARCHAR(200) NULL,
    `source` ENUM('MANUAL', 'ROSTER_IMPORT') NOT NULL DEFAULT 'MANUAL',
    `source_key` VARCHAR(120) NULL COMMENT '가져오기 중복 방지 키',
    `corrects_event_id` BIGINT NULL COMMENT '이 행이 정정한 이전 기록',
    `correction_reason` VARCHAR(200) NULL,
    `superseded` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '정정되어 더 이상 현재 상태가 아님(삭제 대신 보존)',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    UNIQUE KEY `uk_easy_attendance_source_key` (`source_key`),
    INDEX `idx_easy_attendance_student_day` (`student_id`, `occurred_on`),
    INDEX `idx_easy_attendance_class_day` (`class_id`, `occurred_on`),
    INDEX `idx_easy_attendance_business_day` (`business_id`, `occurred_on`),
    CONSTRAINT `fk_easy_attendance_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    CONSTRAINT `fk_easy_attendance_class` FOREIGN KEY (`class_id`) REFERENCES `easy_classes` (`class_id`),
    CONSTRAINT `fk_easy_attendance_student` FOREIGN KEY (`student_id`) REFERENCES `easy_students` (`student_id`),
    CONSTRAINT `fk_easy_attendance_corrects` FOREIGN KEY (`corrects_event_id`) REFERENCES `easy_attendance_events` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
