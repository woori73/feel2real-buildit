-- 중국 공시·뉴스 상세 번역/해설 필드 추가.
-- 원문 링크가 끊겨도 수집·검증한 분석자료가 DB에 보존되도록 한다.
-- detail_kr: ④ 원문 흐름을 따른 상세 한국어 번역·해설 (전문 복제 금지, 해설 형태)
-- key_facts_kr: ⑤ 금액·지분·회사·승인기관·일정 핵심 사실
-- cross_check_kr: ⑥ 한국/중국 공시 대조
-- fact_vs_claim_kr: ⑦ 공시 확인 사실 vs 언론 해석·주장 구분
-- related_refs: 관련 공시 참조
-- link_status/link_checked_at: ⑨ 원문 링크 상태 (OK/BROKEN/BLOCKED/UNKNOWN)

ALTER TABLE wemade_china_disclosures
  ADD COLUMN detail_kr TEXT NULL,
  ADD COLUMN key_facts_kr TEXT NULL,
  ADD COLUMN cross_check_kr TEXT NULL,
  ADD COLUMN fact_vs_claim_kr TEXT NULL,
  ADD COLUMN related_refs VARCHAR(1000) NULL,
  ADD COLUMN link_status VARCHAR(20) NULL DEFAULT 'UNKNOWN',
  ADD COLUMN link_checked_at DATETIME NULL;

ALTER TABLE wemade_china_news
  ADD COLUMN detail_kr TEXT NULL,
  ADD COLUMN key_facts_kr TEXT NULL,
  ADD COLUMN cross_check_kr TEXT NULL,
  ADD COLUMN fact_vs_claim_kr TEXT NULL,
  ADD COLUMN related_refs VARCHAR(1000) NULL,
  ADD COLUMN link_status VARCHAR(20) NULL DEFAULT 'UNKNOWN',
  ADD COLUMN link_checked_at DATETIME NULL;
