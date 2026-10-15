-- ============================================================
-- V1049: NewStart 지피지기 DB — 2026-10-08 신복위 카페 주택 재산평가 사례 반영
-- 입력: 2026-10-08 신복위 공식카페(ccrs5500) 조사 보고서 (2026-01-01~2026-10-08)
-- 원칙:
-- · authority를 'OFFICIAL'/'공식'으로 승격하지 않음 (authority: '카페 사례 조사' / '카페 자료 인용')
-- · 개인정보·닉네임 절대 포함 금지 (INSERT 시 닉네임 제거)
-- · "2026년 중간에 공시가격 중심에서 KB시세·감정가 중심으로 변경됐다"는 가설은
--   확인되지 않았으므로 단정적 표현 금지 → "확인되지 않음"으로 기록
-- · 감정평가액이 실제 적용된 사례는 2026년 조사에서 0건
-- · NEW-20261008-06은 잘못된 정보(창원시 7,700만원)를 바로잡는 정정 자료
--
-- 항목:
-- NEW-20261008-01 → NEW (CASE, ACTIVE): 시세 우선순위 KB시세 > 한국부동산원 > 실거래가/공시지가
-- NEW-20261008-02 → NEW (CASE, ACTIVE): 재산평가액 공식 (재산시세 - 담보대출 - 공제재산가액)
-- NEW-20261008-03 → NEW (CASE, ACTIVE): 공동명의 계산 순서 (시세-담보대출)×지분율-공제
-- NEW-20261008-04 → NEW (CASE, ACTIVE): 배우자 단독명의 (시세-대출잔액)의 50% 반영
-- NEW-20261008-05 → NEW (CASE, ACTIVE): 지역공제 = 지역별 최우선변제금, 3종류 중 최대값 하나
-- NEW-20261008-06 → NEW (CASE, ACTIVE): [정정] 창원시 7,700만원은 잘못된 정보
-- NEW-20261008-07 → NEW (CASE, ACTIVE): 단순 차감액보다 3~4천만원 적게 나오는 패턴 (원인 미확인)
-- NEW-20261008-08 → NEW (CASE, ACTIVE): 임시안 단계 미반영 후 소명으로 조정되는 흐름
-- NEW-20261008-09 → NEW (RULE, ACTIVE): 신복위 채무조정 공제재산가액 공식표 (신속/프리/워크)
--
-- dedup_key는 UNIQUE이므로 재실행은 ON DUPLICATE KEY UPDATE로 멱등 처리.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ------------------------------------------------------------
-- NEW-20261008-01 (NEW / CASE / ACTIVE)
-- 주택 시세 평가 우선순위 — KB시세 > 한국부동산원 > 실거래가/공시지가
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('CASE', '기타',
'주택 시세 평가 우선순위 — KB시세 > 한국부동산원 > 실거래가/공시지가',
'2026년 1~10월 카페 사례 조사에서 주택 시세 평가의 우선순위가 KB시세 → 한국부동산원 → 국토교통부 실거래가/개별공시지가액/개별주택가격 순으로 일관되게 확인됨. "2026년 중간에 공시가격 중심에서 KB시세·감정가 중심으로 변경됐다"는 가설은 확인되지 않았으며, 감정평가액이 실제 적용된 사례는 조사에서 0건.',
'\n- 카페 상담 형식 답변: KB시세 기준으로 보고, 안 될 경우 한국부동산원 → 국토교통부 실거래가 또는 개별공시지가액·개별주택가격 순으로 반영\n- 캠코 담당자 전달 글: 부실차주 새출발기금은 한국부동산원 부동산테크 시세로 본다는 언급\n- 2026년 1월부터 10월까지 KB시세 → 실거래가 → 공시지가(공시가격) 순의 우선순위가 반복 확인됨\n- KB시세가 없는 빌라·아파트는 실거래가, 실거래가도 없으면 공시지가를 보는 흐름\n\n\n- "2026년 중간에 공시가격 중심에서 KB시세·감정가 중심으로 바뀌었다"는 가설은 확인되지 않음\n- 감정평가액이 실제 적용된 사례는 2026년 조사에서 0건\n- 6월경 내부 산정기준 변화 정황이 언급된 글은 있으나(임시안 60%→체결 시 35% 조정 언급), 시세 출처 변경의 증거는 아님\n\n※ 이 항목은 카페 사례 조사에 기반한 정리이며, 공식 규정 문구가 아닙니다.',
'카페 사례 조사',
'PROPERTY_VALUATION,MARKET_PRICE,PUBLIC_PRICE,APPRAISAL',
'https://cafe.naver.com/ccrs5500/550975', '2026-10-08', '2026-10-08', NULL,
1, 'ACTIVE', 'MEDIUM', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261008-01')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_url` = VALUES(`source_url`),
`source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261008-02 (NEW / CASE / ACTIVE)
-- 재산평가액 공식 — 재산시세 - 담보대출/임차보증금 - 공제재산가액
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('CASE', '기타',
'재산평가액 공식 — 재산시세에서 담보대출과 공제재산가액을 차감',
'카페 FAQ에서 확인된 재산평가액 공식: 재산시세 - 담보대출/임차보증금 - 공제재산가액. 이 금액이 총채무보다 적어야 한다는 조건이 함께 명시됨. 1월 실제 사례에서 KB시세 4억4천만원 - 주담대 3억6천4백만원 = 청산가치 7,600만원으로 정확히 일치.',
'\n- 카페 FAQ: 재산평가액 = 재산시세 - 담보대출/임차보증금 - 공제재산가액\n- 이 재산평가액이 총채무보다 적어야 한다는 조건이 함께 명시됨\n- 1월 실제 사례: KB시세 4억4천만원 - 주담대 3억6천4백만원 = 청산가치 7,600만원으로 정확히 일치\n- 여러 후기에서 "시세에서 담보대출 잔액을 뺀다"는 표현이 반복됨\n\n\n- 근저당 설정액과 실제 대출잔액 중 무엇을 차감하는지는 확정적 답변이 확인되지 않음\n- 관련 질문은 있으나 "이것을 쓴다"는 확정 답변이 없음\n- 상담 시 차감 기준을 단정하지 말고 확인이 필요하다고 안내\n\n※ 이 항목은 카페 사례 조사에 기반한 정리이며, 공식 규정 문구가 아닙니다.',
'카페 사례 조사',
'PROPERTY_VALUATION,SECURED_DEBT,MORTGAGE_HOUSE',
'https://cafe.naver.com/ccrs5500/774152', '2026-10-08', '2026-10-08', NULL,
1, 'ACTIVE', 'MEDIUM', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261008-02')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_url` = VALUES(`source_url`),
`source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261008-03 (NEW / CASE / ACTIVE)
-- 공동명의 주택 — (시세 - 담보대출) × 지분율 - 공제 순서로 계산
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('CASE', '기타',
'공동명의 주택 — (시세 - 담보대출) × 지분율 - 공제 순서로 계산',
'2월 세종시 사례에서 "KB시세 - 담보대출 = 금액(50% 내 지분) - 공제재산가액" 계산식이 확인됨. 5월 부부 공동명의 아파트 사례(KB시세 약 4.2억원, 지분 기준 약 2.1억원)도 같은 이해를 반영. 대체로 시세의 지분만큼만 본인 재산으로 봄.',
'\n- 2월 세종시 사례: KB시세 - 담보대출 = 금액(50% 내 지분) - 공제재산가액 계산식 제시\n- 5월 사례: 부부 공동명의 아파트 KB시세 약 4.2억원, 지분 기준 약 2.1억원으로 기재\n- 10월 질문글에도 "공동명의니까 시세의 반만 제 재산으로 보는 건가요"라는 같은 이해가 반영됨\n\n\n- 담보채무를 먼저 빼고 지분율을 적용하는 순서의 사례는 확인됨\n- 반대로 지분율을 먼저 적용하고 해당 채무를 차감하는 순서의 공식 확인은 없음\n- 상담 시 계산 순서를 단정하지 말고 실제 임시안·확정안 수치로 확인할 것을 안내\n\n※ 이 항목은 카페 사례 조사에 기반한 정리이며, 공식 규정 문구가 아닙니다.',
'카페 사례 조사',
'PROPERTY_VALUATION,JOINT_OWNERSHIP',
NULL, '2026-10-08', '2026-10-08', NULL,
1, 'ACTIVE', 'MEDIUM', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261008-03')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`),
`source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261008-04 (NEW / CASE / ACTIVE)
-- 배우자 단독명의 주택 — (시세 - 대출잔액)의 50%를 본인 재산으로 반영
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('CASE', '기타',
'배우자 단독명의 주택 — (시세 - 대출잔액)의 50%를 본인 재산으로 반영',
'4월·5월 글에서 배우자 재산의 절반을 신청인 재산으로 평가한다고 확인. "(시세 - 잔액)의 절반을 채무자의 재산으로 보고, 여기서 지역과 가구원수에 따른 공제재산가액까지 차감"한다는 설명. 9월 개인워크아웃 체결 후기에서 배우자 명의 아파트가 심사에 포함되어 변제 조건이 체결됨. 7월에는 배우자 명의 재산 때문에 개인워크아웃 접수가 거절된 사례도 있어 심사 엄격화 흐름.',
'\n- 4월 글: (시세 - 잔액)의 절반을 채무자의 재산으로 보고 공제재산가액까지 차감\n- 5월 글: 배우자 재산의 절반 가량을 신청인의 재산으로 평가\n- 9월 개인워크아웃 체결 후기: 배우자 명의 아파트(본인 기재 2억2천만원, 선순위채무 4천만원) 포함, 총채무 1억2,900만원, 96개월 × 40만8천원 = 3,920만원 변제로 체결\n- 7월 사례: 배우자 명의 재산 때문에 개인워크아웃 접수가 거절됐다는 글(댓글 51개)\n- 10월 신속 신청 글: 배우자 명의 아파트 KB LAND 기준 2억9천만원, 부채 7,100만원 상담 중\n\n\n- 새출발기금과 개인워크아웃 모두 배우자 재산 50% 반영\n- 개인워크아웃에서 배우자 재산 조회가 더 엄격해지는 흐름이 보임\n- 신속채무조정에서는 배우자 재산 확인이 상대적으로 느슨했다는 증언도 있음\n\n※ 이 항목은 카페 사례 조사에 기반한 정리이며, 공식 규정 문구가 아닙니다.',
'카페 사례 조사',
'PROPERTY_VALUATION,PERSONAL_WORKOUT,JOINT_OWNERSHIP',
'https://cafe.naver.com/ccrs5500/794967', '2026-10-08', '2026-10-08', NULL,
1, 'ACTIVE', 'MEDIUM', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261008-04')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_url` = VALUES(`source_url`),
`source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261008-05 (NEW / CASE / ACTIVE)
-- 지역공제(재산공제) — 지역별 최우선변제금, 3종류 중 최대값 하나 적용
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('CASE', '기타',
'지역공제(재산공제) — 지역별 최우선변제금 기준, 3종류 중 최대값 하나 적용',
'당일 90% 확정 통화를 받은 적용자의 설명에 따르면 재산공제 3종류(거주지 지역별 최우선변제금/상가 최우선변제금/가구원수 생계비) 중 가장 큰 금액 하나를 공제하며, 거주지 지역공제는 "지역별 최우선변제금" 기준. 확인된 티어: 서울 5,500만원 / 부천·안양(과밀억제권역) 4,800만원 / 지역광역시 2,800만원 / 지방·광역시·경기도(일반) 2,500만원. 주의: 상담 담당자 계산으로 확인된 사례는 1건이며 나머지는 회원 전제·질문 수준.',
'\n- 확정 적용자 설명: 재산공제 3종류(거주지 지역별 최우선변제금/상가 최우선변제금/가구원수 생계비) 중 가장 큰 금액 하나를 공제\n- 거주지 지역공제는 "지역별 최우선변제금" 기준\n- 확인된 티어: 서울 5,500만원 / 부천·안양(과밀억제권역) 4,800만원 / 지역광역시 2,800만원 / 지방·광역시·경기도(일반) 2,500만원\n- 상담 담당자 계산 적용 확인 사례: 전세 6,000만원 - 전세대출 2,000만원 - 지역공제 2,800만원 = 재산 1,200만원\n\n\n- 위 금액들은 회원 전제·질문 수준이 대부분이며, 임시안·확정안 수치로 확인된 사례는 상담 계산 적용 1건이 유일\n- 안양시 4,800만원의 정확한 성격(어떤 지역 구분인지)과 적용조건(가구원수·거주요건 등)에 대한 공식 문서는 확인되지 않음\n- 부천도 동일액이라 경기 과밀권역 티어로 추정될 뿐임\n- "창원시 7,700만원"은 잘못된 정보이므로 별도 정정 항목(NEW-20261008-06) 참조\n\n※ 이 항목은 카페 사례 조사에 기반한 정리이며, 공식 규정 문구가 아닙니다.',
'카페 사례 조사',
'PROPERTY_VALUATION,REGIONAL_DEDUCTION',
NULL, '2026-10-08', '2026-10-08', NULL,
1, 'ACTIVE', 'MEDIUM', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261008-05')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`),
`source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261008-06 (NEW / CASE / ACTIVE)
-- [정정] 창원시 지역공제 7,700만원은 잘못된 정보
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('CASE', '기타',
'[정정] 창원시 지역공제 7,700만원은 잘못된 정보',
'해당 글은 일반회원의 질문글로 "창원시 기준 재산가액 7700만원"은 회원 본인의 전제 수치이지 실제 적용된 공제액이 아님. 공식 공제재산가액표상 창원시(기타 지역)는 3인 7,500만원 / 2인 이하 6,000만원 / 4인 이상 9,000만원으로 7,700만원은 표에 존재하지 않음. 새출발기금 지역공제(최우선변제금 기준)로 봐도 기타 지역은 2,500만원. 이 항목은 잘못된 정보를 바로잡는 정정 자료.',
'\n- 해당 글은 일반회원의 "사전채무조정 답변 부탁" 질문글로, 운영자·상담사 글이 아님\n- "창원시 기준 재산가액 7700만원"은 회원이 쓴 본인의 전제 수치이지 실제 적용된 공제액이 아님\n- 상담 계정의 댓글도 7,700만원을 확인해주지 않음\n- 공식 공제재산가액표상 창원시(기타 지역): 3인 7,500만원 / 2인 이하 6,000만원 / 4인 이상 9,000만원 — 7,700만원은 표에 존재하지 않음\n- 새출발기금 지역공제(최우선변제금 기준)로 봐도 기타 지역은 2,500만원\n- 해당 글은 ''지역공제''가 아니라 ''공제재산가액''에 대한 질문이었음\n\n\n- 이 항목은 잘못된 정보를 바로잡는 정정 자료로 등록\n- 상담 시 창원시 7,700만원을 공제액으로 안내하지 않음\n\n※ 이 항목은 카페 사례 조사에 기반한 정정 기록이며, 공식 규정 문구가 아닙니다.',
'카페 사례 조사',
'PROPERTY_VALUATION,REGIONAL_DEDUCTION,CORRECTION',
'https://cafe.naver.com/ccrs5500/771781', '2026-10-08', '2026-10-08', NULL,
1, 'ACTIVE', 'MEDIUM', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261008-06')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_url` = VALUES(`source_url`),
`source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261008-07 (NEW / CASE / ACTIVE)
-- 단순 차감액보다 최종 인정 재산가액이 3~4천만원 적게 나오는 패턴 (원인 미확인)
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('CASE', '기타',
'단순 차감액보다 최종 인정 재산가액이 3~4천만원 적게 나오는 패턴 (원인 미확인)',
'8월 확정안 상담 사례: 아파트 시세 4억1,800만원 - 선순위채무 3억500만원 = 1억1,300만원인데 최종 인정 순재산가액 7,800만원. 3,500만원의 차액이 발생하며 현재 원인 미확인. 8월 댓글 사례: 주택시세 3억7,000만원 - 선순위채무 2억5,000만원 = 1억2,000만원인데 최종 인정 8,000만원. 4,000만원의 차액이 발생하며 현재 원인 미확인. 글쓴이는 "지역공제 빼고 뭐 빼고 하니 재산가액이 7,800만원"이라고 적어 지역공제 외 추가 조정 요인을 시사. 억지로 맞추지 말고 차액 미확인으로 기록.',
'\n- 8월 확정안 상담: 아파트 시세 4억1,800만원, 선순위채무 3억500만원, 최종 인정 순재산가액 7,800만원\n- 단순 차감(4억1,800 - 3억500 = 1억1,300만원)과 비교하면 3,500만원의 차액이 발생하며 현재 원인 미확인\n- 8월 댓글 사례: 주택시세 3억7,000만원, 선순위채무 2억5,000만원, 최종 인정 순재산가액 8,000만원\n- 단순 차감(3억7,000 - 2억5,000 = 1억2,000만원)과 비교하면 4,000만원의 차액이 발생하며 현재 원인 미확인\n- 글쓴이는 "지역공제 빼고 뭐 빼고 하니 재산가액이 7,800만원"이라고 적어 지역공제 외 추가 조정 요인을 시사\n- 무담보채무 9,000만원에 감면율 75%가 적용된 사례\n\n\n- 단순 차감액보다 최종 인정액이 3,000만~4,000만원가량 적게 나오는 패턴이 여러 사례에서 반복됨\n- 정확한 내역은 글쓴이들도 파악하지 못하는 경우가 많았음\n- 역산이 맞지 않으면 억지로 맞추지 말고 "○○만원의 차액이 발생하며 현재 원인 미확인"으로 기록\n\n※ 이 항목은 카페 사례 조사에 기반한 정리이며, 공식 규정 문구가 아닙니다.',
'카페 사례 조사',
'PROPERTY_VALUATION,FINAL_PLAN',
'https://cafe.naver.com/ccrs5500/779250', '2026-10-08', '2026-10-08', NULL,
1, 'ACTIVE', 'MEDIUM', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261008-07')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_url` = VALUES(`source_url`),
`source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261008-08 (NEW / CASE / ACTIVE)
-- 임시안 단계 담보대출·지역공제 미반영 후 소명으로 조정되는 흐름
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('CASE', '기타',
'임시안 단계 담보대출·지역공제 미반영 후 소명으로 조정되는 흐름',
'9월 임시안 사례에서 "주택담보대출 1억을 재산가액에 다 넣어버린 듯"이라는 문제 제기. 임시안에서 담보대출·지역공제 미반영으로 감면율 0%나 낮은 조정액이 나왔다가 소명·자료 제출 후 조정되는 흐름이 반복됨. 상담 시 주의점: 임시안 수치만 보고 판단하지 말고 확정안까지 확인할 것.',
'\n- 9월 임시안 사례: 조정 전 채무 1억2천만원인데 조정 후 1억, 감면율 77%. "재산가액도 많지 않은데 주택담보대출 1억을 재산가액에 다 넣어버린 듯"이라는 문제 제기\n- 임시안 단계에서 담보대출·지역공제가 미반영돼 감면율 0%나 낮은 조정액이 나왔다가 소명·자료 제출 후 조정되는 흐름이 반복됨\n- "공시가격으로 생각했는데 더 높게 나왔다", "KB시세가 갑자기 올라 잡혔다"는 평가액 불만도 반복됨\n\n\n- 상담 시 임시안 수치만 보고 판단하지 말고 확정안까지 확인할 것을 안내\n- 임시안 산정 오류 가능성을 염두에 두고 소명 자료 준비를 안내\n\n※ 이 항목은 카페 사례 조사에 기반한 정리이며, 공식 규정 문구가 아닙니다.',
'카페 사례 조사',
'PROPERTY_VALUATION,TEMPORARY_PLAN,FINAL_PLAN',
'https://cafe.naver.com/ccrs5500/795918', '2026-10-08', '2026-10-08', NULL,
1, 'ACTIVE', 'MEDIUM', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261008-08')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_url` = VALUES(`source_url`),
`source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261008-09 (NEW / RULE / ACTIVE)
-- 신복위 채무조정 공제재산가액 공식표 (신속/프리/워크) — 3인 가구 기준
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('RULE', '기타',
'신복위 채무조정 공제재산가액 공식표 (신속/프리/워크) — 3인 가구 기준',
'카페 FAQ에서 확인한 공제재산가액표(신복위 공식 유튜브 2023년 2월 자료 인용). 3인 가구 기준: 서울특별시 16,500만원 / 과밀억제권역(서울 제외) 14,500만원 / 광역시(군지역 제외)·세종·화성·용인·김포 8,500만원 / 기타 지역 7,500만원. 과밀억제권역 = 인천광역시 및 경기도 28개 시. 공동명의는 각자 지분율로 산정 후 공제재산가액 추가 차감. 주의: 2023년 2월 자료이며 실제 운영기준과 다를 수 있음.',
'\n- 출처: 카페 FAQ (신복위 공식 유튜브 2023년 2월 자료 인용, 회원 작성)\n- 3인 가구 기준 공제재산가액:\n  · 서울특별시: 16,500만원 (2인 이하 13,200만원 / 4인 이상 19,800만원)\n  · 과밀억제권역(서울 제외): 14,500만원 (2인 이하 11,600만원 / 4인 이상 17,400만원)\n  · 광역시(군지역 제외)·세종·화성·용인·김포: 8,500만원 (2인 이하 6,800만원 / 4인 이상 10,200만원)\n  · 기타 지역: 7,500만원 (2인 이하 6,000만원 / 4인 이상 9,000만원)\n- 과밀억제권역 = 인천광역시 및 경기도 28개 시\n- 공동명의는 각자 지분율로 산정 후 공제재산가액 추가 차감\n\n\n- 2023년 2월 자료이며 실제 운영기준과 다를 수 있다고 명시됨\n- 새출발기금 부실차주의 지역공제(최우선변제금 기준)와는 별도 기준이므로 혼동하지 않음\n- OFFICIAL로 승격하지 말고 카페 자료 인용 수준으로 활용\n\n※ 이 항목은 카페 자료 인용이며, 공식 규정 문구가 아닙니다.',
'카페 자료 인용',
'PROPERTY_VALUATION,REGIONAL_DEDUCTION,PERSONAL_WORKOUT',
'https://cafe.naver.com/ccrs5500/771781', '2026-10-08', '2026-10-08', NULL,
1, 'ACTIVE', 'MEDIUM', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261008-09')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_url` = VALUES(`source_url`),
`source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);
