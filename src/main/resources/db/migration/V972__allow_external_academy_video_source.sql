SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- Bug: Production 에서 외부 링크 영상 등록(POST /api/tutor/academy/videos/link)이 항상 실패했다.
--
-- 원인: V971 이 외부 링크 영상을 위해 active_source='EXTERNAL' / source_kind='EXTERNAL_LINK'
-- 을 도입했지만, V938 이 만든 두 CHECK 제약을 갱신하지 않았다:
--   * chk_academy_videos_active_source  →  active_source IN ('RAW','NORMALIZED') 만 허용
--   * chk_academy_videos_source_metadata →  RAW / NORMALIZED shape 만 허용
-- 실 MySQL(8.4)은 CHECK 를 강제하므로 EXTERNAL row INSERT 가 errno 3819 로 거부되고,
-- 매핑되지 않은 DataIntegrityViolationException 이 500 으로 나가 화면엔 "영상 링크 등록에
-- 실패했습니다." 만 보였다. 단위 테스트는 실 MySQL 을 돌리지 않아 놓쳤다.
--
-- 수정: 두 제약을 EXTERNAL shape 를 허용하도록 재정의한다. RAW / NORMALIZED 분기는 V938
-- 원문 그대로 보존한다(기존 행/경로 무영향). EXTERNAL 분기는 AcademyVideo.validateSourceContract()
-- 의 불변식과 일치한다: external_url 존재 · storage key 전부 NULL · raw cleanup 불필요 ·
-- lifecycle READY 또는 DELETING.
--
-- 다음 = V973. 이전 = V971.

ALTER TABLE academy_videos
    DROP CHECK chk_academy_videos_active_source,
    DROP CHECK chk_academy_videos_source_metadata;

ALTER TABLE academy_videos
    ADD CONSTRAINT chk_academy_videos_active_source
        CHECK (active_source IN ('RAW', 'NORMALIZED', 'EXTERNAL')),
    ADD CONSTRAINT chk_academy_videos_source_metadata
        CHECK (
            (
                active_source = 'RAW'
                AND raw_storage_key IS NOT NULL
                AND raw_media_type IS NOT NULL
                AND raw_size_bytes > 0
                AND (
                    (lifecycle_status = 'READY'
                        AND raw_cleanup_status = 'NOT_REQUIRED')
                    OR
                    (lifecycle_status = 'DELETING'
                        AND raw_cleanup_status = 'PENDING')
                )
            )
            OR
            (
                active_source = 'NORMALIZED'
                AND normalized_storage_key IS NOT NULL
                AND normalized_media_type = 'video/mp4'
                AND normalized_size_bytes > 0
                AND thumbnail_key IS NOT NULL
                AND (
                    (raw_cleanup_status = 'PENDING'
                        AND raw_storage_key IS NOT NULL
                        AND raw_media_type IS NOT NULL
                        AND raw_size_bytes > 0)
                    OR
                    (raw_cleanup_status = 'DONE'
                        AND raw_storage_key IS NULL
                        AND raw_media_type IS NULL
                        AND raw_size_bytes IS NULL)
                )
            )
            OR
            (
                active_source = 'EXTERNAL'
                AND external_url IS NOT NULL
                AND raw_storage_key IS NULL
                AND normalized_storage_key IS NULL
                AND thumbnail_key IS NULL
                AND raw_cleanup_status = 'NOT_REQUIRED'
                AND lifecycle_status IN ('READY', 'DELETING')
            )
        );
