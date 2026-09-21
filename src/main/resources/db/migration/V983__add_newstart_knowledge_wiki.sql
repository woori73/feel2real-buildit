-- NewStart Knowledge Wiki — ADMIN ONLY 지식 베이스
-- NewStart AI가 참고할 공식 지식/규칙/사례/전자책/감사원자료/카페자료 등을 운영자가 관리하는 베이스
-- ADMIN ONLY: /api/admin/newstart/wiki/** → Spring Security hasRole('ADMIN') + SSR /admin/newstart/wiki/**

CREATE TABLE newstart_knowledge_items (
    knowledge_id          BIGINT        NOT NULL AUTO_INCREMENT,
    source_type           VARCHAR(20)   NOT NULL,   -- OFFICIAL|AUDIT|EBOOK|CAFE|CASE|RULE
    category              VARCHAR(50)   NOT NULL,   -- 대분류 (예: 감면율, 자격요건, 서류준비, 상환계획, 감면교육, 기타)
    title                 VARCHAR(200)  NOT NULL,
    summary               TEXT,                     -- AI가 읽기 좋은 2~3줄 요약
    content               LONGTEXT,                 -- 전체 정리된 원문 (마크다운 허용)
    authority             VARCHAR(50),              -- 발행/작성 주체 (예: 캠코, 새출발기금, 금감원, 감사원, 전자책, 카페매니저, 커뮤니티회원)
    tags                  TEXT,                     -- 쉼표 구분 태그 (예: 감면율, 70세이상, 4인가구, 지역공제)
    source_url            VARCHAR(500),             -- 원문 링크 (카페 게시글 URL, 전자책 페이지, 공식 공문 링크 등)
    source_date           DATE,                     -- 원문 작성/발행일
    effective_from        DATE,                     -- 이 지식의 적용 시작일 (정책 시행일 등)
    effective_to          DATE,                     -- 이 지식의 적용 종료일 (폐지일 등)
    version               INT          NOT NULL DEFAULT 1,   -- 지식 버전 (수정 시 증가)
    status                VARCHAR(20)   NOT NULL DEFAULT 'DRAFT', -- DRAFT|REVIEW|ACTIVE|ARCHIVED
    confidence            VARCHAR(20),              -- HIGH|MEDIUM|LOW (AI retrieval 시 가중치)
    related_case_ids      TEXT,                     -- 연관 사례 knowledge_id (쉼표 구분)
    related_rule_ids      TEXT,                     -- 연관 규칙 knowledge_id (쉼표 구분)
    created_by            VARCHAR(50),
    updated_by            VARCHAR(50),
    created_at            DATETIME(6),
    updated_at            DATETIME(6),
    PRIMARY KEY (knowledge_id),
    INDEX idx_newstart_knowledge_status_type (status, source_type),
    INDEX idx_newstart_knowledge_category (category),
    INDEX idx_newstart_knowledge_effective (effective_from, effective_to),
    INDEX idx_newstart_knowledge_source_date (source_date),
    INDEX idx_newstart_knowledge_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 감면율 게시판 사례 전용 테이블 (기존 수집 데이터와 연계)
CREATE TABLE newstart_empirical_cases (
    case_id               BIGINT        NOT NULL AUTO_INCREMENT,
    article_id            VARCHAR(20)   NOT NULL,          -- 카페 게시글 번호 (예: 5700)
    board_name            VARCHAR(50)   NOT NULL DEFAULT '감면율',
    title                 VARCHAR(200)  NOT NULL,
    author_nickname       VARCHAR(60),                     -- 작성자 닉네임
    author_grade          VARCHAR(30),                     -- 작성자 등급 (감사멤버 등)
    written_at            DATETIME,                        -- 게시글 작성일시
    view_count            INT,
    -- 채무 정보
    debt_total            BIGINT,                          -- 총 채무액 (원 단위)
    debt_breakdown        JSON,                            -- 채무 세부 내역
    debt_secured          BIGINT,                          -- 담보 채무
    debt_unsecured        BIGINT,                          -- 무담보 채무
    debt_acquired_by_fund BIGINT,                          -- 새출발기금 매입 채무
    -- 자산 정보
    asset_property        BIGINT,                          -- 부동산 평가액
    asset_vehicle         BIGINT,                          -- 차량 시세
    asset_financial       BIGINT,                          -- 금융 자산
    -- 가구/소득/개인 정보
    household_type        VARCHAR(30),                     -- 1인가구|2인가구|3인가구|4인가구|5인가구|6인가구
    dependents            INT,                             -- 부양가족 수 (미성년자녀 수)
    age                   INT,                             -- 연령
    income_2025           BIGINT,                          -- 2025년 소득금액증명원 상 소득 (원)
    income_current        BIGINT,                          -- 현재 월소득/연소득 (원)
    employment_status     VARCHAR(30),                     -- EMPLOYED|UNEMPLOYED|SELF_EMPLOYED
    business_status       VARCHAR(30),                     -- OPERATING|CLOSED
    application_type      VARCHAR(30),                     -- INSOLVENCY|INSOLVENCY_RISK
    -- 감면율/확정금액 정보
    predicted_reduction_rate   VARCHAR(30),               -- 매니저/커뮤니티 예상 감면율 (예: 75~80%)
    final_reduction_rate       VARCHAR(30),               -- 작성자 후속 댓글로 확인된 실제 감면율 (예: 75%)
    temp_reduction_rate        VARCHAR(30),               -- 임시안 감면율
    predicted_repayment_amount BIGINT,                    -- 예상 상환금액 (원)
    final_repayment_amount     BIGINT,                    -- 확정 상환금액 (원)
    repayment_period_months    INT,                       -- 상환 개월 수
    grace_period_months        INT,                       -- 거치 개월 수
    monthly_payment            BIGINT,                    -- 월 상환액 (원)
    -- 원문 및 메타
    content_raw            LONGTEXT,                       -- 게시글 원문
    manager_prediction     JSON,                          -- 매니저 예측 전체 내용
    author_followup_result TEXT,                          -- 작성자 후속 결과 댓글
    prediction_vs_actual   JSON,                          -- 예측 vs 실제 비교
    special_circumstances  JSON,                          -- 특수 상황 (장애, 암, 주거급여, 공동명의 등)
    key_insights           JSON,                          -- 핵심 포인트 배열
    source_url             VARCHAR(500),                   -- 원문 URL
    collected_at           DATETIME(6),
    created_by             VARCHAR(50),
    updated_by             VARCHAR(50),
    created_at             DATETIME(6),
    updated_at             DATETIME(6),
    PRIMARY KEY (case_id),
    UNIQUE KEY uk_newstart_empirical_cases_article (article_id),
    INDEX idx_newstart_empirical_reduction (final_reduction_rate),
    INDEX idx_newstart_empirical_debt (debt_total),
    INDEX idx_newstart_empirical_income (income_2025),
    INDEX idx_newstart_empirical_age (age),
    INDEX idx_newstart_empirical_household (household_type),
    INDEX idx_newstart_empirical_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 지식 아이템과 사례/규칙 간 연결 (Many-to-Many)
CREATE TABLE newstart_knowledge_relations (
    relation_id           BIGINT        NOT NULL AUTO_INCREMENT,
    from_knowledge_id     BIGINT        NOT NULL,
    to_knowledge_id       BIGINT        NOT NULL,
    relation_type         VARCHAR(30)   NOT NULL,   -- CASE|RULE|PREREQUISITE|SIMILAR|CONTRADICTS
    created_by            VARCHAR(50),
    created_at            DATETIME(6),
    PRIMARY KEY (relation_id),
    UNIQUE KEY uk_newstart_knowledge_relation (from_knowledge_id, to_knowledge_id, relation_type),
    INDEX idx_newstart_knowledge_relations_from (from_knowledge_id),
    INDEX idx_newstart_knowledge_relations_to (to_knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 지식 상태 변경 이력 (감사용)
CREATE TABLE newstart_knowledge_status_log (
    log_id                BIGINT        NOT NULL AUTO_INCREMENT,
    knowledge_id          BIGINT        NOT NULL,
    from_status           VARCHAR(20),
    to_status             VARCHAR(20)   NOT NULL,
    changed_by            VARCHAR(50)   NOT NULL,
    change_reason         TEXT,
    created_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (log_id),
    INDEX idx_newstart_knowledge_status_log_knowledge (knowledge_id),
    INDEX idx_newstart_knowledge_status_log_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;