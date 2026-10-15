-- ============================================================
-- V910: prd/stg 공통 테스트 계정 추가
--   · 운영 migration에 포함되므로 dev-seed의 student/tutor와 충돌하지 않게
--     login_id를 test-student / test-tutor로 분리한다.
--   · 평문 비밀번호는 저장하지 않는다. BCrypt 해시만 반영한다.
--   · 계정 재주입/비밀번호 회전이 가능하도록 idempotent하게 작성한다.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

SET @test_student_hash := '$2a$10$YCszZhBzRaQzGEW54d.kEO9I78SLI6vsJBYjUe4pvr0jeAyOx7NYC';
SET @test_tutor_hash := '$2a$10$60M5/fs7tmt8hyOXfm.0/OjMCqCbtpEqdnVKrMI8nEZjeiBSdDCSC';

INSERT INTO `users` (`login_id`, `password`, `name`, `email`, `phone`, `role`, `created_by`, `updated_by`)
VALUES
    ('test-student', @test_student_hash, '운영테스트학생', 'test-student@feel2real.test', '010-9000-0001', 'STUDENT', 'V910', 'V910')
ON DUPLICATE KEY UPDATE
    `password` = VALUES(`password`),
    `name` = VALUES(`name`),
    `email` = VALUES(`email`),
    `phone` = VALUES(`phone`),
    `role` = VALUES(`role`),
    `updated_by` = 'V910';

SET @test_student_user_id := (SELECT `user_id` FROM `users` WHERE `login_id` = 'test-student');

INSERT INTO `students` (`user_id`, `school_name`, `grade`, `parent_name`, `parent_phone`, `created_by`, `updated_by`)
VALUES
    (@test_student_user_id, '테스트고등학교', 2, '테스트학부모', '010-9000-1001', 'V910', 'V910')
ON DUPLICATE KEY UPDATE
    `school_name` = VALUES(`school_name`),
    `grade` = VALUES(`grade`),
    `parent_name` = VALUES(`parent_name`),
    `parent_phone` = VALUES(`parent_phone`),
    `updated_by` = 'V910';

INSERT INTO `users` (`login_id`, `password`, `name`, `email`, `phone`, `role`, `created_by`, `updated_by`)
VALUES
    ('test-tutor', @test_tutor_hash, '운영테스트강사', 'test-tutor@feel2real.test', '010-9000-0002', 'TUTOR', 'V910', 'V910')
ON DUPLICATE KEY UPDATE
    `password` = VALUES(`password`),
    `name` = VALUES(`name`),
    `email` = VALUES(`email`),
    `phone` = VALUES(`phone`),
    `role` = VALUES(`role`),
    `updated_by` = 'V910';

SET @test_tutor_user_id := (SELECT `user_id` FROM `users` WHERE `login_id` = 'test-tutor');
SET @test_student_id := (SELECT `student_id` FROM `students` WHERE `user_id` = @test_student_user_id);

INSERT INTO `tutor_student_map` (`tutor_user_id`, `student_id`, `is_active`, `created_by`, `updated_by`)
VALUES
    (@test_tutor_user_id, @test_student_id, TRUE, 'V910', 'V910')
ON DUPLICATE KEY UPDATE
    `is_active` = TRUE,
    `updated_by` = 'V910';
