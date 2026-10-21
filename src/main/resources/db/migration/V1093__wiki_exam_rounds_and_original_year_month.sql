-- 모의고사 위키 회차(연도·월) 구조화.
-- 목적: 매월 코드 수정 없이 관리자 화면에서 회차(메인/아카이브)와 해설 자료를 운영한다.
-- 1) wiki_exam_originals 에 exam_year/exam_month 를 추가하고 기존 exam_label 에서 값을 백필한다.
--    기존 행은 삭제·변경하지 않으며(열 추가 + 신규 열 채움만), 다운로드·공개 정책은 그대로 유지된다.
-- 2) wiki_exam_rounds 신규 테이블: 회차 메타(제목·부제·배너·메인 지정·공개 여부).
-- 3) 2026년 9월(아카이브)·10월(메인) 회차를 시드한다.

ALTER TABLE wiki_exam_originals
    ADD COLUMN exam_year  INT NULL AFTER grade,
    ADD COLUMN exam_month INT NULL AFTER exam_year,
    ADD INDEX idx_wiki_exam_originals_round (exam_year, exam_month, is_deleted, is_visible);

-- 백필: 연도는 라벨의 4자리 연도, 월은 "N월" 표기에서 추출한다.
-- 두 자리 월(10·11·12)을 한 자리 월보다 먼저 검사해 부분 일치를 막는다.
-- 연도를 특정할 수 없는 기존 행은 위키가 시작된 2026년으로 둔다.
UPDATE wiki_exam_originals
SET exam_year = CASE
        WHEN exam_label LIKE '%2025%' THEN 2025
        WHEN exam_label LIKE '%2027%' THEN 2027
        ELSE 2026
    END,
    exam_month = CASE
        WHEN exam_label LIKE '%12월%' THEN 12
        WHEN exam_label LIKE '%11월%' THEN 11
        WHEN exam_label LIKE '%10월%' THEN 10
        WHEN exam_label LIKE '%1월%' THEN 1
        WHEN exam_label LIKE '%2월%' THEN 2
        WHEN exam_label LIKE '%3월%' THEN 3
        WHEN exam_label LIKE '%4월%' THEN 4
        WHEN exam_label LIKE '%5월%' THEN 5
        WHEN exam_label LIKE '%6월%' THEN 6
        WHEN exam_label LIKE '%7월%' THEN 7
        WHEN exam_label LIKE '%8월%' THEN 8
        WHEN exam_label LIKE '%9월%' THEN 9
        ELSE NULL
    END;

CREATE TABLE wiki_exam_rounds (
    wiki_exam_round_id BIGINT       NOT NULL AUTO_INCREMENT,
    exam_year          INT          NOT NULL,
    exam_month         INT          NOT NULL,
    title              VARCHAR(200) NOT NULL,
    subtitle           VARCHAR(300) NULL,
    banner_path        VARCHAR(500) NULL,
    is_current         BIT(1)       NOT NULL DEFAULT b'0',
    is_published       BIT(1)       NOT NULL DEFAULT b'1',
    is_deleted         BIT(1)       NOT NULL DEFAULT b'0',
    created_at         DATETIME(6),
    updated_at         DATETIME(6),
    created_by         VARCHAR(50),
    updated_by         VARCHAR(50),
    PRIMARY KEY (wiki_exam_round_id),
    UNIQUE KEY uk_wiki_exam_rounds_ym (exam_year, exam_month),
    INDEX idx_wiki_exam_rounds_public (is_deleted, is_published, exam_year, exam_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO wiki_exam_rounds
    (exam_year, exam_month, title, subtitle, banner_path, is_current, is_published, is_deleted, created_at, updated_at)
VALUES
    (2026, 9, '2026년 9월 모의고사', '고1·고2·고3 영어 모의고사 문제지·정답지·해설 자료', NULL, b'0', b'1', b'0', NOW(), NOW()),
    (2026, 10, '2026년 10월 모의고사 해설 자료', '고1 · 고2 · 고3 영어 모의고사 문제풀이 · 구문독해 · 핵심 정리', '/images/wiki/banner-2026-10.png', b'1', b'1', b'0', NOW(), NOW());
