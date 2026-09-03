-- 공개 BuildIt Wiki(/wiki) — 문항별 Question Wiki + THREAD.
-- 하나의 모의고사 문항을 장기적으로 진화하는 Knowledge Unit 으로 취급한다.
-- 교육자·학생이 한 문항을 중심으로 질문·해설·토론·변형문제·기출을 축적하고,
-- 이후 AI 가 문항 전체 Context 를 안정적 question_wiki_id 로 한 번에 읽을 수 있게 한다.
--
-- 전부 신규 테이블 + 신규 seed row → 기존 데이터 무관, 1단계 마이그레이션.
-- 기존 /wiki 커뮤니티(community_posts, scope=wiki)·자료실(wiki_exam_originals·wiki_analysis_materials)은 건드리지 않는다.
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ── 1) 문항 (Knowledge Unit) ─────────────────────────────────────────
CREATE TABLE question_wikis (
    question_wiki_id  BIGINT       NOT NULL AUTO_INCREMENT,
    subject           VARCHAR(20)  NOT NULL DEFAULT 'ENGLISH',
    grade             INT          NOT NULL,                 -- 1|2 (고1/고2). 향후 3 확장 가능.
    exam_year         INT          NOT NULL,
    exam_label        VARCHAR(100) NOT NULL,                 -- 예: "9월 전국연합학력평가"
    question_number   INT          NOT NULL,
    passage_group_key VARCHAR(60)  NULL,                     -- 공동지문 묶음(예: 41-42). NULL=단독 지문.
    title             VARCHAR(200) NULL,                     -- 짧은 설명(선택). 예: "빈칸 추론 - evergreen tree 비유"
    is_visible        BIT(1)       NOT NULL DEFAULT b'1',
    created_at        DATETIME(6),
    updated_at        DATETIME(6),
    created_by        VARCHAR(50),
    updated_by        VARCHAR(50),
    PRIMARY KEY (question_wiki_id),
    CONSTRAINT uk_question_wikis_identity
        UNIQUE (subject, grade, exam_year, exam_label, question_number),
    INDEX idx_question_wikis_nav (subject, grade, exam_year, exam_label, question_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 2) 문항 ↔ 기존 Wiki 자료 연결 (원본 / 분석 자료) ─────────────────
-- 자료 자체는 wiki_exam_originals·wiki_analysis_materials 에 이미 있고, 여기서는 링크만 건다.
CREATE TABLE question_wiki_material_links (
    link_id          BIGINT       NOT NULL AUTO_INCREMENT,
    question_wiki_id  BIGINT       NOT NULL,
    material_kind     VARCHAR(30)  NOT NULL,                 -- EXAM_ORIGINAL | ANALYSIS_MATERIAL
    material_id       BIGINT       NOT NULL,                 -- wiki_exam_originals / wiki_analysis_materials PK
    label            VARCHAR(200) NULL,
    created_at        DATETIME(6),
    updated_at        DATETIME(6),
    created_by        VARCHAR(50),
    updated_by        VARCHAR(50),
    PRIMARY KEY (link_id),
    CONSTRAINT fk_qwml_question FOREIGN KEY (question_wiki_id) REFERENCES question_wikis(question_wiki_id) ON DELETE CASCADE,
    CONSTRAINT uk_qwml_unique UNIQUE (question_wiki_id, material_kind, material_id),
    INDEX idx_qwml_question (question_wiki_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 3) THREAD 글 ────────────────────────────────────────────────────
-- data_kind 로 데이터 성격을 저장 시점에 고정한다(AI 신뢰도 판단용):
--   DISCUSSION         커뮤니티 지식(질문·의견·해석·풀이·출제예상·수업경험·해설개선)
--   GENERATED_PRACTICE 사용자 제작 변형/예상 문제 (question_thread_variant_details 동반)
--   EXAM_EVIDENCE      실제 학교 기출 증거     (question_thread_exam_evidence 동반)
-- category 는 별도 축(질문/해석·구문/어법/어휘/출제포인트/예상문제/변형문제/풀이·해설/해설개선/수업인사이트/기타).
-- parent_post_id 로 1단계 답글만 허용한다(답글의 답글 금지 — 서비스에서 강제).
CREATE TABLE question_thread_posts (
    post_id          BIGINT       NOT NULL AUTO_INCREMENT,
    question_wiki_id  BIGINT       NOT NULL,
    parent_post_id    BIGINT       NULL,
    author_user_id    BIGINT       NOT NULL,
    author_role       VARCHAR(10)  NOT NULL,                 -- 작성 시점 role 스냅샷(STUDENT/TUTOR/ADMIN)
    category          VARCHAR(30)  NOT NULL,
    data_kind         VARCHAR(20)  NOT NULL,
    title            VARCHAR(200) NULL,
    content          TEXT         NOT NULL,
    is_visible        BIT(1)       NOT NULL DEFAULT b'1',
    hidden_at         DATETIME(6)  NULL,
    hidden_by_user_id BIGINT       NULL,
    created_at        DATETIME(6),
    updated_at        DATETIME(6),
    created_by        VARCHAR(50),
    updated_by        VARCHAR(50),
    PRIMARY KEY (post_id),
    CONSTRAINT fk_qtp_question FOREIGN KEY (question_wiki_id) REFERENCES question_wikis(question_wiki_id) ON DELETE CASCADE,
    CONSTRAINT fk_qtp_parent FOREIGN KEY (parent_post_id) REFERENCES question_thread_posts(post_id) ON DELETE CASCADE,
    CONSTRAINT fk_qtp_author FOREIGN KEY (author_user_id) REFERENCES users(user_id),
    CONSTRAINT fk_qtp_hidden_by FOREIGN KEY (hidden_by_user_id) REFERENCES users(user_id),
    INDEX idx_qtp_question_visible (question_wiki_id, is_visible, created_at, post_id),
    INDEX idx_qtp_question_kind (question_wiki_id, data_kind, is_visible),
    INDEX idx_qtp_question_category (question_wiki_id, category, is_visible),
    INDEX idx_qtp_parent (parent_post_id, is_visible, created_at),
    INDEX idx_qtp_author (author_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 4) 변형/예상 문제 상세 (GENERATED_PRACTICE) ─────────────────────
-- 향후 우수 변형문제를 문제 DB / WORKBOOK 으로 승격할 수 있도록 구조화해서 저장한다.
CREATE TABLE question_thread_variant_details (
    variant_id       BIGINT       NOT NULL AUTO_INCREMENT,
    post_id          BIGINT       NOT NULL,
    question_text    TEXT         NOT NULL,
    question_type    VARCHAR(40)  NULL,                      -- 빈칸/어법/서답형/내용일치 등 자유 입력
    answer           TEXT         NULL,
    explanation      TEXT         NULL,
    source_sentence  TEXT         NULL,
    source_question  VARCHAR(200) NULL,
    difficulty       VARCHAR(20)  NULL,
    created_at        DATETIME(6),
    updated_at        DATETIME(6),
    created_by        VARCHAR(50),
    updated_by        VARCHAR(50),
    PRIMARY KEY (variant_id),
    CONSTRAINT fk_qtvd_post FOREIGN KEY (post_id) REFERENCES question_thread_posts(post_id) ON DELETE CASCADE,
    CONSTRAINT uk_qtvd_post UNIQUE (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 5) 기출 증거 상세 (EXAM_EVIDENCE) ──────────────────────────────
-- 실제 학교 내신에서 이 지문/문장이 어떻게 출제됐는지. 예상(GENERATED_PRACTICE)과 절대 같은 데이터로 취급하지 않는다.
CREATE TABLE question_thread_exam_evidence (
    evidence_id       BIGINT       NOT NULL AUTO_INCREMENT,
    post_id           BIGINT       NOT NULL,
    school_name       VARCHAR(100) NULL,
    grade_label       VARCHAR(20)  NULL,
    exam_period       VARCHAR(60)  NULL,                     -- 예: "2026 2학기 중간"
    related_sentence  TEXT         NULL,
    exam_question_type VARCHAR(40) NULL,
    exam_point        TEXT         NULL,
    description       TEXT         NULL,
    exam_id           BIGINT       NULL,                     -- 향후 exams 테이블 연결(선택)
    created_at        DATETIME(6),
    updated_at        DATETIME(6),
    created_by        VARCHAR(50),
    updated_by        VARCHAR(50),
    PRIMARY KEY (evidence_id),
    CONSTRAINT fk_qtee_post FOREIGN KEY (post_id) REFERENCES question_thread_posts(post_id) ON DELETE CASCADE,
    CONSTRAINT uk_qtee_post UNIQUE (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 6) THREAD 첨부 (lesson_community_attachments 형태) ──────────────
CREATE TABLE question_thread_attachments (
    attachment_id      BIGINT       NOT NULL AUTO_INCREMENT,
    post_id            BIGINT       NOT NULL,
    uploader_user_id   BIGINT       NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type       VARCHAR(100) NOT NULL,
    file_size          BIGINT       NOT NULL,
    storage_key        VARCHAR(500) NOT NULL,
    is_visible         BIT(1)       NOT NULL DEFAULT b'1',
    created_at         DATETIME(6),
    updated_at         DATETIME(6),
    created_by         VARCHAR(50),
    updated_by         VARCHAR(50),
    PRIMARY KEY (attachment_id),
    CONSTRAINT fk_qta_post FOREIGN KEY (post_id) REFERENCES question_thread_posts(post_id) ON DELETE CASCADE,
    CONSTRAINT fk_qta_uploader FOREIGN KEY (uploader_user_id) REFERENCES users(user_id),
    INDEX idx_qta_post (post_id, is_visible, attachment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 7) AI 종합 요약 (버전형) ───────────────────────────────────────
-- spec §9·§10: AI 는 원문을 자동 수정하지 않는다. AI Suggestion → Human Approval → New Version.
-- 이번 PR 에는 write 경로 없음(빈 테이블). Context 응답에 최신 APPROVED 요약을 포함만 한다.
CREATE TABLE question_ai_summaries (
    summary_id        BIGINT       NOT NULL AUTO_INCREMENT,
    question_wiki_id  BIGINT       NOT NULL,
    version           INT          NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT | APPROVED
    summary_text      MEDIUMTEXT   NOT NULL,
    thread_post_count INT          NULL,
    created_at        DATETIME(6),
    updated_at        DATETIME(6),
    created_by        VARCHAR(50),
    updated_by        VARCHAR(50),
    PRIMARY KEY (summary_id),
    CONSTRAINT fk_qas_question FOREIGN KEY (question_wiki_id) REFERENCES question_wikis(question_wiki_id) ON DELETE CASCADE,
    CONSTRAINT uk_qas_version UNIQUE (question_wiki_id, version),
    INDEX idx_qas_question_status (question_wiki_id, status, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── seed: 2026년 9월 전국연합학력평가 고1·고2 영어, 문항 20~24 / 29~42 ──
-- 문항 범위는 사용자가 지정한 대상 범위(20-24, 29-42)를 그대로 쓴다. 임의 확장하지 않는다.
-- 41-42 는 공동지문 한 세트로 묶는다.
INSERT INTO question_wikis
    (subject, grade, exam_year, exam_label, question_number, passage_group_key, title, is_visible, created_at, updated_at)
SELECT 'ENGLISH', g.grade, 2026, '9월 전국연합학력평가', n.num,
       CASE WHEN n.num IN (41, 42) THEN CONCAT('ENGLISH-G', g.grade, '-2026-09-41_42') ELSE NULL END,
       NULL, b'1', NOW(6), NOW(6)
FROM (SELECT 1 AS grade UNION ALL SELECT 2) g
CROSS JOIN (
    SELECT 20 AS num UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
    UNION ALL SELECT 29 UNION ALL SELECT 30 UNION ALL SELECT 31 UNION ALL SELECT 32 UNION ALL SELECT 33
    UNION ALL SELECT 34 UNION ALL SELECT 35 UNION ALL SELECT 36 UNION ALL SELECT 37 UNION ALL SELECT 38
    UNION ALL SELECT 39 UNION ALL SELECT 40 UNION ALL SELECT 41 UNION ALL SELECT 42
) n;
