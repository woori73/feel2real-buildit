-- 학원 홈페이지 이미지 자산 테이블
-- HeroMediaSlot(원장사진·교재표지·시간표)과 구분되는 홈페이지 배너·배경용
-- 원본/편집본 구분, 버전 관리, 되돌리기 지원

CREATE TABLE IF NOT EXISTS academy_image_asset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    academy_id BIGINT NOT NULL COMMENT '소속 학원 ID (학원별 격리)',
    slot_key VARCHAR(50) NOT NULL COMMENT '이미지 슬롯 (HERO_BANNER, SECTION_BG 등)',
    original_path VARCHAR(500) NOT NULL COMMENT '원본 이미지 저장 경로',
    edited_path VARCHAR(500) COMMENT '편집본 이미지 저장 경로',
    version INT NOT NULL DEFAULT 1 COMMENT '버전 (1부터 시작)',
    previous_asset_id BIGINT COMMENT '이전 버전 자산 ID (되돌리기용)',
    edit_description VARCHAR(500) COMMENT '편집 작업 설명',
    staged_for_preview BOOLEAN NOT NULL DEFAULT FALSE COMMENT '미리보기 스테이징 여부',
    published BOOLEAN NOT NULL DEFAULT FALSE COMMENT '발행 여부',
    INDEX idx_academy_image_asset_academy (academy_id),
    INDEX idx_academy_image_asset_slot (academy_id, slot_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='학원 홈페이지 이미지 자산 (HeroMediaSlot과 구분)';
