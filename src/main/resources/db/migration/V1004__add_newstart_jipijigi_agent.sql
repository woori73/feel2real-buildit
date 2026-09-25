-- 지피지기 Agent V1 — 운영 보조 Agent (수동 명령 → 조사 → 초안 → 운영자 승인 워크플로)
-- NAVER 실제 게시는 V1 범위 밖: 게시 버튼 비활성, 상태는 APPROVED에서 정지.
-- ADMIN ONLY: /api/admin/newstart/jipijigi/** 및 SSR /admin/newstart/jipijigi (SecurityConfig의 기존 catch-all 규칙 준수)

-- 게시 대상 게시판 서버측 allowlist. 이 테이블의 enabled 행 외에는 게시가 원천 차단된다.
-- menu_id는 NAVER에서 운영자가 실제 값을 확인해 입력하기 전까지 NULL → 게시 준비 불가.
CREATE TABLE newstart_jipijigi_target_boards (
    board_row_id   BIGINT       NOT NULL AUTO_INCREMENT,
    club_id        BIGINT       NOT NULL,                 -- 네이버 카페 clubid (새출발기금 부실차주 라이브러리 = 31312978, 2026-09-23 카페 주소 실측)
    menu_id        BIGINT       NULL,                     -- 게시판 menuid — NAVER에서 확인 후 입력 (추측 저장 금지)
    board_name     VARCHAR(100) NOT NULL,
    cafe_name      VARCHAR(100) NOT NULL,
    enabled        TINYINT(1)   NOT NULL DEFAULT 0,
    memo           VARCHAR(500),
    created_by     VARCHAR(50),
    updated_by     VARCHAR(50),
    created_at     DATETIME(6),
    updated_at     DATETIME(6),
    PRIMARY KEY (board_row_id),
    UNIQUE KEY uk_jipijigi_board (club_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 운영자 수동 명령 (preset 버튼/자유 텍스트). 자동 스케줄 없음 — V1은 명령 접수형.
CREATE TABLE newstart_jipijigi_commands (
    command_id     BIGINT       NOT NULL AUTO_INCREMENT,
    instruction    TEXT         NOT NULL,                 -- 운영자 자연어 명령 원문
    command_type   VARCHAR(30)  NOT NULL DEFAULT 'CUSTOM',-- RECENT_QUESTIONS|CCRS_RESEARCH|INTRO_POST|CUSTOM
    status         VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED', -- RECEIVED|RUNNING|DONE|FAILED
    result_summary TEXT,
    error_message  VARCHAR(500),
    created_by     VARCHAR(50),
    created_at     DATETIME(6),
    updated_at     DATETIME(6),
    PRIMARY KEY (command_id),
    INDEX idx_jipijigi_command_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 실제 수집된 카페 질문 (질문 발명 금지 — 수집 실패 시 body_status=UNREADABLE로 명시).
CREATE TABLE newstart_jipijigi_questions (
    question_id    BIGINT       NOT NULL AUTO_INCREMENT,
    article_id     VARCHAR(20)  NOT NULL,                 -- 카페 게시글 번호
    club_id        BIGINT       NOT NULL,
    menu_id        BIGINT       NULL,                     -- 질문게시판=1 (2026-09-23 수집 JSON boardId 실측)
    board_name     VARCHAR(100),
    title          VARCHAR(300) NOT NULL,
    url            VARCHAR(500) NOT NULL,
    written_at     DATETIME,
    body           MEDIUMTEXT,
    body_status    VARCHAR(20)  NOT NULL DEFAULT 'READABLE', -- READABLE|UNREADABLE
    comment_count  INT,
    comments_json  MEDIUMTEXT,                            -- 수집 원본 (Agent 게시물 재노출 시 닉네임 비공개)
    collected_at   DATETIME(6),
    created_at     DATETIME(6),
    updated_at     DATETIME(6),
    PRIMARY KEY (question_id),
    UNIQUE KEY uk_jipijigi_question_article (club_id, article_id),
    INDEX idx_jipijigi_question_written (written_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Agent 초안. 상태: DRAFT → READY_FOR_REVIEW → APPROVED → PUBLISHED / REJECTED.
-- V1에서 PUBLISHED 전이는 NAVER 연동 미구현으로 차단된다 (서버 강제).
CREATE TABLE newstart_jipijigi_drafts (
    draft_id        BIGINT       NOT NULL AUTO_INCREMENT,
    command_id      BIGINT       NULL,                    -- 이 초안을 만든 명령 (수작동 조사면 NULL)
    question_id     BIGINT       NULL,                    -- ANSWER_TYPE별 대상 질문 (INTRO는 NULL)
    draft_type      VARCHAR(30)  NOT NULL,                -- INTRO|QUESTION_ANSWER
    title           VARCHAR(300) NOT NULL,
    body            MEDIUMTEXT   NOT NULL,                -- 게시 원문 (AI 고지 문구 포함)
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    target_board_id BIGINT       NULL,                    -- newstart_jipijigi_target_boards 참조
    published_url   VARCHAR(500) NULL,                    -- V1: 항상 NULL
    reject_reason   VARCHAR(500),
    created_by      VARCHAR(50),
    updated_by      VARCHAR(50),
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    PRIMARY KEY (draft_id),
    INDEX idx_jipijigi_draft_status (status, updated_at),
    INDEX idx_jipijigi_draft_question (question_id),
    INDEX idx_jipijigi_draft_command (command_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 상태 전이 감사 로그 (provenance).
CREATE TABLE newstart_jipijigi_draft_status_log (
    log_id       BIGINT      NOT NULL AUTO_INCREMENT,
    draft_id     BIGINT      NOT NULL,
    from_status  VARCHAR(20),
    to_status    VARCHAR(20) NOT NULL,
    actor        VARCHAR(50),
    reason       VARCHAR(500),
    created_at   DATETIME(6),
    PRIMARY KEY (log_id),
    INDEX idx_jipijigi_status_log_draft (draft_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 조사 추적: 질문/초안별 STEP별 근거. "Agent가 우리 DB를 보고 답했는지" 감사의 근거 테이블.
CREATE TABLE newstart_jipijigi_research_traces (
    trace_id      BIGINT      NOT NULL AUTO_INCREMENT,
    draft_id      BIGINT      NULL,
    question_id   BIGINT      NULL,
    command_id    BIGINT      NULL,
    step_no       INT         NOT NULL,                   -- 1 내부DB/RAG 2 신복위 3 부실차주카페 4 공식검증 5 추가기관
    source_class  VARCHAR(30) NOT NULL,                   -- OFFICIAL_RULE|INTERNAL_CASE|CCRS_CAFE_CASE|BORROWER_CAFE_CASE|OPERATOR_MATERIAL|OTHER
    ref_type      VARCHAR(30) NOT NULL,                   -- KNOWLEDGE_ITEM|EMPIRICAL_CASE|OFFICIAL_UPDATE|CAFE_ARTICLE|EXTERNAL_URL|NONE
    ref_id        VARCHAR(50),
    ref_url       VARCHAR(500),
    excerpt       TEXT,
    note          VARCHAR(500),                           -- 부족/실패/수동조사 필요 사유 명시
    searched_at   DATETIME(6),
    PRIMARY KEY (trace_id),
    INDEX idx_jipijigi_trace_draft (draft_id, step_no),
    INDEX idx_jipijigi_trace_question (question_id, step_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
