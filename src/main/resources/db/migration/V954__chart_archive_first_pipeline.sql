-- BuildIt Chart 조사 파이프라인을 '정보 아카이브 우선'으로 재편(PR-A, backend).
--
-- eligibility_status 의미 확장(VARCHAR, 스키마 변경 없음 — chart_review_batches.status 와 동일 패턴):
--   EVALUATABLE      = 교육자 식별 + 현장 영어교육 범위 + 교육철학·커리큘럼 서술 가능
--                      → 점수화·TOP100 대상
--   RESEARCH_PENDING = 최소 게이트(개인 식별 / 현장 영어교육 범위 / 공개 근거 URL)는 통과했으나
--                      교육철학·커리큘럼·현재 오프라인 활동 등 평가 근거가 부족
--                      → 공개 검색에는 노출, 점수화·TOP100 대상 아님, 향후 Evidence 보강 대상(폐기 아님)
-- 저장 최소 조건 미달은 REJECTED 로 422(행을 만들지 않음).
--
-- 기존 'ELIGIBLE'(구 Hard Gate 통과 = 교육철학·커리큘럼 보유 + 근거 확보)은 EVALUATABLE 로 승계한다.
-- 현재 공개 중인 50명(Legacy 43 + 분당 5 + 부산 2)은 모두 'ELIGIBLE' 이므로 EVALUATABLE 로 이관되어
-- publication/TOP100 에서 그대로 유지된다.
UPDATE chart_candidates
   SET eligibility_status = 'EVALUATABLE', updated_at = NOW(6)
 WHERE eligibility_status = 'ELIGIBLE';

-- 공개 지역별 검색(PR-B) 대비 인덱스.
ALTER TABLE chart_candidates
  ADD INDEX idx_chart_candidates_search (activity_province, activity_city_district, eligibility_status);
