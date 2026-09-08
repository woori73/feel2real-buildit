SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 외부에 이미 올라간 학원 영상(네이버·YouTube 등)을 URL 자산으로만 등록할 수 있게 한다
-- (작업지시서 §3·§4). 영상 binary 를 서버로 복제하지 않는다 — external_url 만 저장한다.
-- 다음 마이그레이션 = V972. 이전 = V970.
--
-- 설계:
--   * 별도 테이블을 만들지 않는다 — midterm_live_video_links.academy_video_id FK(ON DELETE
--     CASCADE, V970)와 학원당 영상 개수 상한을 그대로 재사용하려면 academy_videos 행이어야 한다.
--   * source_kind 는 NOT NULL DEFAULT 'DIRECT_UPLOAD' 로 기존 행 전부를 안전하게 backfill 한다.
--     기존 업로드 영상의 storage key·저장 방식·URL 은 이 마이그레이션으로 바뀌지 않는다.
--   * external_* 컬럼은 EXTERNAL_LINK 행에서만 채워진다(DIRECT_UPLOAD 는 NULL).
--   * generated column 없음 → V969(errno 3104) 문제와 무관. 단순 컬럼 + CHECK 만 추가한다.
ALTER TABLE academy_videos
    ADD COLUMN source_kind        VARCHAR(20)   NOT NULL DEFAULT 'DIRECT_UPLOAD',
    ADD COLUMN external_url       VARCHAR(2048) NULL,
    ADD COLUMN external_provider  VARCHAR(20)   NULL,
    ADD COLUMN external_embed_url VARCHAR(2048) NULL,
    ADD CONSTRAINT ck_academy_videos_source_kind
        CHECK (source_kind IN ('DIRECT_UPLOAD', 'EXTERNAL_LINK'));
