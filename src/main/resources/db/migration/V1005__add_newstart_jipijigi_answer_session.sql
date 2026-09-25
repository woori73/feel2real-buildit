-- 지피지기 Agent — 답변 세션 자동 축적 저장소
-- 규칙: 답변 생성에 성공하면 게시 성공 여부와 무관하게 이 테이블에 저장한다.
--       다음 질문에서는 외부 카페 검색보다 먼저 이 축적 DB를 RAG 검색한다.
--       카페 경험사례(source_class)와 공식자료는 분리 보관하며, Agent 답변 자체를 공식 사실로 승격하지 않는다.
--       (재사용 시 Evidence.sourceClass=INTERNAL_CASE, RefType=ANSWER_SESSION 고정)
-- ADMIN ONLY: /api/admin/newstart/jipijigi/** (SecurityConfig catch-all 규칙 준수)

CREATE TABLE newstart_jipijigi_answer_sessions (
    session_id         BIGINT       NOT NULL AUTO_INCREMENT,
    fingerprint        CHAR(64)     NOT NULL,                    -- sha256(정규화 질문 + 근거 ref 세트) → dedupe 키
    draft_id           BIGINT       NULL,                        -- 이 답변을 만든 초안 (1:1)
    question_id        BIGINT       NULL,
    command_id         BIGINT       NULL,
    -- 후속 회원 댓글·결과 연결용 식별자 (운영자 보고로 채워진다)
    source_club_id     BIGINT       NULL,
    source_article_id  VARCHAR(20)  NULL,
    source_comment_id  VARCHAR(30)  NULL,                        -- Agent 답변이 등록된 댓글 ID
    follow_up_ref      VARCHAR(100) NULL,                        -- 이후 회원 후속 댓글/결과 글 식별자
    question_type      VARCHAR(30)  NOT NULL,                    -- QUESTION_ANSWER|RESEARCH_REPORT
    question_topic     VARCHAR(100) NOT NULL,                    -- 운영원칙 §3 9개 축적 분야 중 하나
    question_text      MEDIUMTEXT   NOT NULL,                    -- 원 질문
    answer_title       VARCHAR(300) NOT NULL,
    answer_body        MEDIUMTEXT   NOT NULL,                    -- 최종 답변 (정정·수정 후 본문을 반영)
    answered_at        DATETIME(6)  NOT NULL,
    -- 출처별 근거 (JSON 배열; source_class별 분리 보관)
    evidence_internal  TEXT         NULL,                        -- 내부 DB/RAG 근거
    evidence_ccrs      TEXT         NULL,                        -- 신복위 카페 글·댓글·링크
    evidence_borrower  TEXT         NULL,                        -- 부실차주 카페 글·댓글·링크
    evidence_official  TEXT         NULL,                        -- 공식자료 출처·링크
    db_hits            TEXT         NULL,                        -- STEP별 hit 수 {step1..step5,total}
    external_searched  TINYINT(1)   NOT NULL DEFAULT 0,          -- STEP2/STEP3 외부 카페 검색 수행 여부
    confidence         VARCHAR(20)  NOT NULL,                    -- HIGH|MEDIUM|LOW
    operator_edited    TINYINT(1)   NOT NULL DEFAULT 0,          -- 운영자가 초안 본문을 수정했는지
    published_as       VARCHAR(20)  NOT NULL DEFAULT 'NONE',     -- NONE|POST|COMMENT (V1: 운영자 수동 게시 보고)
    published_url      VARCHAR(500) NULL,
    review_state       VARCHAR(20)  NOT NULL DEFAULT 'CANDIDATE',-- CANDIDATE|ACCEPTED|REJECTED — ACCEPTED만 RAG 재사용
    created_at         DATETIME(6),
    updated_at         DATETIME(6),
    PRIMARY KEY (session_id),
    UNIQUE KEY uk_jipijigi_session_fp (fingerprint),
    UNIQUE KEY uk_jipijigi_session_draft (draft_id),
    INDEX idx_jipijigi_session_review (review_state, answered_at),
    INDEX idx_jipijigi_session_question (question_id),
    INDEX idx_jipijigi_session_article (source_club_id, source_article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
