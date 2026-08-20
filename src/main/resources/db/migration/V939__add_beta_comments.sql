-- 랜딩 '/' 베타 서비스 의견 수집·표시 + 어드민 모더레이션 (#252)
-- beta_comments: 로그인 사용자가 남긴 베타 의견. 기본 PENDING, 어드민 승인(APPROVED)만 랜딩 노출.
--
-- 표시모드 테이블(beta_landing_setting)은 두지 않는다. 더미/실데이터 전환은 승인 건수에서
-- 파생시킨다(0건이면 하드코딩 더미, 1건 이상이면 실데이터) — 수동 토글 상태와 실제 데이터가
-- 어긋나는 경우를 아예 만들지 않기 위함이다.

CREATE TABLE beta_comments (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    nickname         VARCHAR(12)  NOT NULL,
    participant_type VARCHAR(20)  NOT NULL,
    comment          VARCHAR(80)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    highlight_label  VARCHAR(20)  NULL,
    created_at       DATETIME(6),
    updated_at       DATETIME(6),
    created_by       VARCHAR(50),
    updated_by       VARCHAR(50),
    PRIMARY KEY (id),
    CONSTRAINT fk_beta_comments_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 랜딩 조회(status='APPROVED' ORDER BY created_at DESC LIMIT 10)와 어드민 상태별 목록을 함께 태운다.
CREATE INDEX idx_beta_comments_status_created ON beta_comments (status, created_at);
