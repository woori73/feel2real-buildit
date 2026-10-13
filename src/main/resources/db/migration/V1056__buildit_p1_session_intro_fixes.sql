-- BuildIt P1 F1/F2: Claude 검수 결함 수정
--
-- F1: 승인 전 생성 세션의 academy_id를 NULL 허용으로 변경
-- /create가 Academy 생성 전에 세션을 저장하므로, 가짜 ID(0L) 대신 NULL을 사용한다.
-- V1055의 FK 제약은 유지된다 (NULL은 FK 검사를 통과).
ALTER TABLE academy_ai_sessions
    MODIFY COLUMN academy_id BIGINT NULL COMMENT '승인 전 생성 세션은 NULL';

-- F2: 버전 스냅샷에 top-level intro 포함
-- AI가 변경한 intro를 복원 시 함께 되돌리기 위함.
ALTER TABLE academy_page_versions
    ADD COLUMN intro TEXT NULL COMMENT 'AI 변경 시점의 top-level intro 스냅샷';
