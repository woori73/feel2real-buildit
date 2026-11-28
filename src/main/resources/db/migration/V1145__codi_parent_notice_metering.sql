CREATE TABLE codi_usage_counter (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    academy_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    confirmed_qty BIGINT NOT NULL DEFAULT 0,
    reserved_qty BIGINT NOT NULL DEFAULT 0,
    plan_version VARCHAR(32) NULL,
    CONSTRAINT uq_codi_counter_period UNIQUE (academy_id, period_start, task_type),
    CONSTRAINT ck_codi_counter_quantities CHECK (confirmed_qty >= 0 AND reserved_qty >= 0)
) ENGINE=InnoDB;

CREATE TABLE codi_task_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    academy_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    result_kind VARCHAR(32) NOT NULL,
    result_id VARCHAR(100) NOT NULL,
    qty INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uq_codi_ledger_request UNIQUE (request_key),
    CONSTRAINT uq_codi_ledger_result UNIQUE (result_id)
) ENGINE=InnoDB;
