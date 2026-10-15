SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 강사 학원 홍보 영상 업로드(베타 P1). 업로드 원본을 ffmpeg로 H.264/AAC/MP4(720p 캡)로 정규화한
-- 산출물과 썸네일 1프레임의 storage key를 보존한다. 원본은 변환 후 폐기하므로 저장하지 않는다.
--
-- status: 동기 변환이라 PROCESSING이 없고 READY/FAILED만 둔다(spec Non-Goal: 비동기 잡/상태머신).
-- 삭제 정책이 물리삭제라 soft-delete 컬럼 없음.
-- 소유자는 academies.tutor_user_id(1:1 UNIQUE)로 유일하게 파생되므로 owner 컬럼을 중복 보관하지 않는다.
CREATE TABLE academy_videos (
    video_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    academy_id BIGINT NOT NULL,
    original_filename VARCHAR(255),
    storage_key VARCHAR(500) NOT NULL,
    thumbnail_key VARCHAR(500) NOT NULL,
    duration_sec INT,
    size_bytes BIGINT,
    width INT,
    height INT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    KEY idx_academy_videos_academy_status_created (academy_id, status, created_at DESC),
    CONSTRAINT fk_academy_videos_academy FOREIGN KEY (academy_id) REFERENCES academies(academy_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
