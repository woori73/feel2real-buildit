-- BuildIt 2단계: 테넌트 기능 설정 스키마 기준값 이관 (에듀홀릭, handle='eduholic').
--
-- 공개 페이지의 학원별 기능 4종(campaignPopup / featuredCta / liveMode / videoFilter)은
-- Academy.page_settings.builderDraft 의 `tenant.*` 키에 JSON 문자열로 저장된다.
-- 이 마이그레이션은 에듀홀릭의 기존 하드코딩값(eduholic_* JS)을 같은 스키마로 옮겨
-- 기준값(reference)으로 보존한다.
--
-- 중요: enabled=false 로 이관한다. eduholic.feel2real.co.kr 호스트는 기존
-- eduholic_* 스크립트(hostname gate)가 계속 담당하고, tenant_* 스크립트는 해당
-- 호스트에서 실행하지 않으므로 화면·동작에 변화가 없다. 다른 학원은 키 자체가 없어
-- 기본 비활성화 상태를 유지한다.
--
-- 안전:
--   * 스키마 변경 없음. 대상 1행 UPDATE 뿐이며 handle 이 없으면 아무 행도 바뀌지 않는다.
--   * 기존 page_settings(sectionVisibility/sectionOrder/businessInfo/기존 builderDraft 키)는
--     JSON_SET 이 보존한다 — 4개 키를 더할 뿐 삭제·변환하지 않는다.
--   * builderDraft 객체가 없던 경우에도 먼저 빈 객체로 만든 뒤 키를 넣어 경로 누락을 막는다.
--   * 이미 tenant.* 키가 있는 행은 JSON_SET 이 값을 덮어쓰므로, 재실행 시에도 같은
--     기준값으로 수렴한다(멱등).
UPDATE academies
SET page_settings = JSON_SET(
      JSON_SET(
        COALESCE(page_settings, JSON_OBJECT()),
        '$.builderDraft',
        COALESCE(JSON_EXTRACT(page_settings, '$.builderDraft'), JSON_OBJECT())
      ),
      '$."builderDraft"."tenant.campaignPopup"',
      '{"enabled":false,"landingPath":"/eduholic-midterm-2026.html","dismissKey":"eduholic-midterm-2026-dismissed-date","kicker":"MIDTERM LIVE","headline":"학교별 중간고사 준비, 지금 진행 중입니다.","body":"학교 프린트 분석부터 학습자료·변형문제·시험 직전 대비까지. 박정민 원장이 실제 준비 과정을 보여드립니다.","ctaLabel":"준비 현장 보기 →","laterLabel":"오늘은 그만 보기","ariaLabel":"2026 2학기 내신 LIVE 안내"}',
      '$."builderDraft"."tenant.featuredCta"',
      '{"enabled":false,"label":"2026 중간고사 LIVE","landingPath":"/eduholic-midterm-2026.html","ariaLabel":"2026 중간고사 대비 현장 보기 — 압구정에듀홀릭 내신 LIVE"}',
      '$."builderDraft"."tenant.liveMode"',
      '{"enabled":false,"videoSrc":"/video/eduholic/shingu2-jikbo-explainer.mp4","videoTitle":"신구중2 2학기 중간고사(9월30일) 대비 직보 자료","videoDesc":"내일(9월30일) 시험 전 마지막으로 확인하는 영어 문제풀이 · 핵심 해설","videoNotes":["※ AI 한국어 선생님이 영어 발음은 살짝 서툴 수 있어요. 😅","가끔 귀여운(?) 발음이 나오더라도 너그럽게 이해해 주시고,","문제 풀이와 핵심 해설에 집중해 주세요!"],"landingPath":"/eduholic-midterm-2026.html","headingEyebrow":"내신 LIVE","headingTitle":"2학기 영어 내신 LIVE","headingDesc":"학교별 실제 자료를 읽고, 학교에 맞는 예상문제를 만듭니다.","sceneIntervalMs":4500,"schoolCardKindLabel":"학교별 준비 LIVE","filterMarker":"내신대비_라이브_최종_53초","liveVideosApi":"/api/academies/{slug}/midterm-campaign/live-videos","showcaseApi":"/api/academies/{slug}/midterm-campaign/showcase"}',
      '$."builderDraft"."tenant.videoFilter"',
      '{"enabled":false,"marker":"내신대비_라이브_최종_53초"}'
    )
WHERE handle = 'eduholic';
