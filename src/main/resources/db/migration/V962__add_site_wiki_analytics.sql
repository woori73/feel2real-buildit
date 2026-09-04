-- 통합 Analytics: F2R 전체 방문(PV/UV) + Wiki 방문(PV/UV) + Wiki 다운로드 기록.
-- 신규 테이블만 추가(기존 row 무관). 다음 마이그레이션 = V963.

-- 실제 페이지 진입 이벤트 로그(1건 = 1 PV). 정적 리소스(css/js/img)·API 호출은 SiteVisitFilter가
-- 응답 Content-Type(text/html)으로 걸러 저장 대상에서 제외한다. IP는 저장하지 않는다.
-- UV(순 방문자)는 visitor_key(로그인: "U:{user_id}", 비로그인: "S:{session id}") distinct count로 계산한다.
CREATE TABLE site_page_visits (
    site_page_visit_id BIGINT       NOT NULL AUTO_INCREMENT,
    visit_date          DATE         NOT NULL,
    visited_at          DATETIME(6)  NOT NULL,
    is_wiki             BIT(1)       NOT NULL DEFAULT b'0',   -- true면 /wiki 하위(Wiki 화면), false여도 F2R 전체 집계엔 포함
    path                VARCHAR(255) NOT NULL,
    user_id             BIGINT       NULL,                     -- 비로그인은 NULL
    role                VARCHAR(20)  NOT NULL,                 -- STUDENT|TUTOR|ADMIN|GUEST
    display_name        VARCHAR(60)  NOT NULL,                 -- 방문 시점 실명 스냅샷(비회원은 "비회원")
    visitor_key          VARCHAR(64)  NOT NULL,
    created_at          DATETIME(6),
    updated_at          DATETIME(6),
    created_by          VARCHAR(50),
    updated_by          VARCHAR(50),
    PRIMARY KEY (site_page_visit_id),
    INDEX idx_site_page_visits_date (visit_date),
    INDEX idx_site_page_visits_wiki_date (is_wiki, visit_date),
    INDEX idx_site_page_visits_visitor (visitor_key),
    INDEX idx_site_page_visits_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Wiki 자료 다운로드 이벤트 로그. 실제 download endpoint에서 파일을 정상적으로 읽어온 시점에만 기록한다
-- (프론트 버튼 클릭 카운트 아님). ORIGINAL|ANALYSIS|LEARNING = 기존 Wiki 자료 3종.
CREATE TABLE wiki_download_logs (
    wiki_download_log_id BIGINT       NOT NULL AUTO_INCREMENT,
    download_date         DATE         NOT NULL,
    downloaded_at         DATETIME(6)  NOT NULL,
    material_category     VARCHAR(20)  NOT NULL,               -- ORIGINAL|ANALYSIS|LEARNING
    material_id           BIGINT       NOT NULL,
    material_title        VARCHAR(255) NOT NULL,                -- 다운로드 시점 제목 스냅샷
    user_id               BIGINT       NULL,                    -- 비회원 다운로드 허용 자료는 NULL 가능
    role                  VARCHAR(20)  NOT NULL,                -- STUDENT|TUTOR|ADMIN|GUEST
    display_name          VARCHAR(60)  NOT NULL,
    created_at            DATETIME(6),
    updated_at            DATETIME(6),
    created_by            VARCHAR(50),
    updated_by            VARCHAR(50),
    PRIMARY KEY (wiki_download_log_id),
    INDEX idx_wiki_download_logs_date (download_date),
    INDEX idx_wiki_download_logs_material (material_category, material_id),
    INDEX idx_wiki_download_logs_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
