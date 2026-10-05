-- RENUMBERED: V1022__add_wemade_valuation.sql -> V1032__add_wemade_valuation.sql (Flyway version conflict fix: V1000/V1002 duplicates)
-- V1032: 위메이드 SOTP 가치 재평가 (읽기 전용)
-- wemade_valuation_components / wemade_valuation_scenarios 신규. destructive 변경 없음.
-- NOTE: 지시서의 V1003은 V1003__add_easy_cutover_foundation.sql과 버전 충돌
-- (Flyway 중복 버전 기동 실패) → 최신 번호 V1022 사용.

CREATE TABLE IF NOT EXISTS wemade_valuation_components (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  component_key VARCHAR(100) NOT NULL,
  name_ko VARCHAR(200) NOT NULL,
  category VARCHAR(20) NOT NULL COMMENT 'GAME_IP|LISTED_STAKE|SUBSIDIARY|CASH|DEBT|OPTION',
  value_low_krw BIGINT NULL COMMENT '원 단위',
  value_base_krw BIGINT NULL COMMENT '원 단위, NULL=미산정',
  value_high_krw BIGINT NULL COMMENT '원 단위, NULL=미산정',
  basis VARCHAR(2000) NULL COMMENT '산정 근거 (가정 포함 시 명시)',
  source_name VARCHAR(200) NULL,
  source_url VARCHAR(1000) NULL,
  as_of_date DATE NULL,
  grade CHAR(1) NOT NULL DEFAULT 'U' COMMENT 'A=공식1차, B=복수신뢰, C=언론/증권사, D=계산/커뮤니티, U=미확인',
  content_hash CHAR(64) NOT NULL COMMENT 'sha256(component_key|as_of_date|value_base_krw)',
  collected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wvc_key (component_key),
  UNIQUE KEY uq_wvc_hash (content_hash),
  INDEX idx_wvc_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='위메이드 SOTP 가치평가 구성요소 (시나리오별 low/base/high, 읽기 전용)';

CREATE TABLE IF NOT EXISTS wemade_valuation_scenarios (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scenario_key VARCHAR(20) NOT NULL COMMENT 'CONSERVATIVE|BASE|OPTIMISTIC',
  name_ko VARCHAR(100) NOT NULL,
  description VARCHAR(2000) NULL,
  assumptions_json TEXT NULL COMMENT '시나리오 가정 (JSON)',
  collected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wvs_key (scenario_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SOTP 시나리오 및 공개 가정 (읽기 전용)';

-- 구성요소 시드: 리서치로 확정된 값만. 미확인 항목은 값을 넣지 않는다.
INSERT INTO wemade_valuation_components
  (component_key, name_ko, category, value_low_krw, value_base_krw, value_high_krw,
   basis, source_name, source_url, as_of_date, grade, content_hash)
VALUES
  ('stake_wemademax', '위메이드맥스 지분가치', 'LISTED_STAKE',
   143209813760, 143209813760, 143209813760,
   '위메이드 직접 보유 30,864,184주(36.83%) × 2026-10-02 종가 4,640원. 특별관계자 포함 보유분 60,644,293주(72.37%)는 지배력 지표로 별도 표기하며 지분가치 합산에는 직접 보유분만 반영.',
   'DART 주식등대량보유상황보고서(위메이드맥스, 보고기준일 2026-05-04, 디지털투데이 2026-05-06 인용) + KRX 종가',
   'https://www.digitaltoday.co.kr/news/articleView.html?idxno=662686',
   '2026-10-02', 'A',
   '730b9310e930a8da1090b65d546226e26abbcbffed1fd28f2b45c8b42a92d946'),
  ('mir_ip_royalty', '미르 IP 라이선스 가치', 'GAME_IP',
   250000000000, 500000000000, 750000000000,
   '시나리오 가정 기반 (공식 로열티율 미공개). 보수=연간 경상 로열티 500억원×5년, 기준=연간 1,000억원×5년(리딩투자증권 2026F 라이선스 매출 1,111억원 수준), 낙관=연간 1,500억원×5년(伽马数据 2026E 传奇IP 시장 400亿元量级 + 2025년 한국 법원 80:20 분할 확정 반영). 모두 할인 미적용 단순합.',
   '리딩투자증권 위메이드 리포트(2025-12-09) + 伽马数据 「“传奇”IP生态发展研究报告」(2026-02-25)',
   'https://pdf.dfcfw.com/pdf/H3_AP202602261820053350_1.pdf?17721023340',
   '2026-10-05', 'C',
   'f4aa7f7d3e1826b4224f0456efbacb9fe719cc265478164770f44be7599511c0'),
  ('nightcrows_w_option', 'Night Crows W 신작 옵션', 'OPTION',
   0, 0, NULL,
   '공식 출시일 미공개로 가치 미산정. 위메이드 2026-06-24 신작 게임명 「나이트 크로우W」 확정 발표 (일정 미제시). 원작 누적 매출 약 7,500억원·누적 이용자 1,400만명(위메이드맥스, 2026-04-13)은 참고 지표로만 표시하고 가치 산정에는 미반영. 원작 나이트 크로우 가치는 위메이드맥스 지분가치에 내재되어 있어 별도 가산하지 않음 (이중 계산 방지).',
   '위메이드 공식 발표 (아시아에이 2026-06-24 인용) + 위메이드맥스 (OSEN 2026-04-13 인용)',
   'https://www.asiaa.co.kr/news/articleView.html?idxno=253316',
   '2026-10-05', 'B',
   '023af68051176b3ea643474437420e80c43ad9879c7187e638157aa68d24bec7'),
  ('china_option', '중국사업 옵션가치', 'OPTION',
   0, NULL, NULL,
   '판호 확보(夜鸦, 国新出审〔2026〕1504号, 2026-06-29)는 확정이나, 킹넷 CNINFO 공시(2026-09-12)가 중국 발개위·상무·외환 관련 승인/신고의 불확실성을 명시하고 로열티율이 미공개되어 현금흐름 산정 불가. 보수 시나리오는 0, 기준/낙관은 미산정.',
   '킹넷 CNINFO 「关于公司拟间接参股韩国公司Wemade Co.,Ltd.的公告」(2026-09-12, 东方财富 2026-09-14 인용)',
   'https://finance.eastmoney.com/a/202609143873332476.html',
   '2026-10-05', 'A',
   '5e647b32831be4cdb9324b83de46cc135ea56e51c40555cbd688e021ab229fbf');

INSERT INTO wemade_valuation_scenarios
  (scenario_key, name_ko, description, assumptions_json)
VALUES
  ('CONSERVATIVE', '보수적',
   '확정된 가치만 반영. 미르 IP는 경상 로열티 보수 가정, Night Crows W·중국사업은 0.',
   '{"sotp_includes": ["위메이드맥스 지분가치(확정 1,432억원)", "미르 IP 보수 가정(2,500억원)"], "mir_ip": "연간 경상 로열티 500억원 × 5년 단순합 (할인 미적용, 중국 계약금 제외)", "nightcrows_w": "0 — 공식 출시일 미공개", "china": "0 — 중국 발개위·상무·외환 승인 불확실성 (킹넷 공시)", "excluded": ["위메이드플레이 지분 (2026-04 위믹스코리아 38.28% 해소 후 위메이드 지분율 미확인)", "순현금/부채 (2026-10-02 기준 미확인)", "핵심 게임 사업부 가치 (별도 산정 없음)"], "note": "부분 SOTP. 단일 목표주가를 제시하지 않음."}'),
  ('BASE', '기준',
   '미르 IP 기준 가정(증권사 라이선스 매출 수준) 반영. 그 외 보수적 시나리오와 동일.',
   '{"sotp_includes": ["위메이드맥스 지분가치(확정 1,432억원)", "미르 IP 기준 가정(5,000억원)"], "mir_ip": "연간 1,000억원 × 5년 단순합 (근거: 리딩투자증권 2026F 라이선스 매출 1,111억원)", "nightcrows_w": "0 — 공식 출시일 미공개", "china": "0 — 중국 승인 불확실성", "excluded": ["위메이드플레이 지분 (지분율 미확인)", "순현금/부채 (기준일 미확인)", "핵심 게임 사업부 가치 (별도 산정 없음)"], "note": "부분 SOTP. 단일 목표주가를 제시하지 않음."}'),
  ('OPTIMISTIC', '낙관적',
   '미르 IP 낙관 가정(중국 시장 성장 반영). Night Crows W·중국사업은 여전히 미산정.',
   '{"sotp_includes": ["위메이드맥스 지분가치(확정 1,432억원)", "미르 IP 낙관 가정(7,500억원)"], "mir_ip": "연간 1,500억원 × 5년 단순합 (근거: 伽马数据 2026E 传奇IP 시장 400亿元量级 + 2025년 한국 법원 위메이드:액토즈 80:20 분할 확정)", "nightcrows_w": "미산정 — 공식 출시일 미공개 (원작 누적 7,500억원·이용자 1,400만명은 참고 지표)", "china": "미산정 — 판호 확보(夜鸦 2026-06-29) 확정이나 승인 불확실성", "excluded": ["위메이드플레이 지분 (지분율 미확인)", "순현금/부채 (기준일 미확인)", "핵심 게임 사업부 가치 (별도 산정 없음)"], "note": "부분 SOTP. 단일 목표주가를 제시하지 않음."}');
