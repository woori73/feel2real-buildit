-- 내신 영어 예상문제(1회 20,000원, 40문항) 의뢰 접수. 홈페이지 '예상문제 의뢰하기' 버튼의 실제 접수 대상.
-- 상태: RECEIVED(접수) -> IN_PROGRESS(제작 중) -> DONE(완료) / CANCELED(취소). 삭제 기능은 두지 않는다(접수 기록 보존).
CREATE TABLE exam_requests (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    user_id        BIGINT        NOT NULL,
    school_name    VARCHAR(60)   NOT NULL,
    grade          VARCHAR(20)   NOT NULL,
    exam_round     VARCHAR(40)   NOT NULL,
    exam_scope     VARCHAR(1000) NOT NULL,
    contact        VARCHAR(60)   NOT NULL,
    memo           VARCHAR(500)  NULL,
    question_count INT           NOT NULL,
    price_krw      INT           NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'RECEIVED',
    created_at     DATETIME(6),
    updated_at     DATETIME(6),
    created_by     VARCHAR(50),
    updated_by     VARCHAR(50),
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_requests_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_exam_requests_user_created ON exam_requests (user_id, created_at);
CREATE INDEX idx_exam_requests_status_created ON exam_requests (status, created_at);
