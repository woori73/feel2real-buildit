-- 입시상담: 지난해(2026학년도) 입시결과 — 대학 입학처가 공식 게시한 경쟁률·충원·등록자 성적 지표.
-- 새 테이블만 추가한다(기존 테이블 변경 없음). 시드(buildit-admissions prior_results)로 DRAFT 적재 → activate-all로 승격.
-- 지표는 대학이 쓴 이름 그대로(metric_label) 저장하고, 서로 다른 지표를 합치거나 환산하지 않는다.
-- 기본 행(metric_label = '')은 모집인원·지원자·경쟁률·충원을, 지표 행은 성적 지표 하나를 담는다.
CREATE TABLE admissions_prior_results (
    prior_result_id      BIGINT         NOT NULL AUTO_INCREMENT,
    university_id        BIGINT         NOT NULL,
    academic_year        INT            NOT NULL COMMENT '상담 학년도(승격 단위, 예: 2027)',
    result_year          INT            NOT NULL COMMENT '입시결과가 속한 학년도(예: 2026)',
    round_type           VARCHAR(10)    NOT NULL COMMENT 'SUSI/JEONGSI',
    group_name           VARCHAR(10)    NULL COMMENT '정시 군(가/나/다)',
    type_name            VARCHAR(200)   NOT NULL COMMENT '원문 전형명',
    department_name      VARCHAR(200)   NOT NULL COMMENT '원문 모집단위명',
    metric_label         VARCHAR(200)   NOT NULL DEFAULT '' COMMENT '원문 지표명(기본 행은 빈 문자열)',
    metric_unit          VARCHAR(20)    NULL COMMENT 'GRADE/PERCENTILE/SCORE/OTHER',
    metric_value         DECIMAL(12,4)  NULL,
    raw_value_text       VARCHAR(200)   NULL COMMENT '원문 셀 문자열',
    recruit_count        INT            NULL,
    applicant_count      INT            NULL,
    competition_rate     DECIMAL(8,2)   NULL,
    extra_admitted_count INT            NULL,
    linked_type_name     VARCHAR(200)   NULL COMMENT '같은 이름의 올해(2027) 전형이 있으면 그 이름',
    linked_department_name VARCHAR(200) NULL COMMENT '같은 이름의 올해(2027) 모집단위가 있으면 그 이름',
    natural_key          VARCHAR(64)    NOT NULL COMMENT 'sha256(결과학년도|트랙|군|전형|모집단위|지표명)',
    source_id            BIGINT         NULL,
    source_url           VARCHAR(1000)  NULL,
    institution_name     VARCHAR(200)   NULL,
    published_at         DATE           NULL,
    evidence_pages       VARCHAR(100)   NULL,
    status               VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    created_at           DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)    NULL,
    PRIMARY KEY (prior_result_id),
    UNIQUE KEY uk_admissions_prior_natural (university_id, natural_key),
    INDEX idx_admissions_prior_univ_year (university_id, academic_year, status),
    CONSTRAINT fk_admissions_prior_univ FOREIGN KEY (university_id)
        REFERENCES admissions_universities (university_id),
    CONSTRAINT fk_admissions_prior_source FOREIGN KEY (source_id)
        REFERENCES admissions_official_materials (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
