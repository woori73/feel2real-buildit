-- PHASE 3-B: Eduholic(압구정에듀홀릭, handle='eduholic') 한 곳에만 Horizontal Content Collection 을 켠다.
--
-- 렌더 판정은 프런트(academy_sections.js)가 builderDraft.lessonsLayout / videosLayout == 'horizontal'
-- 로 한다. 이 두 키는 opt-in 이라 값이 없는 다른 학원 사이트는 기존 레이아웃(showcase / 재생목록)을
-- 그대로 유지한다. 여기서는 handle='eduholic' 한 행의 page_settings JSON 에 두 키만 추가한다.
--
-- 안전:
--   * 스키마 변경 없음. 대상 1행 UPDATE 뿐이며 handle 이 없으면 아무 행도 바뀌지 않는다.
--   * 기존 page_settings(sectionVisibility/sectionOrder/businessInfo/기존 builderDraft 38키)는
--     JSON_SET 이 보존한다 — 두 키를 더할 뿐 삭제·변환하지 않는다.
--   * builderDraft 객체가 없던 경우에도 먼저 빈 객체로 만든 뒤 키를 넣어 경로 누락을 막는다.
UPDATE academies
SET page_settings = JSON_SET(
      JSON_SET(
        COALESCE(page_settings, JSON_OBJECT()),
        '$.builderDraft',
        COALESCE(JSON_EXTRACT(page_settings, '$.builderDraft'), JSON_OBJECT())
      ),
      '$.builderDraft.lessonsLayout', 'horizontal',
      '$.builderDraft.videosLayout', 'horizontal'
    )
WHERE handle = 'eduholic';
