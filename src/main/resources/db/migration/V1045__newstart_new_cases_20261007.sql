-- ============================================================
-- V1045: NewStart 지피지기 DB — 2026-10-07 신규 사례/규칙 반영
-- 입력: ~/workspace/newstart-cases-20261007/new_cases.jsonl (9건)
-- 원칙 (MUSE_지피지기DB_반영지시서.md):
-- · 회원 사실(facts)과 운영자 해석(operator_interpretation)을 content 안에서 별도 섹션으로 구분
-- · operator_experience를 OFFICIAL로 승격하지 않음 (authority: '운영자 경험')
-- · NEW_DERIVED_RULE_REVIEW(03, 05)는 절대 ACTIVE 금지 → REVIEW
-- · 개인정보·닉네임 절대 포함 금지
--
--
-- NEW-20261007-01 → NEW (CASE, ACTIVE): 주택 지분, 공시지가 2.2억 예상→감정가 3.6억 적용
-- NEW-20261007-02 → NEW (CASE, ACTIVE): 공동명의 아파트, 시세평가 확인 후 감면 어려움
-- NEW-20261007-03 → REVIEW_HOLD (CASE, REVIEW): 재산심사 강화 관찰 — 공식근거 교차검증 전
-- NEW-20261007-04 → NEW (CASE, ACTIVE): 확정안 후 취소 → 개인워크아웃 전환 실제 사례
-- NEW-20261007-05 → REVIEW_HOLD (CASE, REVIEW): 사해행위 소송 위험 — 공식근거 교차검증 전
-- NEW-20261007-06 → NEW (RULE, ACTIVE): 보증금 중복공제 금지 (운영기준)
-- NEW-20261007-07 → MERGED_WITH_EXISTING: INSERT 생략. 아래 주석 참조
-- NEW-20261007-08 → NEW (RULE, ACTIVE): 주거급여 병행 검토 (상담 규칙)
-- NEW-20261007-09 → NEW (RULE, ACTIVE): 임시안 후 확정안 4~6개월 흐름·4개월차 확인
--
--
-- JSONL 자체 기재("차량 시가 4천만원 이하는 운영 DB상 재산가액 0 처리")와 일치:
-- 공식 연구 자료 docs/newstart/research-official/ccrs/04_property_knowledge.md §2-2에
-- 이미 동일 규칙이 정리되어 있음 —
-- "시가 4,000만 원 이하 → 재산가액 0원 처리" (새출발기금·개인워크아웃 양쪽 공식),
-- "차량 담보대출: 담보대출 잔액 차감 후 잔여 시가 평가" (양쪽 공식).
-- 해당 자료는 NewstartOfficialReviewIngestService로 newstart_knowledge_items에
-- 적재된 것으로 확인 (title 멱등 적재). 신규 생성 시 실질 중복이므로 INSERT하지 않음.
-- ※ 기존 knowledge_id는 repo에서 확인할 수 없으므로, 운영 DB에서
-- 04_property_knowledge.md §2-2 대응 행의 knowledge_id를 확인해
-- 본 주석에 기입한 뒤 PR에 포함할 것.
--
-- dedup_key는 UNIQUE이므로 재실행은 ON DUPLICATE KEY UPDATE로 멱등 처리.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ------------------------------------------------------------
-- NEW-20261007-01 (NEW / CASE / ACTIVE)
-- 주택 지분 공시지가 예상 → 감정가격 적용으로 상환부담 급증
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('CASE', '감면율',
'주택 지분 공시지가 예상 → 감정가격 적용으로 상환부담 급증',
'확정체결 전 주택 지분을 보유한 회원이 공시지가 2억2천만원 수준을 예상했으나 실제 재산평가에서 감정가격 3억6천만원이 적용되어, 임시안 감면율은 동일해도 월상환액과 총 상환부담이 크게 증가한 실제 사례.',
'\n- 확정체결 전 주택 지분 보유\n- 회원이 공시지가 2억2천만원 수준을 예상\n- 실제 재산평가에서 감정가격 3억6천만원으로 환산\n- 임시안 감면율 표시는 동일했으나 월상환액과 총 상환부담이 크게 증가\n\n\n- 부동산 보유 차주가 공시지가를 예상했다가 실제 시세·감정가 기준 평가를 확인해 당황한 실제 사례\n- 최근 재산심사 강화와 연결해 지피지기 유사사례로 활용\n\n※ 이 항목은 운영자 경험을 바탕으로 한 사례 정리이며, 공식 규정 문구가 아닙니다.',
'운영자 경험',
'PROPERTY_VALUATION,HOUSE_SHARE,PUBLIC_PRICE,MARKET_PRICE,APPRAISAL,FINAL_PLAN',
NULL, '2026-10-07', '2026-10-07', NULL,
1, 'ACTIVE', 'MEDIUM', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261007-01')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261007-02 (NEW / CASE / ACTIVE)
-- 공동명의 아파트 — 최근 시세상승·시세평가 확인 후 감면 어려움
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('CASE', '감면율',
'공동명의 아파트 — 최근 시세상승·시세평가 확인 후 감면 어려움',
'부실차주 신청 중(확정안 전 단계)인 회원이 공동명의 아파트를 보유하고, 최근 시세 상승으로 채무와 재산가액이 비슷해져 감면이 어려울 것이라고 설명한 실제 사례.',
'\n- 부실차주 신청 중, 확정안 전 단계\n- 공동명의 아파트 보유\n- 회원은 최근 시세가 크게 올라 채무와 재산가액이 비슷해져 감면이 어려울 것이라고 설명\n- 신청 전에 알았다면 신용을 지키거나 다른 제도를 알아봤을 것이라고 호소\n\n\n- 최근 시세상승도 원인이지만 공시지가 수준을 예상했다가 실제 시세·감정가 기준 평가를 확인한 영향도 큰 것으로 판단\n- 고의연체 자체만으로 ''처음부터 충분한 재산이 있었던 사례''라고 단정하지 않음\n\n※ 이 항목은 운영자 경험을 바탕으로 한 사례 정리이며, 공식 규정 문구가 아닙니다.',
'운영자 경험',
'PROPERTY_VALUATION,JOINT_OWNERSHIP,MARKET_PRICE,HAIRCUT_ZERO,DELINQUENCY',
NULL, '2026-10-07', '2026-10-07', NULL,
1, 'ACTIVE', 'MEDIUM', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261007-02')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261007-03 (REVIEW_HOLD / CASE / REVIEW — ACTIVE 금지)
-- 최근 주택 재산심사 강화 — 시세 부재 시 공시지가 산정에서 실제 시세 측정 방향
-- ※ NEW_DERIVED_RULE_REVIEW: 공식근거와 운영사례 교차검증 전까지 REVIEW 유지
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('CASE', '기타',
'최근 주택 재산심사 강화 — 시세 부재 시 공시지가 산정에서 실제 시세 측정 방향',
'운영자 경험상 과거에는 확인 가능한 시세가 잘 없는 주택의 경우 공시지가 등을 이용해 산정되는 사례가 있었으나, 최근 심사 강화 이후에는 실제 시세를 확인·측정하여 주택 재산가액을 산정하는 방향으로 변경된 것으로 관찰.',
'\n- 운영자 경험상 과거에는 확인 가능한 시세가 잘 없는 주택의 경우 공시지가 등을 이용해 산정되는 사례가 있었음\n- 최근 심사 강화 이후에는 실제 시세를 확인·측정하여 주택 재산가액을 산정하는 방향으로 변경된 것으로 관찰\n\n\n- 공식 규정 문구로 승격하지 말고 운영경험/실제사례 기반으로만 활용\n- ACTIVE 전 공식근거와 운영사례 교차검증 필요 (REVIEW 상태 유지)\n\n※ 이 항목은 공식 규정이 아니라 운영자 경험에 기반한 관찰 기록입니다.',
'운영자 경험',
'PROPERTY_REVIEW,MARKET_PRICE,PUBLIC_PRICE,REVIEW_STRENGTHENING',
NULL, '2026-10-07', '2026-10-07', NULL,
1, 'REVIEW', 'LOW', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261007-03')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261007-04 (NEW / CASE / ACTIVE)
-- 부실차주 확정안 후 취소 → 개인워크아웃에서 큰 감면을 받은 실제 사례
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('CASE', '기타',
'부실차주 확정안 후 취소 → 개인워크아웃에서 큰 감면을 받은 실제 사례',
'새출발기금 부실차주 확정안을 받은 뒤 취소하고 신용회복위원회 개인워크아웃으로 전환해 실제로 큰 폭의 감면을 받은 사례가 운영경험상 존재한다는 운영 기록.',
'\n- 새출발기금 부실차주 확정안을 받은 뒤 취소\n- 이후 신용회복위원회 개인워크아웃으로 전환\n- 실제로 큰 폭의 감면을 받은 사례가 운영경험상 존재\n\n\n- 신복위 상담사·심사역이 캠코 단일채권자 동의가 어렵다고 말해도 개인워크아웃이 사실상 불가능하다고 단정하지 않음\n- 상담사 설명과 실제 조정결과는 다를 수 있음\n- 감면율·캠코 동의를 사전에 보장하지 않음\n\n※ 이 항목은 운영자 경험을 바탕으로 한 사례 정리이며, 공식 규정 문구가 아닙니다. 감면 결과를 보장하지 않습니다.',
'운영자 경험',
'FINAL_PLAN,CANCEL,PERSONAL_WORKOUT,KAMCO,HAIRCUT,KAMCO_CONSENT',
NULL, '2026-10-07', '2026-10-07', NULL,
1, 'ACTIVE', 'MEDIUM', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261007-04')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261007-05 (REVIEW_HOLD / CASE / REVIEW — ACTIVE 금지)
-- 장기연체·신용불량 상태에서 주택 매도 → 사해행위 소송 위험
-- ※ NEW_DERIVED_RULE_REVIEW: 공식근거와 운영사례 교차검증 전까지 REVIEW 유지
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('CASE', '기타',
'장기연체·신용불량 상태에서 주택 매도 → 사해행위 소송 위험',
'부실차주 신청을 포기·취소해도 장기연체·신용불량 상태가 즉시 해소되지 않으며, 그 상태에서 주택 등 주요 재산을 매도하면 채권사가 사해행위로 보고 취소소송 등 법적 조치를 할 수 있다는 운영 주의 기록. ''취소 → 집 매도 → 개인워크아웃'' 경로를 안전하게 안내하지 않음.',
'\n- 부실차주 신청을 포기·취소해도 기존 장기연체·신용불량 상태가 즉시 해소되는 것은 아님\n- 그 상태에서 주택 등 주요 재산을 매도하면 채권사가 재산처분을 사해행위로 보고 취소소송 등 법적 조치를 할 수 있음\n- 재산처분 후 개인워크아웃 등 다른 채무조정에 들어가도 처분 이력이 남아 문제가 될 수 있음\n\n\n- ''새출발기금 취소 → 집 매도 → 개인워크아웃''을 안전한 경로로 안내하지 않음\n- 확정안 후 집을 건드리지 않고 취소→개인워크아웃으로 전환한 성공사례와 명확히 구분\n\n\n- 공식 규정 문구로 승격하지 말고 운영경험/실제사례 기반으로만 활용\n- ACTIVE 전 공식근거와 운영사례 교차검증 필요 (REVIEW 상태 유지)\n\n※ 이 항목은 공식 규정이 아니라 운영자 경험에 기반한 주의 기록이며, 법률 자문이 아닙니다.',
'운영자 경험',
'FRAUDULENT_TRANSFER,PROPERTY_SALE,DELINQUENCY,PERSONAL_WORKOUT,LITIGATION',
NULL, '2026-10-07', '2026-10-07', NULL,
1, 'REVIEW', 'MEDIUM', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261007-05')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261007-06 (NEW / RULE / ACTIVE)
-- 상가·주거 보증금 공제 — 둘 다 중복공제하지 않음
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('RULE', '기타',
'상가·주거 보증금 공제 — 둘 다 중복공제하지 않음',
'지역별 보증금 공제는 주거 임차보증금 또는 사업장 임차보증금 중 하나에만 적용하는 운영기준. 두 보증금에 각각 중복공제하지 않으며, 실제 운영사례 공제 규모는 대체로 2천만~3천만원 수준이다.',
'\n- 지역별 보증금 공제는 주거 임차보증금 또는 사업장 임차보증금 중 하나에 적용하는 운영기준\n- 두 보증금 모두에 각각 중복공제하는 것으로 안내하지 않음\n- 실제 운영사례에서 공제 규모는 대체로 2천만~3천만원 수준\n- 상가보증금 약 2천만원은 전액 공제된 사례가 흔함\n\n\n- 지역·월세를 물어 AI가 임의 계산하지 말고 운영 DB에 확인된 범위만 답변\n\n※ 이 항목은 운영자 경험/운영사례에 기반한 운영기준이며, 공식 규정 문구가 아닙니다.',
'운영자 경험',
'DEPOSIT,BUSINESS_DEPOSIT,HOUSING_DEPOSIT,PROPERTY_DEDUCTION',
NULL, '2026-10-07', '2026-10-07', NULL,
1, 'ACTIVE', 'HIGH', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261007-06')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261007-07 → MERGED_WITH_EXISTING (INSERT 생략)
-- 사유: 실질 중복. docs/newstart/research-official/ccrs/04_property_knowledge.md §2-2에
-- 동일 규칙 기재 ("시가 4,000만 원 이하 → 재산가액 0원 처리", "차량 담보대출 잔액 차감 후
-- 잔여 시가 평가", 새출발기금·개인워크아웃 양쪽 공식). 해당 자료는 이미
-- newstart_knowledge_items에 적재됨 (NewstartOfficialReviewIngestService, title 멱등).
-- 기존 knowledge_id는 운영 DB에서 확인 후 PR에 기입할 것.
-- ------------------------------------------------------------

-- ------------------------------------------------------------
-- NEW-20261007-08 (NEW / RULE / ACTIVE)
-- 무직·저소득 차주 — 생계급여뿐 아니라 주거급여도 함께 검토
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('RULE', '기타',
'무직·저소득 차주 — 생계급여뿐 아니라 주거급여도 함께 검토',
'무직·저소득 차주의 복지자격 상담 시 생계급여만 보지 않고 주거급여도 함께 신청·상담하도록 안내하는 상담 규칙. 주거급여는 생계급여보다 더 높은 소득인정액 기준을 적용하고, 2027년 기준중위소득 인상에 따라 1인가구 주거급여 선정기준 금액도 상승하는 점을 반영.',
'\n- 현재 무직·저소득 차주의 복지자격 상담 시 생계급여만 보지 않고 주거급여도 함께 신청·상담하도록 안내\n- 주거급여는 생계급여보다 더 높은 소득인정액 기준을 적용하므로 현실적으로 신청 가능성을 확인할 가치가 있음\n- 2027년 기준중위소득 인상에 따라 1인가구 주거급여 선정기준 금액도 상승하는 점을 상담에 반영\n\n※ 이 항목은 운영자 경험에 기반한 상담 규칙이며, 공식 규정 문구가 아닙니다. 실제 수급 자격은 복지로 등 공식 판정 결과를 따른다.',
'운영자 경험',
'LOW_INCOME,UNEMPLOYED,HOUSING_BENEFIT,LIVELIHOOD_BENEFIT',
NULL, '2026-10-07', '2026-10-07', NULL,
1, 'ACTIVE', 'HIGH', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261007-08')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);

-- ------------------------------------------------------------
-- NEW-20261007-09 (NEW / RULE / ACTIVE)
-- 임시안 후 확정안 — 4~6개월 운영흐름과 4개월차 진행확인
-- ------------------------------------------------------------
INSERT INTO `newstart_knowledge_items`
(`source_type`, `category`, `title`, `summary`, `content`, `authority`,
`tags`, `source_url`, `source_date`, `effective_from`, `effective_to`,
`version`, `status`, `confidence`, `related_case_ids`, `related_rule_ids`,
`created_by`, `updated_by`, `created_at`, `updated_at`, `dedup_key`)
VALUES
('RULE', '상환계획',
'임시안 후 확정안 — 4~6개월 운영흐름과 4개월차 진행확인',
'부실차주가 임시안을 받은 뒤 통상 4~6개월 후 1차 확정안이 나오는 운영흐름. 임시안 수령 후 약 4개월 차에는 차주가 새출발기금에 직접 전화해 담당자와 통화하며 진행상황·재산/소득 심사·추가 소명 필요 여부를 확인하도록 안내하는 상담 규칙.',
'\n- 부실차주가 임시안을 받은 뒤 통상 4~6개월 후 1차 확정안이 나오는 운영흐름\n- 임시안 수령 후 약 4개월 차에는 차주가 새출발기금에 직접 전화해 담당자와 통화\n- 현재 진행상황, 재산·소득 심사, 추가 소명 필요 여부 등을 확인하도록 안내\n\n\n- 확정안 체결시점을 차주가 임의로 늦출 수 있다고 안내하지 않음; 새출발기금 절차에 따라 진행\n\n※ 이 항목은 운영자 경험에 기반한 운영흐름 정리이며, 공식 규정 문구가 아닙니다.',
'운영자 경험',
'PROVISIONAL_PLAN,FINAL_PLAN,TIMELINE,FOLLOW_UP',
NULL, '2026-10-07', '2026-10-07', NULL,
1, 'ACTIVE', 'HIGH', NULL, NULL,
'muse', 'muse', NOW(6), NOW(6), 'NEW-20261007-09')
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`content` = VALUES(`content`), `authority` = VALUES(`authority`),
`tags` = VALUES(`tags`), `source_date` = VALUES(`source_date`),
`effective_from` = VALUES(`effective_from`), `status` = VALUES(`status`),
`confidence` = VALUES(`confidence`), `updated_by` = VALUES(`updated_by`),
`updated_at` = NOW(6);
