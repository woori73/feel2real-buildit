-- ============================================================
-- V928: 에듀홀릭 시연용 Academy + 전용 tutor 계정 추가
--   · edu-holic.feel2real.co.kr 서브도메인으로 서빙되는 학원 페이지의 데이터.
--   · 페이지 자체는 전용 정적 템플릿(templates/public/academy_edu_holic.html)이라
--     아래 시드는 서브도메인 해석(handle→academy)과 공개 디렉터리 노출에 쓰인다.
--   · 이 Academy는 DemoAcademy.HANDLE로 변경이 차단된다
--     (AuthenticatedUserResolver.verifyOwnsAcademy).
--   · 평문 비밀번호는 저장하지 않는다. BCrypt 해시만 반영한다.
--     평문은 repo 밖 ~/.config/f2r/test-accounts.md 에 보관한다(V910 선례와 동일).
--   · 재적용/비밀번호 회전이 가능하도록 idempotent하게 작성한다.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

SET @edu_holic_hash := '$2a$10$YMU6xt31uCLV.f6MEJL.neV4rY/6SlDUtBUursfl5.XU.swR0F25m';

-- ── tutor 계정 ──
INSERT INTO `users`
    (`login_id`, `password`, `name`, `nickname`, `email`, `phone`, `role`, `created_by`, `updated_by`)
VALUES
    ('edu-holic', @edu_holic_hash, '압구정에듀홀릭', '에듀홀릭', 'edu-holic@feel2real.test',
     '010-9000-0003', 'TUTOR', 'V928', 'V928')
ON DUPLICATE KEY UPDATE
    `password` = VALUES(`password`),
    `name` = VALUES(`name`),
    `nickname` = VALUES(`nickname`),
    `email` = VALUES(`email`),
    `phone` = VALUES(`phone`),
    `role` = VALUES(`role`),
    `updated_by` = 'V928';

SET @edu_holic_user_id := (SELECT `user_id` FROM `users` WHERE `login_id` = 'edu-holic');
SET @english_subject_id := (SELECT `subject_id` FROM `subjects` WHERE `name_en` = 'english');

-- ── Academy ──
-- handle='edu-holic' 이 서브도메인 라우팅 키다(SubdomainAcademyFilter).
-- is_visible=TRUE 이므로 /academies 공개 목록·사이트맵에 노출된다.
-- 목록에서 클릭하면 AcademyPathRedirectInterceptor가 서브도메인으로 301 하므로
-- 사용자에게 보이는 페이지는 항상 전용 템플릿 하나다.
INSERT INTO `academies`
    (`tutor_user_id`, `slug`, `handle`, `name`, `logo_storage_key`, `cover_storage_key`,
     `intro`, `status`, `is_visible`, `created_by`, `updated_by`)
VALUES
    (@edu_holic_user_id,
     'edu-holic-academy',
     'edu-holic',
     '압구정에듀홀릭학원',
     '/storage/sample/academy-logo.png',
     '/storage/sample/academy-cover.png',
     '2008년부터 압구정에서 17년 이상 이어온 1:1 맞춤 영어 교육. Feel to Real Grammar 4레벨 체계.',
     'PUBLISHED', TRUE, 'V928', 'V928')
ON DUPLICATE KEY UPDATE
    `handle` = VALUES(`handle`),
    `name` = VALUES(`name`),
    `logo_storage_key` = VALUES(`logo_storage_key`),
    `cover_storage_key` = VALUES(`cover_storage_key`),
    `intro` = VALUES(`intro`),
    `status` = VALUES(`status`),
    `is_visible` = VALUES(`is_visible`),
    `updated_by` = 'V928';

SET @edu_holic_academy_id := (SELECT `academy_id` FROM `academies` WHERE `slug` = 'edu-holic-academy');

-- ── 과목 / 대상 / 지역 ──
INSERT IGNORE INTO `academy_subjects` (`academy_id`, `subject_id`)
VALUES (@edu_holic_academy_id, @english_subject_id);

INSERT IGNORE INTO `academy_target_groups` (`academy_id`, `target_group`)
VALUES
    (@edu_holic_academy_id, 'ELEMENTARY'),
    (@edu_holic_academy_id, 'MIDDLE'),
    (@edu_holic_academy_id, 'HIGH');

INSERT INTO `academy_regions`
    (`academy_id`, `city`, `district`, `display_name`, `created_by`, `updated_by`)
VALUES
    (@edu_holic_academy_id, '서울', '강남구', '서울 강남구', 'V928', 'V928')
ON DUPLICATE KEY UPDATE
    `display_name` = VALUES(`display_name`),
    `updated_by` = 'V928';
