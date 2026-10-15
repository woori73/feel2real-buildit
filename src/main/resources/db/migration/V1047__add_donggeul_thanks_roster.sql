-- ============================================================
-- V1047: 동글동글 감사회원 명단 수동 추가 (2026-10-08 사용자 지시)
--   · 2026-10-08 네이버 카페에서 감사멤버로 승격. NewStart 사이트 감사회원 식별용.
--   · 기존 명단과 동일 형식 (닉네임 + 네이버ID 앞 4자리).
--   · 회원가입 시 NewStartIdPwSignupService.uniqueMatch(loginId, nickname) →
--     nickname='동글동글' + loginId startsWith 'jeon' + 미연결 1건이면 THANKS_MEMBER 부여.
--   · 카페 멤버 태그: 1c0cvkmo2sro. 등급 변경일 2026-10-08 (카페 화면 확인).
--   · UNIQUE KEY (snapshot_date, cafe_member_tag) 기준 idempotent.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

INSERT INTO `newstart_thanks_roster`
    (`cafe_member_tag`, `naver_id_prefix`, `nickname`, `grade`, `grade_changed_at`,
     `member_level`, `source`, `snapshot_date`, `source_url`, `source_file`,
     `match_status`, `linked_user_id`, `linked_provider_user_id`)
VALUES
    ('1c0cvkmo2sro', 'jeon', '동글동글', '감사멤버', '2026-10-08',
     150, 'MANUAL', '2026-10-08', NULL, NULL,
     'UNMATCHED', NULL, NULL)
ON DUPLICATE KEY UPDATE
    `naver_id_prefix` = VALUES(`naver_id_prefix`),
    `nickname` = VALUES(`nickname`),
    `grade` = VALUES(`grade`);
