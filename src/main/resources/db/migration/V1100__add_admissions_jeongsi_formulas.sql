-- 입시상담: 2027 정시 수능 환산 계산식 — 대학 정시 모집요강의 수능 반영 방법(반영비율·활용지표·등급표·가산점)을
-- 계산식(expression)과 등급표(tables_json)로 옮긴 것. 형식은 buildit-admissions docs/schema/JEONGSI_FORMULA.md.
-- 새 테이블만 추가한다(기존 테이블 변경 없음). 시드(jeongsi_formulas)로 DRAFT 적재 → activate-all로 승격.
-- needs_review_reason이 있으면 원문에 없는 환산 방법을 정했거나 해석에 따라 점수가 크게 달라지는 계산식이라
-- 대학 확인 전에는 점수를 안내하지 않는다.
CREATE TABLE admissions_jeongsi_formulas (
    formula_id              BIGINT         NOT NULL AUTO_INCREMENT,
    university_id           BIGINT         NOT NULL,
    academic_year           INT            NOT NULL COMMENT '적용 학년도(예: 2027)',
    formula_key             VARCHAR(100)   NOT NULL COMMENT '시드 formula_key(대학 안에서 고유)',
    label                   VARCHAR(300)   NOT NULL COMMENT '원문 계열·유형 이름',
    type_names              TEXT           NOT NULL COMMENT '적용 전형명 JSON 배열(원문 이름)',
    department_names        TEXT           NULL COMMENT '적용 모집단위명 JSON 배열(비면 전형 전체)',
    unit_scope_text         TEXT           NULL COMMENT '원문의 해당 모집단위 문구',
    csat_points             DECIMAL(10,3)  NULL COMMENT '원문 수능 만점',
    perfect_score_expected  DECIMAL(10,3)  NULL,
    expression              TEXT           NOT NULL COMMENT '계산식(jeongsi_formula/v1 수식 언어)',
    tables_json             TEXT           NULL COMMENT '등급표 JSON',
    rounding_text           VARCHAR(500)   NULL,
    eligibility_text        TEXT           NULL,
    not_in_csat_score_text  TEXT           NULL,
    evidence_pages          VARCHAR(200)   NULL,
    raw_text                TEXT           NULL COMMENT '근거 원문 발췌',
    uncertain_json          TEXT           NULL COMMENT '해석이 들어간 부분 JSON 배열',
    needs_review_reason     TEXT           NULL,
    source_id               BIGINT         NULL,
    source_url              VARCHAR(1000)  NULL,
    content_hash            VARCHAR(64)    NOT NULL COMMENT '내용 비교용 sha256',
    status                  VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    created_at              DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)    NULL,
    PRIMARY KEY (formula_id),
    UNIQUE KEY uk_admissions_jeongsi_formula (university_id, academic_year, formula_key),
    INDEX idx_admissions_jeongsi_univ_year (university_id, academic_year, status),
    CONSTRAINT fk_admissions_jeongsi_univ FOREIGN KEY (university_id)
        REFERENCES admissions_universities (university_id),
    CONSTRAINT fk_admissions_jeongsi_source FOREIGN KEY (source_id)
        REFERENCES admissions_official_materials (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
