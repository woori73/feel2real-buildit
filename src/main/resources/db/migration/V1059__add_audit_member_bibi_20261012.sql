-- ============================================================
-- V1059: 비비 감사회원 명단 수동 추가 (2026-10-12 사용자 지시)
--   · 2026-10-12 네이버 카페에서 감사멤버 확인 (등급 변경일 2026-10-12, 카페 가입일 2026-10-12).
--   · 기존 명단과 동일 형식 (닉네임 + 네이버ID 앞 4자리).
--   · 회원가입 시 NewStartIdPwSignupService.uniqueMatch(loginId, nickname) →
--     nickname='비비' + loginId startsWith 'sooj' + 미연결 1건이면 THANKS_MEMBER 부여.
--   · 카페 멤버 태그: ufrzeiw6doj1. 등급 변경일 2026-10-12 (카페 화면 확인).
--   · UNIQUE KEY (snapshot_date, cafe_member_tag) 기준 idempotent.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

INSERT INTO `newstart_thanks_roster`
    (`cafe_member_tag`, `naver_id_prefix`, `nickname`, `grade`, `grade_changed_at`,
     `member_level`, `source`, `snapshot_date`, `source_url`, `source_file`,
     `match_status`, `linked_user_id`, `linked_provider_user_id`)
VALUES
    ('ufrzeiw6doj1', 'sooj', '비비', '감사멤버', '2026-10-12',
     150, 'MANUAL', '2026-10-12', NULL, NULL,
     'UNMATCHED', NULL, NULL)
ON DUPLICATE KEY UPDATE
    `naver_id_prefix` = VALUES(`naver_id_prefix`),
    `nickname` = VALUES(`nickname`),
    `grade` = VALUES(`grade`);

