-- ============================================================
-- V1050: 김유맘·힘내보자요나 감사회원 명단 수동 추가 (2026-10-09 사용자 지시)
--   · 2026-10-09 네이버 카페에서 감사멤버로 승격 확인 (등급 변경일 2026-10-09).
--   · 기존 명단과 동일 형식 (닉네임 + 네이버ID 앞 4자리).
--   · 회원가입 시 NewStartIdPwSignupService.uniqueMatch(loginId, nickname) →
--     nickname + loginId startsWith prefix + 미연결 1건이면 THANKS_MEMBER 부여.
--   · UNIQUE KEY (snapshot_date, cafe_member_tag) 기준 idempotent.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

INSERT INTO `newstart_thanks_roster`
    (`cafe_member_tag`, `naver_id_prefix`, `nickname`, `grade`, `grade_changed_at`,
     `member_level`, `source`, `snapshot_date`, `source_url`, `source_file`,
     `match_status`, `linked_user_id`, `linked_provider_user_id`)
VALUES
    ('rpe6btg1vtj2', 'boba', '김유맘', '감사멤버', '2026-10-09',
     150, 'MANUAL', '2026-10-09', NULL, NULL,
     'UNMATCHED', NULL, NULL),
    ('85ozt47bdae1', 'kidl', '힘내보자요나', '감사멤버', '2026-10-09',
     150, 'MANUAL', '2026-10-09', NULL, NULL,
     'UNMATCHED', NULL, NULL)
ON DUPLICATE KEY UPDATE
    `naver_id_prefix` = VALUES(`naver_id_prefix`),
    `nickname` = VALUES(`nickname`),
    `grade` = VALUES(`grade`);
