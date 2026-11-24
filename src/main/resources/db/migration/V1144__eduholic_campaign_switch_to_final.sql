-- 2026-11-24 대표 지시
--   "중간고사 popup부터 정리하고 기말로 업데이트"
--   "1. 상호명 교체 사업자 번호 그대로 2. 기말고사로 바꾸고 popup 말고 홈페이지에서 눈에 띄게 3. 지금 새주소"
-- 대상: F2R 영어 교습소 홈페이지(academies.handle = 'eduholic') 한 곳.
--
-- 안전:
--   * 스키마 변경 없음. eduholic 학원 1행과 그 캠페인 1행 UPDATE 뿐. handle 이 없으면 아무 행도 바뀌지 않는다.
--   * 사업자등록번호·대표·전화·이메일은 건드리지 않는다(대표 지시: 사업자 번호 그대로).
--   * 기말 안내는 팝업 대신 홈 첫 화면 배너(eduholic_final_banner.js)로 보여 준다 → 팝업은 끈다.

-- 1) 캠페인: 이름·팝업 문구를 기말로, 팝업은 끈다(배너로 대신).
UPDATE midterm_campaign m
JOIN academies a ON a.academy_id = m.academy_id
SET m.exam_name      = '2026 2학기 기말고사',
    m.exam_type      = '기말고사',
    m.popup_headline = '2026 2학기 기말고사 내신 LIVE',
    m.popup_body     = '압구정중·신구중 학교별 시험 범위에 맞춘 디지털 기말 모의고사를 지금 풀고, 바로 채점·해설로 복습하세요.',
    m.popup_enabled  = b'0',
    m.updated_at     = NOW(6),
    m.updated_by     = 'migration-V1144'
WHERE a.handle = 'eduholic';

-- 2) 학원 이름(상단·제목)과 소개 글 안의 옛 이름.
UPDATE academies
SET name  = 'F2R 영어 교습소',
    intro = REPLACE(REPLACE(intro, '압구정에듀홀릭학원', 'F2R 영어 교습소'), '압구정에듀홀릭', 'F2R 영어 교습소')
WHERE handle = 'eduholic';

-- 3) 홈페이지 문구(page_settings) 안의 옛 이름을 바꾸고, 하단 상호명·주소를 새로.
--    JSON 을 글자로 바꿔 한글 이름만 치환한 뒤 다시 JSON 으로 저장한다(영문 키·URL 은 영향 없음).
UPDATE academies
SET page_settings = CAST(
      REPLACE(REPLACE(REPLACE(
        CAST(page_settings AS CHAR CHARACTER SET utf8mb4),
        '압구정에듀홀릭학원', 'F2R 영어 교습소'),
        '압구정에듀홀릭', 'F2R 영어 교습소'),
        '에듀홀릭', 'F2R 영어 교습소')
      AS JSON)
WHERE handle = 'eduholic' AND page_settings IS NOT NULL;

UPDATE academies
SET page_settings = JSON_SET(page_settings,
      '$.businessInfo.businessName', 'F2R 영어 교습소',
      '$.businessInfo.address', '서울 강남구 언주로173길 12 압구정빌딩 4층')
WHERE handle = 'eduholic' AND JSON_TYPE(JSON_EXTRACT(page_settings, '$.businessInfo')) = 'OBJECT';
