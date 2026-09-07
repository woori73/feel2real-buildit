SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 압구정에듀홀릭 "2026 2학기 내신 LIVE" — 학교별 시험 준비를 시간 흐름으로 공개하는
-- Public Showcase 의 운영 데이터. 전부 academy 스코프(academy_id / 상위 FK)라 다른 Academy
-- 홈페이지에는 영향이 없다. 신규 테이블만 추가 → 1단계 마이그레이션. 다음 마이그레이션 = V969.
--
-- 설계 원칙:
--   * 원장 브리핑 영상은 기존 academy_videos 를 재사용한다. 여기서는 video_id 를 참조만 한다
--     (ON DELETE SET NULL — 영상이 지워져도 캠페인 행은 살아남는다).
--   * 자료(Material Showcase)는 기존 Wiki 자료(wiki_analysis_materials / problem_generator_materials)를
--     source_type + source_material_id 로 "참조"한다. 원본 파일을 이 스키마로 복제하지 않는다.
--   * 공개 범위(visibility)는 프런트 숨김이 아니라 서버가 판정한다(MidtermMaterialAccessService).

-- ── 캠페인(학원당 활성 1개) ────────────────────────────────────────────────
CREATE TABLE midterm_campaign (
    campaign_id        BIGINT       NOT NULL AUTO_INCREMENT,
    academy_id         BIGINT       NOT NULL,
    exam_name          VARCHAR(120) NOT NULL,                    -- "2026 2학기 중간고사"
    exam_year          INT          NOT NULL,
    semester           VARCHAR(20)  NOT NULL,                    -- "2학기"
    exam_type          VARCHAR(40)  NOT NULL,                    -- "중간고사"
    period_start       DATE         NULL,
    period_end         DATE         NULL,
    state              VARCHAR(20)  NOT NULL DEFAULT 'PREPARING', -- PREPARING|FINAL_WEEK|EXAM_DONE|NEXT_EXAM
    hero_eyebrow       VARCHAR(120) NULL,                        -- "2026 FALL MIDTERM · APGUJEONG EDUHOLIC"
    hero_title         VARCHAR(200) NULL,
    hero_body          VARCHAR(600) NULL,
    hero_cta_label     VARCHAR(40)  NULL,                        -- "우리 학교 찾기"
    published          BIT(1)       NOT NULL DEFAULT b'0',
    popup_enabled      BIT(1)       NOT NULL DEFAULT b'0',
    popup_headline     VARCHAR(120) NULL,
    popup_body         VARCHAR(400) NULL,
    popup_visual_key   VARCHAR(500) NULL,                        -- storage key (기존 이미지 업로드 재사용)
    created_at         DATETIME(6),
    updated_at         DATETIME(6),
    created_by         VARCHAR(50),
    updated_by         VARCHAR(50),
    PRIMARY KEY (campaign_id),
    UNIQUE KEY uk_midterm_campaign_academy (academy_id),
    CONSTRAINT fk_midterm_campaign_academy
        FOREIGN KEY (academy_id) REFERENCES academies (academy_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 학교 컬렉션(Shopify Product Collection) ─────────────────────────────────
CREATE TABLE midterm_school_collection (
    school_collection_id  BIGINT       NOT NULL AUTO_INCREMENT,
    campaign_id           BIGINT       NOT NULL,
    school_name           VARCHAR(100) NOT NULL,                 -- "신구중"
    grade                 INT          NOT NULL,                 -- 1|2|3 (중1~중3 / 고1~고3, 표시 문자열은 프런트)
    exam_title            VARCHAR(120) NULL,                     -- "2026 FALL MIDTERM"
    status_label          VARCHAR(60)  NULL,                     -- "현재 준비 중"
    prep_headline         VARCHAR(200) NULL,                     -- 준비상태 섹션 리드
    briefing_video_id     BIGINT       NULL,                     -- academy_videos 참조(선택 연결)
    briefing_title        VARCHAR(160) NULL,
    briefing_description   VARCHAR(600) NULL,
    briefing_shoot_date   DATE         NULL,
    briefing_publish_date DATE         NULL,
    briefing_published    BIT(1)       NOT NULL DEFAULT b'0',
    exam_feature_note     TEXT         NULL,                     -- "이번 시험 특징"
    sort_order            INT          NOT NULL DEFAULT 0,
    published             BIT(1)       NOT NULL DEFAULT b'0',
    created_at            DATETIME(6),
    updated_at            DATETIME(6),
    created_by            VARCHAR(50),
    updated_by            VARCHAR(50),
    PRIMARY KEY (school_collection_id),
    INDEX idx_midterm_school_collection_campaign (campaign_id, published, sort_order),
    CONSTRAINT fk_midterm_school_collection_campaign
        FOREIGN KEY (campaign_id) REFERENCES midterm_campaign (campaign_id) ON DELETE CASCADE,
    CONSTRAINT fk_midterm_school_collection_video
        FOREIGN KEY (briefing_video_id) REFERENCES academy_videos (video_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 준비 상태(NOW PREPARING) ───────────────────────────────────────────────
CREATE TABLE midterm_prep_status (
    prep_status_id       BIGINT      NOT NULL AUTO_INCREMENT,
    school_collection_id BIGINT      NOT NULL,
    step_key             VARCHAR(20) NOT NULL,                   -- SCHOOL_PRINT|ANALYSIS|WORKSHEET|VARIANT|FINAL_REVIEW
    label                VARCHAR(80) NOT NULL,
    state                VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED', -- NOT_STARTED|IN_PROGRESS|DONE
    sort_order           INT         NOT NULL DEFAULT 0,
    created_at           DATETIME(6),
    updated_at           DATETIME(6),
    created_by           VARCHAR(50),
    updated_by           VARCHAR(50),
    PRIMARY KEY (prep_status_id),
    INDEX idx_midterm_prep_status_collection (school_collection_id, sort_order),
    CONSTRAINT fk_midterm_prep_status_collection
        FOREIGN KEY (school_collection_id) REFERENCES midterm_school_collection (school_collection_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 자료 Showcase(기존 Wiki 자료 참조 + 학부모용 표현 메타데이터) ────────────
CREATE TABLE midterm_material (
    midterm_material_id  BIGINT        NOT NULL AUTO_INCREMENT,
    campaign_id          BIGINT        NOT NULL,
    school_collection_id BIGINT        NULL,                     -- NULL=캠페인 공용 자료
    category             VARCHAR(30)   NOT NULL DEFAULT 'OTHER', -- SCHOOL_PRINT|ANALYSIS|WORKSHEET|VOCABULARY|GRAMMAR|VARIANT|FINAL_REVIEW|OTHER
    display_title        VARCHAR(160)  NOT NULL,
    blurb                VARCHAR(1000) NULL,                     -- 학부모용 한 줄 설명
    principal_comment    VARCHAR(1000) NULL,                     -- "박정민 원장 COMMENT"
    checklist            TEXT          NULL,                     -- 줄바꿈 구분 체크 항목("핵심 어휘\n본문 이해\n...")
    source_type          VARCHAR(20)   NOT NULL DEFAULT 'NONE',  -- NONE|WIKI_ANALYSIS|WIKI_LEARNING|PG_MATERIAL
    source_material_id   BIGINT        NULL,
    visibility           VARCHAR(20)   NOT NULL DEFAULT 'PRIVATE', -- PRIVATE|PREVIEW|PUBLIC|MEMBER
    preview_page_cap     INT           NULL,                     -- PREVIEW 시 노출 페이지 수(Phase 2에서 서버 절단)
    thumbnail_key        VARCHAR(500)  NULL,
    sort_order           INT           NOT NULL DEFAULT 0,
    published            BIT(1)        NOT NULL DEFAULT b'0',
    created_at           DATETIME(6),
    updated_at           DATETIME(6),
    created_by           VARCHAR(50),
    updated_by           VARCHAR(50),
    PRIMARY KEY (midterm_material_id),
    INDEX idx_midterm_material_campaign (campaign_id, published, sort_order),
    INDEX idx_midterm_material_collection (school_collection_id, published, sort_order),
    CONSTRAINT fk_midterm_material_campaign
        FOREIGN KEY (campaign_id) REFERENCES midterm_campaign (campaign_id) ON DELETE CASCADE,
    CONSTRAINT fk_midterm_material_collection
        FOREIGN KEY (school_collection_id) REFERENCES midterm_school_collection (school_collection_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 업데이트 로그(MIDTERM LOG) ─────────────────────────────────────────────
CREATE TABLE midterm_update_log (
    update_log_id        BIGINT        NOT NULL AUTO_INCREMENT,
    campaign_id          BIGINT        NOT NULL,
    school_collection_id BIGINT        NULL,
    log_date             DATE          NOT NULL,
    headline             VARCHAR(200)  NOT NULL,
    body                 VARCHAR(1000) NULL,
    published            BIT(1)        NOT NULL DEFAULT b'1',
    sort_order           INT           NOT NULL DEFAULT 0,
    created_at           DATETIME(6),
    updated_at           DATETIME(6),
    created_by           VARCHAR(50),
    updated_by           VARCHAR(50),
    PRIMARY KEY (update_log_id),
    INDEX idx_midterm_update_log_campaign (campaign_id, published, log_date),
    CONSTRAINT fk_midterm_update_log_campaign
        FOREIGN KEY (campaign_id) REFERENCES midterm_campaign (campaign_id) ON DELETE CASCADE,
    CONSTRAINT fk_midterm_update_log_collection
        FOREIGN KEY (school_collection_id) REFERENCES midterm_school_collection (school_collection_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Making-of(스키마 + 운영자 입력만, Public UI 는 Phase 2) ─────────────────
CREATE TABLE midterm_making_of (
    making_of_id         BIGINT       NOT NULL AUTO_INCREMENT,
    campaign_id          BIGINT       NOT NULL,
    school_collection_id BIGINT       NULL,
    stage                VARCHAR(20)  NOT NULL,                  -- SOURCE|ANALYSIS|WORKSHEET|VARIATION|FINAL
    caption              VARCHAR(300) NULL,
    image_key            VARCHAR(500) NULL,
    video_id             BIGINT       NULL,
    published            BIT(1)       NOT NULL DEFAULT b'0',
    sort_order           INT          NOT NULL DEFAULT 0,
    created_at           DATETIME(6),
    updated_at           DATETIME(6),
    created_by           VARCHAR(50),
    updated_by           VARCHAR(50),
    PRIMARY KEY (making_of_id),
    INDEX idx_midterm_making_of_campaign (campaign_id, published, sort_order),
    CONSTRAINT fk_midterm_making_of_campaign
        FOREIGN KEY (campaign_id) REFERENCES midterm_campaign (campaign_id) ON DELETE CASCADE,
    CONSTRAINT fk_midterm_making_of_collection
        FOREIGN KEY (school_collection_id) REFERENCES midterm_school_collection (school_collection_id) ON DELETE CASCADE,
    CONSTRAINT fk_midterm_making_of_video
        FOREIGN KEY (video_id) REFERENCES academy_videos (video_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── After Exam(스키마 + 운영자 입력만, Public UI 는 Phase 2) ────────────────
CREATE TABLE midterm_after_exam (
    after_exam_id        BIGINT NOT NULL AUTO_INCREMENT,
    school_collection_id BIGINT NOT NULL,
    exam_feature         TEXT   NULL,
    prepared_note        TEXT   NULL,
    connection_note      TEXT   NULL,
    next_improvement     TEXT   NULL,
    published            BIT(1) NOT NULL DEFAULT b'0',
    created_at           DATETIME(6),
    updated_at           DATETIME(6),
    created_by           VARCHAR(50),
    updated_by           VARCHAR(50),
    PRIMARY KEY (after_exam_id),
    UNIQUE KEY uk_midterm_after_exam_collection (school_collection_id),
    CONSTRAINT fk_midterm_after_exam_collection
        FOREIGN KEY (school_collection_id) REFERENCES midterm_school_collection (school_collection_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Next Exam CTA(캠페인당 1개) ────────────────────────────────────────────
CREATE TABLE midterm_next_exam (
    next_exam_id     BIGINT       NOT NULL AUTO_INCREMENT,
    campaign_id      BIGINT       NOT NULL,
    enabled          BIT(1)       NOT NULL DEFAULT b'0',
    next_exam_name   VARCHAR(120) NULL,                          -- "2026 기말고사"
    headline         VARCHAR(200) NULL,
    description      VARCHAR(1000) NULL,
    cta_label        VARCHAR(40)  NULL,                          -- "기말고사 준비 상담"
    cta_destination  VARCHAR(300) NULL,
    created_at       DATETIME(6),
    updated_at       DATETIME(6),
    created_by       VARCHAR(50),
    updated_by       VARCHAR(50),
    PRIMARY KEY (next_exam_id),
    UNIQUE KEY uk_midterm_next_exam_campaign (campaign_id),
    CONSTRAINT fk_midterm_next_exam_campaign
        FOREIGN KEY (campaign_id) REFERENCES midterm_campaign (campaign_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 부트스트랩: 에듀홀릭에 빈 비공개 캠페인 1행 ────────────────────────────
-- 운영자가 첫 진입에서 "내신 LIVE 관리" 메뉴를 볼 수 있게 한다(사이드바는 캠페인 행 존재로 판정).
-- published=0 이라 공개 페이지/팝업에는 아무것도 나오지 않는다. handle 이 없으면 아무 행도 안 생긴다.
-- 내용(hero/학교/자료)은 전부 운영자가 관리 화면에서 입력한다 — 여기서 콘텐츠를 하드코딩하지 않는다.
INSERT INTO midterm_campaign
    (academy_id, exam_name, exam_year, semester, exam_type, state, published, popup_enabled,
     created_at, updated_at)
SELECT a.academy_id, '2026 2학기 중간고사', 2026, '2학기', '중간고사', 'PREPARING', b'0', b'0',
       NOW(6), NOW(6)
FROM academies a
WHERE a.handle = 'eduholic'
  AND NOT EXISTS (SELECT 1 FROM midterm_campaign m WHERE m.academy_id = a.academy_id);
