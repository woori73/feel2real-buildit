-- 입시상담 Agent (입시상담 가이드) 전용 테이블
-- NewStart 운영 DB와 완전 분리. admissions_* 테이블은 입시상담 전용.
-- 지피지기의 newstart_knowledge_items 패턴을 입시 도메인으로 적용.
-- 승인 상태: DRAFT → REVIEW → ACTIVE. ACTIVE만 RAG 검색 대상.
-- APPROVED_CANDIDATE를 ACTIVE로 임의 변경 금지 (관리자 승인 API를 통해서만 전환).

-- 1. 대학
CREATE TABLE admissions_universities (
    university_id       BIGINT        NOT NULL AUTO_INCREMENT,
    name                VARCHAR(100)  NOT NULL,
    region              VARCHAR(50),
    type                VARCHAR(20),
    homepage_url        VARCHAR(500),
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (university_id),
    UNIQUE KEY uk_admissions_univ_name (name),
    INDEX idx_admissions_univ_region (region)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 공식자료 (모집요강 원문 등)
CREATE TABLE admissions_official_materials (
    material_id         BIGINT        NOT NULL AUTO_INCREMENT,
    university_id       BIGINT,
    academic_year       INT           NOT NULL,
    title               VARCHAR(300)  NOT NULL,
    issuer              VARCHAR(200)  NOT NULL,
    publish_date        DATE,
    document_url        VARCHAR(1000),
    file_key            VARCHAR(500),
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (material_id),
    INDEX idx_admissions_mat_univ_year (university_id, academic_year),
    INDEX idx_admissions_mat_year (academic_year),
    CONSTRAINT fk_admissions_mat_univ FOREIGN KEY (university_id)
        REFERENCES admissions_universities (university_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 모집단위
CREATE TABLE admissions_departments (
    department_id       BIGINT        NOT NULL AUTO_INCREMENT,
    university_id       BIGINT        NOT NULL,
    academic_year       INT           NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    category            VARCHAR(50),
    quota_info          TEXT,
    source_id           BIGINT,
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (department_id),
    INDEX idx_admissions_dept_univ_year (university_id, academic_year),
    INDEX idx_admissions_dept_name (name),
    CONSTRAINT fk_admissions_dept_univ FOREIGN KEY (university_id)
        REFERENCES admissions_universities (university_id),
    CONSTRAINT fk_admissions_dept_source FOREIGN KEY (source_id)
        REFERENCES admissions_official_materials (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 전형
CREATE TABLE admissions_types (
    admission_type_id   BIGINT        NOT NULL AUTO_INCREMENT,
    university_id       BIGINT        NOT NULL,
    academic_year       INT           NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    category            VARCHAR(50),
    details             TEXT,
    source_id           BIGINT,
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (admission_type_id),
    INDEX idx_admissions_type_univ_year (university_id, academic_year),
    INDEX idx_admissions_type_name (name),
    CONSTRAINT fk_admissions_type_univ FOREIGN KEY (university_id)
        REFERENCES admissions_universities (university_id),
    CONSTRAINT fk_admissions_type_source FOREIGN KEY (source_id)
        REFERENCES admissions_official_materials (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 일정
CREATE TABLE admissions_schedules (
    schedule_id         BIGINT        NOT NULL AUTO_INCREMENT,
    university_id       BIGINT,
    academic_year       INT           NOT NULL,
    title               VARCHAR(200)  NOT NULL,
    start_date          DATE,
    end_date            DATE,
    note                TEXT,
    source_id           BIGINT,
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (schedule_id),
    INDEX idx_admissions_sched_univ_year (university_id, academic_year),
    INDEX idx_admissions_sched_dates (start_date, end_date),
    CONSTRAINT fk_admissions_sched_univ FOREIGN KEY (university_id)
        REFERENCES admissions_universities (university_id),
    CONSTRAINT fk_admissions_sched_source FOREIGN KEY (source_id)
        REFERENCES admissions_official_materials (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 입시상담 지식베이스 (핵심 RAG 원천)
CREATE TABLE admissions_knowledge_items (
    knowledge_id        BIGINT        NOT NULL AUTO_INCREMENT,
    academic_year       INT           NOT NULL,
    year_confirmed      BOOLEAN       NOT NULL DEFAULT FALSE,
    title               VARCHAR(300)  NOT NULL,
    content             LONGTEXT      NOT NULL,
    category            VARCHAR(50),
    tags                TEXT,
    source_class        VARCHAR(20)   NOT NULL,
    source_id           BIGINT,
    source_url          VARCHAR(1000),
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    role                VARCHAR(10)   NOT NULL DEFAULT 'FACT',
    verification_note   TEXT,
    dedup_key           VARCHAR(191),
    version             INT           NOT NULL DEFAULT 1,
    created_by          VARCHAR(50),
    updated_by          VARCHAR(50),
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (knowledge_id),
    UNIQUE KEY uk_admissions_knowledge_dedup (dedup_key),
    INDEX idx_admissions_knowledge_status (status, source_class),
    INDEX idx_admissions_knowledge_year (academic_year, year_confirmed),
    INDEX idx_admissions_knowledge_category (category),
    CONSTRAINT fk_admissions_knowledge_source FOREIGN KEY (source_id)
        REFERENCES admissions_official_materials (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 임베딩 (768차원, EmbeddingGemma 2)
CREATE TABLE admissions_knowledge_embeddings (
    embedding_id        BIGINT        NOT NULL AUTO_INCREMENT,
    knowledge_id        BIGINT        NOT NULL,
    model_version       VARCHAR(50)   NOT NULL,
    dimension           INT           NOT NULL DEFAULT 768,
    vector              LONGBLOB      NOT NULL,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (embedding_id),
    UNIQUE KEY uk_admissions_embedding (knowledge_id, model_version),
    INDEX idx_admissions_embedding_knowledge (knowledge_id),
    CONSTRAINT fk_admissions_embedding_knowledge FOREIGN KEY (knowledge_id)
        REFERENCES admissions_knowledge_items (knowledge_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. 충돌대장
CREATE TABLE admissions_conflicts (
    conflict_id         BIGINT        NOT NULL AUTO_INCREMENT,
    academic_year       INT           NOT NULL,
    title               VARCHAR(300)  NOT NULL,
    description         TEXT          NOT NULL,
    related_knowledge_ids VARCHAR(500),
    status              VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    resolution_note     TEXT,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at         DATETIME(6),
    PRIMARY KEY (conflict_id),
    INDEX idx_admissions_conflict_status (status),
    INDEX idx_admissions_conflict_year (academic_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. 유튜브 분석
CREATE TABLE admissions_youtube_analyses (
    analysis_id         BIGINT        NOT NULL AUTO_INCREMENT,
    video_title         VARCHAR(300),
    video_url           VARCHAR(1000) NOT NULL,
    channel             VARCHAR(200),
    analysis_summary    TEXT,
    related_knowledge_id BIGINT,
    reliability         VARCHAR(20),
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (analysis_id),
    INDEX idx_admissions_yt_knowledge (related_knowledge_id),
    CONSTRAINT fk_admissions_yt_knowledge FOREIGN KEY (related_knowledge_id)
        REFERENCES admissions_knowledge_items (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. 상담 이력 (비회원 세션 토큰 기반, 개인정보 저장 금지)
CREATE TABLE admissions_counseling_sessions (
    session_id          BIGINT        NOT NULL AUTO_INCREMENT,
    session_token       VARCHAR(64)   NOT NULL,
    question            TEXT          NOT NULL,
    question_fingerprint VARCHAR(64)  NOT NULL,
    answer              LONGTEXT,
    evidence_summary    TEXT,
    student_profile     TEXT,
    academic_year       INT,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (session_id),
    INDEX idx_admissions_session_token (session_token),
    INDEX idx_admissions_session_fingerprint (question_fingerprint),
    INDEX idx_admissions_session_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
