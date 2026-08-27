-- V951: BuildIt Chart 전국 Living Archive 전환 — 스키마 확장 (PR-A)
--   1) chart_publications: 같은 날/같은 주 여러 지역 발행을 허용 (week_start UNIQUE 제거),
--      자동 발행은 review batch 없이 가능하도록 review_batch_id NULL 허용, scope 컬럼 추가.
--   2) chart_candidates: 발굴 조사 지능을 그대로 상세 프로필에 쓰는 Living Archive 필드 추가.
--   3) 모두 nullable 추가 / 제약 완화이며 chart_publications 는 현재 0건이므로 기존 데이터 무관(1단계).
--   ⚠ V947~V950 은 수정/재번호화하지 않는다.

-- 1) publications: 주간 단일 발행 → 전국 누적/수시 발행
ALTER TABLE chart_publications DROP INDEX uk_chart_publication_week;
ALTER TABLE chart_publications MODIFY COLUMN review_batch_id BIGINT NULL;
ALTER TABLE chart_publications MODIFY COLUMN week_start DATE NULL;
ALTER TABLE chart_publications ADD COLUMN scope VARCHAR(20) NULL AFTER status;
-- current publication 선택 성능: status + published_at 인덱스는 V949(idx_chart_publication_status_date)에 이미 존재.

-- 2) publication entry: TOP100 밖(DISCOVERED)은 순위가 없음
ALTER TABLE chart_publication_entries MODIFY COLUMN chart_rank INT NULL;
-- entry_type 값에 'DISCOVERED' 추가 (VARCHAR 이므로 스키마 변경 없음). 'RANKED' / 'DISCOVERED' / 'PROMISING'.

-- 3) chart_candidates: Living Archive 구조화 필드 (조사 1회 = 상세 프로필 데이터)
ALTER TABLE chart_candidates
  ADD COLUMN education_philosophy_summary TEXT NULL AFTER growth_advice,
  ADD COLUMN curriculum_summary TEXT NULL AFTER education_philosophy_summary,
  ADD COLUMN distinctive_teaching_summary TEXT NULL AFTER curriculum_summary,
  ADD COLUMN recent_activity_summary TEXT NULL AFTER distinctive_teaching_summary,
  ADD COLUMN strengths_json TEXT NULL AFTER recent_activity_summary,
  ADD COLUMN improvement_suggestions_json TEXT NULL AFTER strengths_json,
  ADD COLUMN profile_image_url VARCHAR(1000) NULL AFTER improvement_suggestions_json,
  ADD COLUMN profile_image_source_url VARCHAR(1000) NULL AFTER profile_image_url,
  ADD COLUMN legacy_source VARCHAR(30) NULL AFTER profile_image_source_url,
  ADD COLUMN legacy_metadata_json TEXT NULL AFTER legacy_source,
  ADD COLUMN excluded_from_chart BIT(1) NULL AFTER legacy_metadata_json,
  ADD COLUMN excluded_reason TEXT NULL AFTER excluded_from_chart,
  ADD COLUMN excluded_at DATETIME(6) NULL AFTER excluded_reason;

-- 재조사 주기(RESEARCHED → 90일 뒤 DUE)는 V952(전국 seed)에서 승계 지역에 next_research_at 을 채우고,
-- 신규 조사 완료 지역은 애플리케이션 completeRegion 이 chart.re-research-days(기본 90) 로 세팅한다.
