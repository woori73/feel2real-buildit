-- ============================================================
-- V902 (dev-seed): 테스트 강사 Academy + Lesson 시드
--   · 강사 계정: V901의 login_id='tutor'
--   · Academy: 테스트영어학원 (slug: test-english-academy)
--   · Lesson 2개: 월정기반 구독형 / 단기특강
--
-- ⚠ 운영(prd) 환경에는 적용되지 않는다.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

SET @tutor_user_id := (SELECT `user_id` FROM `users` WHERE `login_id` = 'tutor');
SET @english_subject_id := (SELECT `subject_id` FROM `subjects` WHERE `name_en` = 'english');

-- Academy
INSERT INTO `academies`
    (`tutor_user_id`, `slug`, `name`, `logo_storage_key`, `cover_storage_key`, `intro`, `status`)
VALUES
    (@tutor_user_id,
     'test-english-academy',
     '테스트영어학원',
     '/storage/dummy/logo.png',
     '/storage/dummy/cover.png',
     '고등학생 내신/수능 영어 전문. 꼼꼼한 1:1 피드백.',
     'PUBLISHED');

SET @academy_id := LAST_INSERT_ID();

-- 과목: 영어
INSERT INTO `academy_subjects` (`academy_id`, `subject_id`)
VALUES (@academy_id, @english_subject_id);

-- 대상: 고등학생
INSERT INTO `academy_target_groups` (`academy_id`, `target_group`)
VALUES (@academy_id, 'HIGH');

-- 지역: 서울 강남구
INSERT INTO `academy_regions` (`academy_id`, `city`, `district`, `display_name`)
VALUES (@academy_id, '서울', '강남구', '서울 강남구');

-- Lesson 1: 월정기 구독형 (주 2회, 월 150,000원)
INSERT INTO `lesson_products`
    (`academy_id`, `name`, `description`, `lesson_type`,
     `price_amount`, `price_unit`, `price_summary`,
     `max_capacity`, `current_count`,
     `schedule_type`, `weekly_count`, `schedule_summary`, `status`)
VALUES
    (@academy_id,
     '고등 영어 내신반',
     '수능/내신 대비 영어 정규반. 매주 화·목 수업.',
     'SUBSCRIPTION',
     150000, 'MONTH', '월 150,000원',
     10, 0,
     'WEEKLY', 2, '매주 화·목 오후 7시',
     'PUBLISHED');

SET @lesson1_id := LAST_INSERT_ID();

INSERT INTO `lesson_weekdays` (`lesson_id`, `weekday`)
VALUES (@lesson1_id, 'TUESDAY'), (@lesson1_id, 'THURSDAY');

-- Lesson 2: 단기특강 (5주 코스, 200,000원)
INSERT INTO `lesson_products`
    (`academy_id`, `name`, `description`, `lesson_type`,
     `price_amount`, `price_unit`, `price_summary`,
     `max_capacity`, `current_count`,
     `schedule_type`, `duration_weeks`, `schedule_summary`, `status`)
VALUES
    (@academy_id,
     '수능 영어 단기특강',
     '수능 영어 집중 5주 코스. 유형별 문제풀이 집중.',
     'SHORT_TERM',
     200000, 'COURSE', '5주 코스 200,000원',
     5, 0,
     'PERIOD', 5, '5주 집중반 (주 3회)',
     'PUBLISHED');

SET @lesson2_id := LAST_INSERT_ID();

INSERT INTO `lesson_weekdays` (`lesson_id`, `weekday`)
VALUES (@lesson2_id, 'MONDAY'), (@lesson2_id, 'WEDNESDAY'), (@lesson2_id, 'FRIDAY');
