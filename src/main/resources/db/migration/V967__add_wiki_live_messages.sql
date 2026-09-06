-- 공개 BuildIt Wiki(/wiki) 메인 "WIKI LIVE" 한 줄 활동 메시지.
-- 로그인 회원만 작성(작성자는 authenticated principal 로만 기록), 조회는 비회원 포함 공개.
-- display_name_snapshot 은 작성 시점 "마스킹된" 표시명(예: "김OO") — 조회 시 users 조인 불필요, 재역마스킹 위험 없음.
-- 신규 테이블만 추가(기존 row 무관). 다음 마이그레이션 = V968.
CREATE TABLE wiki_live_messages (
    wiki_live_message_id  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,
    display_name_snapshot  VARCHAR(60)  NOT NULL,          -- 작성 시점 마스킹 표시명 스냅샷
    message               VARCHAR(500) NOT NULL,           -- 서버에서 escape 후 저장(최대 100자 입력, escape 여유분 포함)
    status                VARCHAR(20)  NOT NULL DEFAULT 'VISIBLE',  -- VISIBLE|HIDDEN(관리자 숨김)
    deleted_at            DATETIME(6)  NULL,
    created_at            DATETIME(6),
    updated_at            DATETIME(6),
    created_by            VARCHAR(50),
    updated_by            VARCHAR(50),
    PRIMARY KEY (wiki_live_message_id),
    -- sinceId 폴링(WHERE status='VISIBLE' AND id > :sinceId ORDER BY id) + 최신 N개 조회
    INDEX idx_wiki_live_messages_status_id (status, wiki_live_message_id),
    -- 도배 방지 rate limit 은 인메모리지만, 운영 조회용으로 남김
    INDEX idx_wiki_live_messages_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
