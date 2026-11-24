-- 2026-11-24 대표 지시: "중간고사 popup부터 정리하고 기말로 업데이트".
-- F2R 영어 교습소(handle='eduholic') 홈페이지 팝업이 아직 "2026 2학기 중간고사 내신 LIVE"라서
-- 캠페인 이름·팝업 문구를 2학기 기말고사로 바꾼다. 팝업 버튼은 eduholic_midterm_popup.js 에서
-- 메인 사이트 디지털 기말고사(https://feel2real.co.kr/naesintest)로 간다.
--
-- 안전:
--   * 스키마 변경 없음. eduholic 캠페인 1행 UPDATE 뿐이며 handle 이 없으면 아무 행도 바뀌지 않는다.
--   * published·popup_enabled 값은 건드리지 않는다(지금 켜져 있으면 기말 문구로 계속 노출).
--   * 학교 컬렉션·자료·영상 등 다른 캠페인 테이블은 건드리지 않는다.
UPDATE midterm_campaign m
JOIN academies a ON a.academy_id = m.academy_id
SET m.exam_name      = '2026 2학기 기말고사',
    m.exam_type      = '기말고사',
    m.popup_headline = '2026 2학기 기말고사 내신 LIVE',
    m.popup_body     = '압구정중·신구중 학교별 시험 범위에 맞춘 디지털 기말 모의고사를 지금 풀고, 바로 채점·해설로 복습하세요.',
    m.updated_at     = NOW(6),
    m.updated_by     = 'migration-V1144'
WHERE a.handle = 'eduholic';
