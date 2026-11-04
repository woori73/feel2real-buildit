-- 입시상담: 정시 환산에 필요한 수능 발표 값. 형식은 buildit-admissions docs/schema/JEONGSI_STATS.md.
-- admissions_csat_std_max: 한국교육과정평가원 수능 채점 결과의 영역·탐구 과목별 표준점수 최고점(계산식 *_STD_MAX).
-- admissions_conversion_scores: 대학이 발표한 탐구 변환표준점수표(백분위 0~100 → 점수, 계산식 INQ*_CONV / INQ*_CONV_MAX).
-- 새 테이블만 추가한다(기존 테이블 변경 없음). 시드(csat_std_max / conversion_scores)로 DRAFT 적재 → activate-all로 승격.
CREATE TABLE admissions_csat_std_max (
    std_max_id              BIGINT         NOT NULL AUTO_INCREMENT,
    academic_year           INT            NOT NULL COMMENT '적용 학년도(예: 2027)',
    subject_key             VARCHAR(100)   NOT NULL COMMENT 'KOR / MATH / INQ:{원문 과목명}',
    area                    VARCHAR(10)    NOT NULL COMMENT 'KOR / MATH / INQ',
    subject_name            VARCHAR(100)   NULL COMMENT '탐구 과목명(원문 표기)',
    track                   VARCHAR(10)    NULL COMMENT '탐구 계열 SOC / SCI / VOC',
    std_max                 DECIMAL(6,2)   NOT NULL COMMENT '표준점수 최고점',
    evidence_pages          VARCHAR(200)   NULL,
    source_id               BIGINT         NULL,
    source_url              VARCHAR(1000)  NULL,
    content_hash            VARCHAR(64)    NOT NULL,
    status                  VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    created_at              DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)    NULL,
    PRIMARY KEY (std_max_id),
    UNIQUE KEY uk_admissions_csat_std_max (academic_year, subject_key),
    CONSTRAINT fk_admissions_csat_std_max_source FOREIGN KEY (source_id)
        REFERENCES admissions_official_materials (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admissions_conversion_scores (
    conversion_id           BIGINT         NOT NULL AUTO_INCREMENT,
    university_id           BIGINT         NOT NULL,
    academic_year           INT            NOT NULL COMMENT '적용 학년도(예: 2027)',
    track                   VARCHAR(10)    NOT NULL COMMENT '탐구 계열 SOC / SCI / VOC',
    label                   VARCHAR(100)   NOT NULL COMMENT '원문 표 이름',
    scores_json             TEXT           NOT NULL COMMENT '백분위("0"~"100") → 변환표준점수 JSON 객체',
    note                    TEXT           NULL,
    evidence_pages          VARCHAR(200)   NULL,
    source_id               BIGINT         NULL,
    source_url              VARCHAR(1000)  NULL,
    content_hash            VARCHAR(64)    NOT NULL,
    status                  VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    created_at              DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)    NULL,
    PRIMARY KEY (conversion_id),
    UNIQUE KEY uk_admissions_conversion_score (university_id, academic_year, track),
    CONSTRAINT fk_admissions_conversion_univ FOREIGN KEY (university_id)
        REFERENCES admissions_universities (university_id),
    CONSTRAINT fk_admissions_conversion_source FOREIGN KEY (source_id)
        REFERENCES admissions_official_materials (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
