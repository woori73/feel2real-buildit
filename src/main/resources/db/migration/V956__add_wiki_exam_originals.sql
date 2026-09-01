-- 공개 BuildIt Wiki(/wiki) — 모의고사 원본(문제지/정답지) PDF 저장소.
-- /analyzes(exam_analyses)는 HTML 분석 리포트 렌더용이라 PDF 원본과 맞지 않아 별도 테이블로 분리.
-- 신규 테이블만 추가(기존 row 무관) → 1단계 마이그레이션.
CREATE TABLE wiki_exam_originals (
    wiki_exam_original_id BIGINT       NOT NULL AUTO_INCREMENT,
    title                 VARCHAR(200) NOT NULL,
    grade                 INT          NOT NULL,           -- 1|2|3 (고1/고2/고3)
    exam_label            VARCHAR(100) NOT NULL,           -- 예: "2026 9월 모의고사"
    doc_type              VARCHAR(20)  NOT NULL,           -- QUESTION | ANSWER (enum name)
    original_file_name    VARCHAR(255) NOT NULL,
    storage_key           VARCHAR(500) NOT NULL,
    file_size             BIGINT       NOT NULL,
    is_visible            BIT(1)       NOT NULL,
    is_deleted            BIT(1)       NOT NULL DEFAULT b'0',
    created_at            DATETIME(6),
    updated_at            DATETIME(6),
    created_by            VARCHAR(50),
    updated_by            VARCHAR(50),
    PRIMARY KEY (wiki_exam_original_id),
    INDEX idx_wiki_exam_originals_public
        (is_deleted, is_visible, grade, created_at, wiki_exam_original_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
