-- ============================================================
-- V901 (dev-seed): 테스트 강사 시드 + 학생 매핑
--   · 로그인 ID: tutor / 비밀번호: admin1234 (V900 student와 동일 BCrypt 해시 재사용)
--   · V900의 학생(login_id='student')과 자동 매핑 (tutor_student_map)
--
-- ⚠ 운영(prd) 환경에는 적용되지 않는다 (spring.flyway.locations 분기).
-- ⚠ V900과 분리 이유: V900은 STUDENT 시드 + 시계열 더미 데이터로 컨텍스트 일관.
--   V901은 Phase 2(§5.5)에서 도입된 TUTOR 시드라 별도 파일로 분리.
-- ============================================================

INSERT INTO `users` (`login_id`, `password`, `name`, `nickname`, `email`, `phone`, `role`) VALUES
    ('tutor', '$2a$10$b1zH0Y4x2dC7YMdGjrWCzuSF4gRpiVnnt2B1s3jA2HPBP3xrvc9Di',
     '테스트강사', 'tutor', 'test_tutor@example.com', '010-0000-0001', 'TUTOR');

-- V900의 학생(login_id='student')과 위 강사를 매핑
INSERT INTO `tutor_student_map` (`tutor_user_id`, `student_id`, `is_active`)
SELECT
    (SELECT `user_id` FROM `users` WHERE `login_id` = 'tutor'),
    (SELECT s.`student_id` FROM `students` s
       JOIN `users` u ON s.`user_id` = u.`user_id`
       WHERE u.`login_id` = 'student'),
    TRUE;
