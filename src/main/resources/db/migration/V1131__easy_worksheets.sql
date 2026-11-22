-- 수업 준비 › 학습지 (2026-11-22). 선생님이 F2R에 이미 있는 문항(내신 예상문제 tp_questions, 예비고1 어법 문항)을 골라
-- 학습지로 묶고, 학생·반에 배정한다. 문항 내용은 복사하지 않고 출처(source_type + source_ref)만 적는다.
-- 삭제는 휴지통(deleted_at)으로 보내고, 휴지통에서 복구하거나 영구 삭제한다. 설계: docs/lesson-prep/README.md

-- 1) 학습지
CREATE TABLE `easy_worksheets` (
    `worksheet_id`   BIGINT       NOT NULL AUTO_INCREMENT,
    `business_id`    BIGINT       NOT NULL,
    `owner_user_id`  BIGINT       NOT NULL COMMENT '만든 선생님(users.user_id)',
    `owner_name`     VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '목록 표시용 만든 사람 이름',
    `title`          VARCHAR(120) NOT NULL,
    `description`    VARCHAR(500) NULL,
    `status`         VARCHAR(10)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT(작성 중)/ACTIVE(사용 중)/HIDDEN(숨김)',
    `question_count` INT          NOT NULL DEFAULT 0,
    `deleted_at`     DATETIME     NULL COMMENT '휴지통으로 보낸 시각(NULL=목록에 보임)',
    `deleted_by`     BIGINT       NULL,
    `created_at`     DATETIME     NOT NULL,
    `updated_at`     DATETIME     NOT NULL,
    PRIMARY KEY (`worksheet_id`),
    INDEX `idx_easy_worksheets_business` (`business_id`, `deleted_at`, `updated_at`),
    INDEX `idx_easy_worksheets_owner` (`owner_user_id`),
    CONSTRAINT `fk_easy_worksheets_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 학습지 문항: 출처만 가리킨다. NAESIN = tp_questions.question_id, GRAMMAR = '{시험 키}:{문항 번호}'(예: 3-1:7)
CREATE TABLE `easy_worksheet_items` (
    `item_id`      BIGINT      NOT NULL AUTO_INCREMENT,
    `worksheet_id` BIGINT      NOT NULL,
    `order_no`     INT         NOT NULL,
    `source_type`  VARCHAR(10) NOT NULL COMMENT 'NAESIN/GRAMMAR',
    `source_ref`   VARCHAR(40) NOT NULL,
    PRIMARY KEY (`item_id`),
    UNIQUE KEY `uk_easy_worksheet_items_source` (`worksheet_id`, `source_type`, `source_ref`),
    INDEX `idx_easy_worksheet_items_order` (`worksheet_id`, `order_no`),
    CONSTRAINT `fk_easy_worksheet_items_worksheet` FOREIGN KEY (`worksheet_id`) REFERENCES `easy_worksheets` (`worksheet_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 즐겨찾기: 선생님마다 따로
CREATE TABLE `easy_worksheet_favorites` (
    `favorite_id`  BIGINT   NOT NULL AUTO_INCREMENT,
    `worksheet_id` BIGINT   NOT NULL,
    `user_id`      BIGINT   NOT NULL,
    `created_at`   DATETIME NOT NULL,
    PRIMARY KEY (`favorite_id`),
    UNIQUE KEY `uk_easy_worksheet_favorites` (`worksheet_id`, `user_id`),
    INDEX `idx_easy_worksheet_favorites_user` (`user_id`),
    CONSTRAINT `fk_easy_worksheet_favorites_worksheet` FOREIGN KEY (`worksheet_id`) REFERENCES `easy_worksheets` (`worksheet_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 배정: 학생마다 한 줄. 반으로 배정하면 그때 그 반 학생마다 한 줄씩 만들고 class_id를 적는다.
--    빼면 removed_at만 채운다(다시 배정하면 비운다). 상태는 선생님이 바꾼다(채점 연결은 2차).
CREATE TABLE `easy_worksheet_assignments` (
    `assignment_id`     BIGINT      NOT NULL AUTO_INCREMENT,
    `worksheet_id`      BIGINT      NOT NULL,
    `business_id`       BIGINT      NOT NULL,
    `student_id`        BIGINT      NOT NULL,
    `class_id`          BIGINT      NULL COMMENT '반으로 배정했을 때의 반',
    `status`            VARCHAR(12) NOT NULL DEFAULT 'NOT_STARTED' COMMENT 'NOT_STARTED(시작 전)/IN_PROGRESS(진행 중)/DONE(완료)',
    `assigned_by`       BIGINT      NULL,
    `assigned_at`       DATETIME    NOT NULL,
    `status_changed_at` DATETIME    NULL,
    `removed_at`        DATETIME    NULL,
    PRIMARY KEY (`assignment_id`),
    UNIQUE KEY `uk_easy_worksheet_assignments` (`worksheet_id`, `student_id`),
    INDEX `idx_easy_worksheet_assignments_student` (`business_id`, `student_id`),
    CONSTRAINT `fk_easy_worksheet_assignments_worksheet` FOREIGN KEY (`worksheet_id`) REFERENCES `easy_worksheets` (`worksheet_id`),
    CONSTRAINT `fk_easy_worksheet_assignments_business` FOREIGN KEY (`business_id`) REFERENCES `easy_businesses` (`business_id`),
    CONSTRAINT `fk_easy_worksheet_assignments_student` FOREIGN KEY (`student_id`) REFERENCES `easy_students` (`student_id`),
    CONSTRAINT `fk_easy_worksheet_assignments_class` FOREIGN KEY (`class_id`) REFERENCES `easy_classes` (`class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
