-- ============================================================
-- V913: prd/stg 공통 샘플 Academy 블로그 글 추가
--   · /academy-posts 테스트를 위해 공개 Academy + 공개 블로그 글 1건을 준비한다.
--   · V910의 test-tutor 계정을 재사용한다.
--   · 테스트 데이터이지만 stg/prd 공통 확인이 필요해 db/migration 라인에 둔다.
--   · 재적용/비밀번호 회전/데이터 보정이 가능하도록 idempotent하게 작성한다.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

SET @test_tutor_user_id := (SELECT `user_id` FROM `users` WHERE `login_id` = 'test-tutor');
SET @english_subject_id := (SELECT `subject_id` FROM `subjects` WHERE `name_en` = 'english');

INSERT INTO `academies`
    (`tutor_user_id`, `slug`, `name`, `logo_storage_key`, `cover_storage_key`, `intro`, `status`, `is_visible`, `created_by`, `updated_by`)
VALUES
    (@test_tutor_user_id,
     'sample-english-academy',
     '샘플 영어학원',
     '/storage/sample/academy-logo.png',
     '/storage/sample/academy-cover.png',
     '학교별 내신 영어 분석과 주차별 학습 전략을 제공하는 샘플 Academy입니다.',
     'PUBLISHED', TRUE, 'V913', 'V913')
ON DUPLICATE KEY UPDATE
    `slug` = VALUES(`slug`),
    `name` = VALUES(`name`),
    `logo_storage_key` = VALUES(`logo_storage_key`),
    `cover_storage_key` = VALUES(`cover_storage_key`),
    `intro` = VALUES(`intro`),
    `status` = VALUES(`status`),
    `is_visible` = VALUES(`is_visible`),
    `updated_by` = 'V913';

SET @sample_academy_id := (SELECT `academy_id` FROM `academies` WHERE `slug` = 'sample-english-academy');

INSERT IGNORE INTO `academy_subjects` (`academy_id`, `subject_id`)
VALUES (@sample_academy_id, @english_subject_id);

INSERT IGNORE INTO `academy_target_groups` (`academy_id`, `target_group`)
VALUES (@sample_academy_id, 'MIDDLE'), (@sample_academy_id, 'HIGH');

INSERT INTO `academy_regions` (`academy_id`, `city`, `district`, `display_name`, `created_by`, `updated_by`)
VALUES (@sample_academy_id, '서울', '강남구', '서울 강남구', 'V913', 'V913')
ON DUPLICATE KEY UPDATE
    `display_name` = VALUES(`display_name`),
    `updated_by` = 'V913';

INSERT INTO `academy_blog_posts`
    (`academy_id`, `title`, `slug`, `summary`,
     `content_json`, `content_html`,
     `meta_title`, `meta_description`,
     `status`, `is_visible`, `published_at`, `created_by`, `updated_by`)
VALUES
    (@sample_academy_id,
     '중간고사 4주 전, 영어 내신을 점수로 바꾸는 준비법',
     '중간고사-4주전-영어내신-준비법',
     '시험 범위가 확정된 뒤 4주 동안 본문 분석, 어법 정리, 서술형 대비, 오답 회고를 어떤 순서로 진행하면 좋은지 정리했습니다.',
     '{"type":"doc","content":[{"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"4주 전부터는 공부량보다 순서가 중요합니다"}]},{"type":"paragraph","content":[{"type":"text","text":"영어 내신은 범위가 정해진 시험입니다. 그래서 막연히 많이 푸는 것보다 본문 분석, 어법 포인트, 서술형 예상, 오답 회고를 순서대로 쌓는 것이 중요합니다."}]},{"type":"heading","attrs":{"level":3},"content":[{"type":"text","text":"D-4주: 본문 구조를 먼저 잡기"}]},{"type":"paragraph","content":[{"type":"text","text":"각 지문의 주제문, 연결어, 지시어를 표시하고 문장별 해석 빈틈을 줄입니다."}]},{"type":"heading","attrs":{"level":3},"content":[{"type":"text","text":"D-2주: 학교별 출제 포인트 점검"}]},{"type":"paragraph","content":[{"type":"text","text":"선생님이 강조한 표현, 변형 가능한 문장, 서술형으로 바뀔 수 있는 구문을 따로 정리합니다."}]},{"type":"heading","attrs":{"level":3},"content":[{"type":"text","text":"마지막 3일: 틀린 것만 다시 보기"}]},{"type":"paragraph","content":[{"type":"text","text":"새 문제보다 이전 오답의 이유를 설명할 수 있는지 확인하는 것이 마지막 점수 상승에 더 효과적입니다."}]}]}',
     CONCAT(
       '<h2>4주 전부터는 공부량보다 순서가 중요합니다</h2>',
       '<p>영어 내신은 범위가 정해진 시험입니다. 그래서 막연히 많이 푸는 것보다 ',
       '본문 분석, 어법 포인트, 서술형 예상, 오답 회고를 순서대로 쌓는 것이 중요합니다.</p>',
       '<h3>D-4주: 본문 구조를 먼저 잡기</h3>',
       '<p>각 지문의 주제문, 연결어, 지시어를 표시하고 문장별 해석 빈틈을 줄입니다.</p>',
       '<h3>D-2주: 학교별 출제 포인트 점검</h3>',
       '<p>선생님이 강조한 표현, 변형 가능한 문장, 서술형으로 바뀔 수 있는 구문을 따로 정리합니다.</p>',
       '<h3>마지막 3일: 틀린 것만 다시 보기</h3>',
       '<p>새 문제보다 이전 오답의 이유를 설명할 수 있는지 확인하는 것이 마지막 점수 상승에 더 효과적입니다.</p>'
     ),
     '중간고사 4주 전 영어 내신 준비법 | 샘플 영어학원',
     '중간고사 4주 전부터 영어 내신을 준비하는 순서와 학교별 출제 포인트 정리법을 소개합니다.',
     'PUBLISHED', TRUE, NOW(), 'V913', 'V913')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`),
    `summary` = VALUES(`summary`),
    `content_json` = VALUES(`content_json`),
    `content_html` = VALUES(`content_html`),
    `meta_title` = VALUES(`meta_title`),
    `meta_description` = VALUES(`meta_description`),
    `status` = VALUES(`status`),
    `is_visible` = VALUES(`is_visible`),
    `published_at` = COALESCE(`published_at`, VALUES(`published_at`)),
    `updated_by` = 'V913';
