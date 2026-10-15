-- ============================================================
-- V994: users.role ENUM에 CREATOR 추가
--   · CHOONGGI 전용 콘텐츠 운영자 role. GLOBAL ADMIN이 아니다.
--   · 기존 값(STUDENT/ADMIN/TUTOR)과 기존 DEFAULT 'STUDENT'는 그대로 유지한다.
--   · 이 migration은 스키마 변경만 한다 — 기존 user row의 role 값은 건드리지 않는다.
--     production `choonggi` 계정(login_id='choonggi')의 role은 이 배포 이후 별도
--     one-time UPDATE로 수행한다(코드 배포와 데이터 변경을 분리 — 배포 전에 role을
--     바꾸면 구버전 JVM이 인식 못하는 값이 되고, 반대로 순서를 지키면 안전하다).
-- ============================================================
ALTER TABLE `users`
  MODIFY COLUMN `role` ENUM('STUDENT', 'ADMIN', 'TUTOR', 'CREATOR') NOT NULL DEFAULT 'STUDENT';
