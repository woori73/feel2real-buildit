SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 내신 LIVE FRAME 의 (학교, 학년) scene 에 기존 academy_videos 를 연결한다(Phase 4c).
-- 다음 마이그레이션 = V971. 이전 = V969.
--
-- 설계 원칙 (V969 midterm_wiki_source_exposures 와 같은 "참조형" 계층):
--   * 영상 binary 를 이 스키마로 복제하지 않는다 — academy_videos.video_id 를 참조만 한다.
--   * campaign 스코프. seasonal campaign context 라 AcademyVideo 자체에 학교/학년을 박지 않는다.
--   * 한 영상을 여러 (학교, 학년, 역할) 에 연결할 수 있고, 같은 slot 에 복수 영상 + sort_order.
--   * generated column 없음 → V969(errno 3104: stored generated column base column 의 CASCADE FK)
--     문제와 무관하다. 단순 FK / UNIQUE / INDEX / CHECK 만 사용한다.
--   * academy_video_id FK 는 ON DELETE CASCADE — 이 행은 순수 link 라 영상이 사라지면 의미가
--     없다(NOT NULL). 영상 tombstone(DELETING) 구간에는 public resolver 가 lifecycle_status
--     != READY 를 제외한다.
CREATE TABLE midterm_live_video_links (
    link_id           BIGINT       NOT NULL AUTO_INCREMENT,
    campaign_id       BIGINT       NOT NULL,
    academy_video_id  BIGINT       NOT NULL,
    school_name       VARCHAR(100) NOT NULL,                 -- Wiki source / FRAME 과 동일 canonical ("신구중")
    grade             INT          NOT NULL,                 -- 1|2|3 (FRAME grade 와 exact match)
    role              VARCHAR(20)  NOT NULL,                 -- STUDENT_STUDY | LESSON_INTERACTION
    visible           BIT(1)       NOT NULL DEFAULT b'0',
    sort_order        INT          NOT NULL DEFAULT 0,
    created_at        DATETIME(6),
    updated_at        DATETIME(6),
    created_by        VARCHAR(50),
    updated_by        VARCHAR(50),
    PRIMARY KEY (link_id),
    UNIQUE KEY uk_mlvl_slot_video (campaign_id, school_name, grade, role, academy_video_id),
    KEY idx_mlvl_public (campaign_id, visible, sort_order, link_id),
    KEY idx_mlvl_video (academy_video_id),
    CONSTRAINT fk_mlvl_campaign
        FOREIGN KEY (campaign_id) REFERENCES midterm_campaign (campaign_id) ON DELETE CASCADE,
    CONSTRAINT fk_mlvl_video
        FOREIGN KEY (academy_video_id) REFERENCES academy_videos (video_id) ON DELETE CASCADE,
    CONSTRAINT ck_mlvl_role
        CHECK (role IN ('STUDENT_STUDY', 'LESSON_INTERACTION')),
    CONSTRAINT ck_mlvl_grade
        CHECK (grade BETWEEN 1 AND 9)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
