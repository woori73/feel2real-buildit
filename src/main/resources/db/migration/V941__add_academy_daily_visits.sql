-- 학원 공개 홈페이지의 세션 기준 일별 방문 횟수 집계.
-- 원시 IP·User-Agent·개인 식별자는 저장하지 않고 Academy/날짜별 숫자만 보존한다.
CREATE TABLE academy_daily_visits (
    daily_visit_id BIGINT      NOT NULL AUTO_INCREMENT,
    academy_id     BIGINT      NOT NULL,
    visit_date     DATE        NOT NULL,
    visit_count    BIGINT      NOT NULL DEFAULT 0,
    created_at     DATETIME(6),
    updated_at     DATETIME(6),
    created_by     VARCHAR(50),
    updated_by     VARCHAR(50),
    PRIMARY KEY (daily_visit_id),
    CONSTRAINT uk_academy_daily_visits_academy_date UNIQUE (academy_id, visit_date),
    CONSTRAINT fk_academy_daily_visits_academy
        FOREIGN KEY (academy_id) REFERENCES academies (academy_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

