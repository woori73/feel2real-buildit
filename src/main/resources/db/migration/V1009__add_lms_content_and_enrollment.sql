SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 에듀홀릭 LMS Phase 1 — 재원생 시험 전 복습영상 콘텐츠 + 무료 수강권(작업지시서 §1·§2·§6).
--
-- 설계 원칙:
--   * LmsContent(학생 학습 콘텐츠)는 AcademyVideo(홈페이지 홍보영상)와 완전히 분리한다.
--     video_id 참조도 하지 않는다 — 저장/재생 "패턴"만 academy_videos 파이프라인
--     (FileStorage / VideoTranscoder / AcademyVideoDeliveryStorage)에서 재사용하고,
--     콘텐츠 자체는 독립 storage_key를 가진 새 행이다.
--   * LmsEnrollment(수강권)는 기존 lesson_applications(신청)를 확장하지 않는다 — 별도 개념.
--     Phase 1은 grant_source = ACADEMY|ADMIN 무료 배정만 실제로 쓰인다. PURCHASE는 값만
--     미리 열어 둔다(향후 유료 영문법 강좌 PG 연동).
--   * 신규 테이블만 추가 → 1단계 마이그레이션. 다음 마이그레이션 = V1010.
--   * renumber 이력: PR #403 원안은 V1001이었으나, main에 V1002~V1008이 먼저 병합됐고
--     Flyway outOfOrder=false라 순서 역행을 막기 위해 V1009로 이동(WORKFLOW.md §4).
--     내용은 V1001 원안과 동일하며 DROP/TRUNCATE/백필 없음.

CREATE TABLE lms_contents (
    lms_content_id   BIGINT        NOT NULL AUTO_INCREMENT,
    academy_id       BIGINT        NOT NULL,
    lesson_id        BIGINT        NOT NULL,
    title            VARCHAR(200)  NOT NULL,
    description      TEXT          NULL,
    content_type     VARCHAR(20)   NOT NULL DEFAULT 'VIDEO',
    original_filename VARCHAR(255) NULL,
    storage_key      VARCHAR(500)  NULL,             -- FileStorage.saveRaw() 반환값(정규화 mp4)
    thumbnail_key    VARCHAR(500)  NULL,
    duration_sec     INT           NULL,
    size_bytes       BIGINT        NULL,
    width            INT           NULL,
    height           INT           NULL,
    school_name      VARCHAR(100)  NULL,
    grade_label      VARCHAR(50)   NULL,
    exam_name        VARCHAR(60)   NULL,              -- "중간고사" | "기말고사" 등
    unit_label       VARCHAR(100)  NULL,               -- "Lesson 5" 등
    display_order    INT           NOT NULL DEFAULT 0,
    status           VARCHAR(20)   NOT NULL DEFAULT 'READY', -- UPLOADING|READY|PUBLISHED|HIDDEN|FAILED
    published_at     DATETIME(6)   NULL,
    created_at       DATETIME(6),
    updated_at       DATETIME(6),
    created_by       VARCHAR(50),
    updated_by       VARCHAR(50),
    PRIMARY KEY (lms_content_id),
    INDEX idx_lms_contents_lesson (lesson_id, status, display_order),
    INDEX idx_lms_contents_academy (academy_id, status),
    CONSTRAINT fk_lms_contents_academy
        FOREIGN KEY (academy_id) REFERENCES academies (academy_id) ON DELETE CASCADE,
    CONSTRAINT fk_lms_contents_lesson
        FOREIGN KEY (lesson_id) REFERENCES lesson_products (lesson_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lms_enrollments (
    lms_enrollment_id BIGINT       NOT NULL AUTO_INCREMENT,
    student_id         BIGINT      NOT NULL,
    academy_id         BIGINT      NOT NULL,
    lesson_id          BIGINT      NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE|REVOKED|EXPIRED
    grant_source        VARCHAR(20) NOT NULL,                   -- ACADEMY|ADMIN|PURCHASE
    granted_at           DATETIME(6) NOT NULL,
    expires_at            DATETIME(6) NULL,
    created_at       DATETIME(6),
    updated_at       DATETIME(6),
    created_by       VARCHAR(50),
    updated_by       VARCHAR(50),
    PRIMARY KEY (lms_enrollment_id),
    UNIQUE KEY uk_lms_enrollment_student_lesson (student_id, lesson_id),
    INDEX idx_lms_enrollment_lesson (lesson_id, status),
    CONSTRAINT fk_lms_enrollment_student
        FOREIGN KEY (student_id) REFERENCES students (student_id) ON DELETE CASCADE,
    CONSTRAINT fk_lms_enrollment_academy
        FOREIGN KEY (academy_id) REFERENCES academies (academy_id) ON DELETE CASCADE,
    CONSTRAINT fk_lms_enrollment_lesson
        FOREIGN KEY (lesson_id) REFERENCES lesson_products (lesson_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
