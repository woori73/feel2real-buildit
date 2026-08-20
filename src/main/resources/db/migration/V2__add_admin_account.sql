-- ============================================================
-- V2: 운영용 admin 계정 추가
--   · login_id: admin-f2r
--   · password: admin-mochi-96  (BCrypt cost 10, $2y$ — Spring BCryptPasswordEncoder 호환)
--   · role: ADMIN
--
-- 해시는 한 번 생성한 값이라 평문으로 환원 불가. 비밀번호를 바꿔야 하면 새 해시를 만들고
-- 새 V_파일(예: V3__rotate_admin_password.sql)을 추가해 UPDATE한다. 본 파일 수정 금지 (Flyway 체크섬).
--
-- 비밀번호 회귀 방지: AdminPasswordHashTest가 본 해시와 평문 매칭을 검증.
--
-- ⚠ dev-seed의 admin/admin1234와는 별개의 계정. dev-seed는 운영에 적용되지 않으므로
--   운영에는 본 V2의 admin-f2r만 존재한다.
-- ============================================================
INSERT INTO `users` (`login_id`, `password`, `name`, `phone`, `role`) VALUES
    ('admin-f2r',
     '$2y$10$b0GANweD8ce2KtqHWbV1Cuvgs6S8Ks6kqhLVTOyNLl9ivR/7Iq4.a',
     'F2R 관리자',
     NULL,
     'ADMIN');
