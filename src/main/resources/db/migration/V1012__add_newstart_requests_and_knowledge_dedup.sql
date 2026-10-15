-- ============================================================
-- V1012: NewStart 질문요청 게시판 + 갈무리 중복검사 키
--   · newstart_requests: 회원 요청 INBOX. kind로 [질문·조사 요청]과 [사이트 개선 요청]을 한 테이블에서
--     구분한다 — 두 종류는 검토 주체와 상태 흐름이 같아서(접수→검토→답변/해결) 분리하면 목록·조인이 중복된다.
--   · 상태 8종(RECEIVED/REVIEWING/ANSWERED/ACCEPTED/IN_PROGRESS/RESOLVED/REJECTED/DUPLICATE)은
--     VARCHAR 저장 + enum 확장으로 future migration 없이 추가 가능.
--   · PII 정책: 채무 금액·기관명·개인식별정보를 담는 컬럼(problem/answer)은 회원 상호 열람 화면에서
--     운영자 마스킹을 전제한다. 원문 저장은 하되 공개 피드(갈무리)로 승격 시 구조화·비식별화 단계를 거친다.
--   · newstart_knowledge_items.dedup_key: 갈무리 파이프라인의 중복 제거 앵커. 기존 117행은 NULL로 두고
--     (MySQL UNIQUE는 NULL 중복 허용) 신규 적재분부터 기입 — 기존 행 소급 갱신은 별도 운영 작업.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE `newstart_requests` (
    `request_id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '작성자 users.user_id (요청자 본인 식별용)',
    `kind` VARCHAR(20) NOT NULL COMMENT 'QUESTION | SITE_IMPROVEMENT',
    `title` VARCHAR(200) NOT NULL COMMENT '요청 제목(PII 미포함)',
    `screen_location` VARCHAR(120) NULL COMMENT '개선 요청: 화면/위치 (예: HOME, A to Z STEP 12-3)',
    `a_to_z_step` VARCHAR(20) NULL COMMENT '개선 요청: 관련 A to Z STEP 번호 (예: 4-4, 12-3)',
    `problem` TEXT NOT NULL COMMENT '문제 내용 / 질문 내용',
    `proposal` TEXT NULL COMMENT '개선 제안 / 기대하는 답변',
    `status` VARCHAR(20) NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED|REVIEWING|ANSWERED|ACCEPTED|IN_PROGRESS|RESOLVED|REJECTED|DUPLICATE',
    `answer` TEXT NULL COMMENT '운영자 답변',
    `answered_by` VARCHAR(50) NULL COMMENT '답변자 표식',
    `resolved_at` TIMESTAMP NULL COMMENT '해결일(RESOLVED/DUPLICATE/REJECTED 처리 시점)',
    `github_ref` VARCHAR(120) NULL COMMENT '연결 GitHub issue/PR (수기 기입, 자동 생성 없음)',
    `resolved_version` VARCHAR(40) NULL COMMENT '반영 배포 버전/릴리즈 표식',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '소프트 삭제',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` VARCHAR(50),
    `updated_by` VARCHAR(50),
    PRIMARY KEY (`request_id`),
    KEY `idx_newstart_request_kind_status` (`kind`, `status`),
    KEY `idx_newstart_request_user` (`user_id`),
    KEY `idx_newstart_request_created` (`created_at`),
    CONSTRAINT `fk_newstart_request_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `newstart_request_status_logs` (
    `log_id` BIGINT NOT NULL AUTO_INCREMENT,
    `request_id` BIGINT NOT NULL,
    `from_status` VARCHAR(20) NULL COMMENT '첫 접수이면 NULL',
    `to_status` VARCHAR(20) NOT NULL,
    `actor` VARCHAR(50) NOT NULL COMMENT '상태 변경자(운영자 login id)',
    `reason` TEXT NULL COMMENT '거절/중복 사유 등 검토 기록',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`log_id`),
    KEY `idx_newstart_request_status_log_request` (`request_id`),
    CONSTRAINT `fk_newstart_request_status_log_request` FOREIGN KEY (`request_id`) REFERENCES `newstart_requests` (`request_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `newstart_knowledge_items`
    ADD COLUMN `dedup_key` VARCHAR(191) NULL COMMENT '갈무리 중복검사 앵커(source 성격+규범화 식별자)' AFTER `source_date`,
    ADD UNIQUE KEY `uk_newstart_knowledge_dedup` (`dedup_key`);
