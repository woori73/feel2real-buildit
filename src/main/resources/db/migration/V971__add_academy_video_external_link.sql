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
--   * 값 검증은 애플리케이션이 소유한다(purpose 컬럼과 동일 방침, V963): JPA @Enumerated(STRING)
--     은 AcademyVideoSourceKind{DIRECT_UPLOAD,EXTERNAL_LINK}·AcademyExternalVideoProvider 의
--     enum 값만 기록하고, AcademyVideo.validateSourceContract() 가 EXTERNAL 불변식을 강제한다.
--     DB CHECK / FK / generated column 을 추가하지 않는다(V969 errno 3104 류의 위험 제거).
--   * external_* 컬럼은 EXTERNAL_LINK 행에서만 채워진다(DIRECT_UPLOAD 는 NULL).
ALTER TABLE academy_videos
    ADD COLUMN source_kind        VARCHAR(20)   NOT NULL DEFAULT 'DIRECT_UPLOAD'
        COMMENT 'DIRECT_UPLOAD|EXTERNAL_LINK. 도입 이전 행은 DIRECT_UPLOAD 로 backfill',
    ADD COLUMN external_url       VARCHAR(2048) NULL
        COMMENT 'EXTERNAL_LINK 전용 — 외부 영상/콘텐츠 페이지 URL(https). 서버로 복제하지 않음',
    ADD COLUMN external_provider  VARCHAR(20)   NULL
        COMMENT 'EXTERNAL_LINK 전용 — NAVER|YOUTUBE|GENERIC (host 로만 판정)',
    ADD COLUMN external_embed_url VARCHAR(2048) NULL
        COMMENT 'EXTERNAL_LINK 전용 — iframe embed 가능 provider 만(YouTube). NULL 이면 CTA 링크만';
