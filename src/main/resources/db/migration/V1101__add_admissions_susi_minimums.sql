-- 입시상담: 2027 수시 수능최저학력기준 — 대학 수시 모집요강의 최저 기준을 조건식(condition)으로 옮긴 것.
-- 형식은 buildit-admissions docs/schema/SUSI_MINIMUM.md(susi_minimum/v1). 조건식은 정시 계산식과 같은 수식 언어이고 참이면 충족.
-- 한 행 = 규칙 하나(rule_kind=RULE) 또는 최저가 없는 전형 하나(rule_kind=NO_MINIMUM, condition_expr 없음).
-- 새 테이블만 추가한다(기존 테이블 변경 없음). 시드(susi_minimums)로 DRAFT 적재 → activate-all로 승격.
CREATE TABLE admissions_susi_minimums (
    minimum_id              BIGINT         NOT NULL AUTO_INCREMENT,
    university_id           BIGINT         NOT NULL,
    academic_year           INT            NOT NULL COMMENT '적용 학년도(예: 2027)',
    rule_key                VARCHAR(150)   NOT NULL COMMENT '시드 rule_key(대학 안에서 고유)',
    rule_kind               VARCHAR(20)    NOT NULL COMMENT 'RULE / NO_MINIMUM',
    label                   VARCHAR(300)   NOT NULL COMMENT '원문 표현',
    type_names              TEXT           NOT NULL COMMENT '적용 전형명 JSON 배열(원문 이름)',
    department_names        TEXT           NULL COMMENT '적용 모집단위명 JSON 배열(비면 전형 전체)',
    unit_scope_text         TEXT           NULL,
    condition_expr          TEXT           NULL COMMENT '조건식(참이면 충족). NO_MINIMUM이면 NULL',
    required_areas_text     TEXT           NULL COMMENT '응시 필수 영역 원문',
    evidence_pages          VARCHAR(200)   NULL,
    raw_text                TEXT           NULL COMMENT '근거 원문 발췌',
    uncertain_json          TEXT           NULL,
    needs_review_reason     TEXT           NULL,
    source_id               BIGINT         NULL,
    source_url              VARCHAR(1000)  NULL,
    content_hash            VARCHAR(64)    NOT NULL,
    status                  VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    created_at              DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)    NULL,
    PRIMARY KEY (minimum_id),
    UNIQUE KEY uk_admissions_susi_minimum (university_id, academic_year, rule_key),
    INDEX idx_admissions_susi_min_univ_year (university_id, academic_year, status),
    CONSTRAINT fk_admissions_susi_min_univ FOREIGN KEY (university_id)
        REFERENCES admissions_universities (university_id),
    CONSTRAINT fk_admissions_susi_min_source FOREIGN KEY (source_id)
        REFERENCES admissions_official_materials (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
