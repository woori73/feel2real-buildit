-- ============================================================
-- V9000 (dev-seed): 테스트 강사 Academy 블로그 대표 글 1건
--   · 번호 주의: 운영(db/migration)은 순차(현재 V908, 다음 V909+)로 올라간다. dev-seed는 운영 번호선과 안 겹치게 V9000+를 쓴다. (docs/WORKFLOW.md §4)
--   · Academy: V902의 slug='test-english-academy'
--   · 발행(PUBLISHED) + 공개(is_visible) 상태 → 공개 목록/상세/sitemap 노출
--   · 이미지는 바이너리라 SQL 시드 제외. 이미지 업로드는 튜터 화면에서 직접 테스트.
--     (업로드 → /api/tutor/academy/blog-sources/images, webp/png/jpeg 모두 JPEG 재인코딩)
--
-- ⚠ 운영(prd) 환경에는 적용되지 않는다 (spring.flyway.locations 분기).
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

SET @academy_id := (SELECT `academy_id` FROM `academies` WHERE `slug` = 'test-english-academy');

INSERT INTO `academy_blog_posts`
    (`academy_id`, `title`, `slug`, `summary`,
     `content_json`, `content_html`,
     `meta_title`, `meta_description`,
     `status`, `is_visible`, `published_at`)
VALUES
    (@academy_id,
     '고등 영어 내신 1등급, 시험 4주 전부터 이렇게 준비하세요',
     '고등영어-내신-1등급-4주-전략',
     '내신 영어는 범위가 정해져 있는 싸움입니다. 시험 4주 전부터 주차별로 무엇을 해야 1등급이 나오는지, 테스트영어학원의 실제 관리 루틴을 공개합니다.',
     -- Tiptap 에디터가 contentJson으로 본문을 로드하므로(editor.js setContent), 빈 doc이면 수정 화면이 빈다.
     -- 아래 JSON은 content_html과 동일 내용의 Tiptap 문서 구조. (SQL이라 작은따옴표는 '' 로 escape)
     '{"type": "doc", "content": [{"type": "heading", "attrs": {"level": 2}, "content": [{"type": "text", "text": "내신 영어는 범위 안에서 끝내는 싸움입니다"}]}, {"type": "paragraph", "content": [{"type": "text", "text": "안녕하세요, "}, {"type": "text", "text": "테스트영어학원", "marks": [{"type": "bold"}]}, {"type": "text", "text": "입니다. 수능 영어와 달리 내신 영어는 출제 범위가 명확합니다. 그래서 ''얼마나 새로 아느냐''가 아니라 ''범위를 얼마나 빈틈없이 만드느냐''가 등급을 가릅니다."}]}, {"type": "heading", "attrs": {"level": 3}, "content": [{"type": "text", "text": "D-4주차 : 본문을 문장 단위로 분해한다"}]}, {"type": "bulletList", "content": [{"type": "listItem", "content": [{"type": "paragraph", "content": [{"type": "text", "text": "주어·동사를 먼저 찾아 표시한다"}]}]}, {"type": "listItem", "content": [{"type": "paragraph", "content": [{"type": "text", "text": "수식어구(전치사구, 분사구, 관계절)는 괄호로 묶는다"}]}]}, {"type": "listItem", "content": [{"type": "paragraph", "content": [{"type": "text", "text": "각 문장의 우리말 해석을 노트에 직접 쓴다"}]}]}]}, {"type": "blockquote", "content": [{"type": "paragraph", "content": [{"type": "text", "text": "해석은 눈으로 하지 말고 손으로 쓰게 합니다. 눈으로만 보면 아는 것 같지만 시험장에서 무너집니다."}]}]}, {"type": "heading", "attrs": {"level": 3}, "content": [{"type": "text", "text": "D-3주차 : 어법 포인트를 출제자 시선으로 정리한다"}]}, {"type": "bulletList", "content": [{"type": "listItem", "content": [{"type": "paragraph", "content": [{"type": "text", "text": "수일치 (주어-동사, 특히 수식어로 멀어진 경우)"}]}]}, {"type": "listItem", "content": [{"type": "paragraph", "content": [{"type": "text", "text": "능동/수동 (분사, 관계절 동사)"}]}]}, {"type": "listItem", "content": [{"type": "paragraph", "content": [{"type": "text", "text": "병렬 구조 (and/or로 연결된 형태 일치)"}]}]}]}, {"type": "heading", "attrs": {"level": 3}, "content": [{"type": "text", "text": "D-1주차 : 오답 노트만 본다"}]}, {"type": "paragraph", "content": [{"type": "text", "text": "마지막 주에는 새 문제를 풀지 않습니다. 그동안 틀린 것만 다시 봅니다."}]}, {"type": "heading", "attrs": {"level": 2}, "content": [{"type": "text", "text": "혼자 하기 어렵다면"}]}, {"type": "paragraph", "content": [{"type": "text", "text": "테스트영어학원은 매주 화·목 정규반에서 본문 분석부터 서술형 첨삭까지 1:1로 관리합니다. 다음 내신, 같이 1등급 만들어 봐요."}]}]}',
     CONCAT(
       '<h2>내신 영어는 범위 안에서 끝내는 싸움입니다</h2>',
       '<p>안녕하세요, <strong>테스트영어학원</strong>입니다. 수능 영어와 달리 내신 영어는 출제 범위가 명확합니다. ',
       '그래서 ''얼마나 새로 아느냐''가 아니라 ''범위를 얼마나 빈틈없이 만드느냐''가 등급을 가릅니다.</p>',
       '<h3>D-4주차 : 본문을 문장 단위로 분해한다</h3>',
       '<ul><li>주어·동사를 먼저 찾아 표시한다</li>',
       '<li>수식어구(전치사구, 분사구, 관계절)는 괄호로 묶는다</li>',
       '<li>각 문장의 우리말 해석을 노트에 직접 쓴다</li></ul>',
       '<blockquote>해석은 눈으로 하지 말고 손으로 쓰게 합니다. 눈으로만 보면 아는 것 같지만 시험장에서 무너집니다.</blockquote>',
       '<h3>D-3주차 : 어법 포인트를 출제자 시선으로 정리한다</h3>',
       '<ul><li>수일치 (주어-동사, 특히 수식어로 멀어진 경우)</li>',
       '<li>능동/수동 (분사, 관계절 동사)</li>',
       '<li>병렬 구조 (and/or로 연결된 형태 일치)</li></ul>',
       '<h3>D-1주차 : 오답 노트만 본다</h3>',
       '<p>마지막 주에는 새 문제를 풀지 않습니다. 그동안 틀린 것만 다시 봅니다.</p>',
       '<h2>혼자 하기 어렵다면</h2>',
       '<p>테스트영어학원은 매주 화·목 정규반에서 본문 분석부터 서술형 첨삭까지 1:1로 관리합니다. 다음 내신, 같이 1등급 만들어 봐요.</p>'
     ),
     '고등 영어 내신 1등급 4주 전략 | 테스트영어학원',
     '고등 내신 영어 1등급을 위한 시험 4주 전 주차별 학습 플랜. 본문 분석, 어법 포인트, 서술형 대비, 오답 관리까지 정리했습니다.',
     'PUBLISHED', TRUE, NOW());
