-- ============================================================
-- V1044: 현투 감사회원 명단 수동 추가 (2026-10-07 사용자 지시)
--   · 뉴스타트 사이트 감사회원 식별용. 기존 명단과 동일 형식 (닉네임 + 네이버ID 앞 2자리).
--   · 회원가입 시 NewStartIdPwSignupService.uniqueMatch(loginId, nickname) →
--     nickname='현투' + loginId startsWith 'js' + 미연결 1건이면 THANKS_MEMBER 부여.
--   · 카페 멤버 태그: uqs9082xgogn (2026-10-07 카페 가입).
--   · UNIQUE KEY (snapshot_date, cafe_member_tag) 기준 idempotent.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

INSERT INTO `newstart_thanks_roster`
    (`cafe_member_tag`, `naver_id_prefix`, `nickname`, `grade`, `grade_changed_at`,
     `member_level`, `source`, `snapshot_date`, `source_url`, `source_file`,
     `match_status`, `linked_user_id`, `linked_provider_user_id`)
VALUES
    ('uqs9082xgogn', 'js', '현투', '감사멤버', NULL,
     150, 'MANUAL', '2026-10-07', NULL, NULL,
     'UNMATCHED', NULL, NULL)
ON DUPLICATE KEY UPDATE
    `naver_id_prefix` = VALUES(`naver_id_prefix`),
    `nickname` = VALUES(`nickname`),
    `grade` = VALUES(`grade`);
