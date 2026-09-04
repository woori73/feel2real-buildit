-- 공개 BuildIt Wiki(/wiki) — "모의고사 학습 자료" (회원 업로드 자료) + /wiki 방문 집계.
-- wiki_analysis_materials(ADMIN 공식 분석자료)와 목적/권한이 달라 별도 테이블이다.
--   · learning: 일반 회원이 올리는 어휘/Workbook/변형문제/기타. 다운로드는 회원 전용.
--   · analysis: ADMIN 만 등록. 다운로드 공개.
-- 신규 테이블만 추가(기존 row 무관) → 1단계 마이그레이션. 다음 마이그레이션 = V961.

CREATE TABLE wiki_learning_materials (
    wiki_learning_material_id BIGINT       NOT NULL AUTO_INCREMENT,
    data_type                 VARCHAR(20)  NOT NULL,       -- VOCAB|WORKBOOK|VARIANT|WEEKLY|ETC (어휘/Workbook/변형문제/WEEKLY/기타)
    grade                     INT          NOT NULL,       -- 1|2|3 (고1/고2/고3) — 업로드 시점 Wiki 학년 탭에서 자동
    title                     VARCHAR(200) NOT NULL,       -- 파일명에서 자동 생성, 사용자가 수정 가능
    content_type              VARCHAR(120) NOT NULL,
    original_file_name        VARCHAR(255) NOT NULL,
    storage_key               VARCHAR(500) NOT NULL,
    file_size                 BIGINT       NOT NULL,
    uploader_user_id          BIGINT       NOT NULL,        -- 서버 authenticated principal 로만 기록
    uploader_name             VARCHAR(60)  NOT NULL,        -- 표시용 스냅샷(가입 후 닉네임 변경돼도 당시 이름 유지)
    is_deleted                BIT(1)       NOT NULL DEFAULT b'0',
    created_at                DATETIME(6),
    updated_at                DATETIME(6),
    created_by                VARCHAR(50),
    updated_by                VARCHAR(50),
    PRIMARY KEY (wiki_learning_material_id),
    INDEX idx_wiki_learning_materials_public
        (is_deleted, grade, created_at, wiki_learning_material_id),
    INDEX idx_wiki_learning_materials_uploader (uploader_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- /wiki 방문 집계 — 날짜당 1행. academy_daily_visits 와 동일 패턴(native upsert + HttpSession 일 단위 중복방지).
-- 개인정보(IP 원문 등) 저장하지 않는다. "오늘 몇 명 / 늘고 있나" 운영 지표 전용.
CREATE TABLE wiki_daily_visits (
    wiki_daily_visit_id BIGINT NOT NULL AUTO_INCREMENT,
    visit_date          DATE   NOT NULL,
    visit_count         BIGINT NOT NULL,
    created_at          DATETIME(6),
    updated_at          DATETIME(6),
    PRIMARY KEY (wiki_daily_visit_id),
    UNIQUE KEY uk_wiki_daily_visits_date (visit_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
