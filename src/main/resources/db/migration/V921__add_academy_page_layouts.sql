SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- Academy Builder Phase 1: 한 Academy당 1개의 페이지 레이아웃을 보존한다.
-- layout_json 은 raw HTML/CSS 가 아닌 allowlist 기반 semantic JSON 만 저장한다.
CREATE TABLE academy_page_layouts (
    layout_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    academy_id BIGINT NOT NULL,
    layout_version INT NOT NULL DEFAULT 1,
    layout_json LONGTEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    UNIQUE KEY uk_academy_page_layouts_academy (academy_id),
    CONSTRAINT fk_academy_page_layouts_academy FOREIGN KEY (academy_id) REFERENCES academies(academy_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
