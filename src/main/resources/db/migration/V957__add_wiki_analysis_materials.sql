-- 공개 BuildIt Wiki(/wiki) — 분석 자료 파일 자료실.
-- exam_analyses(/analyzes, HTML 분석 콘텐츠)와 목적·파일 성격이 달라 절대 재사용하지 않는다.
-- 실제 업로드 문서(PDF/HWP/HWPX/DOCX 등)를 원본 그대로 저장하고 다운로드로만 제공한다.
-- 신규 테이블만 추가(기존 row 무관) → 1단계 마이그레이션.
CREATE TABLE wiki_analysis_materials (
    wiki_analysis_material_id BIGINT       NOT NULL AUTO_INCREMENT,
    title                     VARCHAR(200) NOT NULL,
    description               VARCHAR(1000) NULL,
    school_name               VARCHAR(100) NULL,           -- "전국" 등 자유 입력
    grade                     INT          NOT NULL,       -- 1|2|3 (고1/고2/고3)
    exam_year                 INT          NOT NULL,
    semester                  VARCHAR(20)  NOT NULL,
    exam_type                 VARCHAR(40)  NOT NULL,
    subject                   VARCHAR(60)  NULL,
    content_type              VARCHAR(100) NOT NULL,
    original_file_name        VARCHAR(255) NOT NULL,
    storage_key               VARCHAR(500) NOT NULL,
    file_size                 BIGINT       NOT NULL,
    is_visible                BIT(1)       NOT NULL,
    is_deleted                BIT(1)       NOT NULL DEFAULT b'0',
    created_at                DATETIME(6),
    updated_at                DATETIME(6),
    created_by                VARCHAR(50),
    updated_by                VARCHAR(50),
    PRIMARY KEY (wiki_analysis_material_id),
    INDEX idx_wiki_analysis_materials_public
        (is_deleted, is_visible, grade, created_at, wiki_analysis_material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
