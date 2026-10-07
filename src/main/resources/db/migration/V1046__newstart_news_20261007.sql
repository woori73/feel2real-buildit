-- V1046: NewStart 최신 소식(공식 업데이트) 신규 항목 등록 (Track B, 2026-10-07)
-- 대상 테이블: newstart_official_updates (V989 생성, V991/V998 컬럼 추가)
--
-- 선정 기준: 2026년 새출발기금 공식 발표자료만. 보도자료 문장을 그대로 복사하지 않고
-- 회원이 읽기 쉽게 요약한 summary를 저장. content(원문 본문)는 미저장(NULL).
--
-- 중복 방지: dedup_key UNIQUE 제약으로 보장.
--  - 공식 문서ID가 있는 항목: DOC:{SOURCE_TYPE}:{문서ID 소문자} (NewStartOfficialUpdateService.dedupKey 규칙과 동일)
--  - 공식 문서ID가 없는 항목: TITLE_DATE:{SOURCE_TYPE}:{정규화 제목}:{발표일}
--  - 상시 안내 페이지: URL:{정규화 URL}
-- 운영 DB에 동일 dedup_key가 이미 존재하면(자동 discovery가 선등록한 경우) INSERT가
-- 실패하므로, 그 경우 해당 행을 이 파일에서 제거하고 다시 적용한다.
-- 기존 행을 UPDATE/DELETE 하지 않는다.
--
-- review_status: 공식 출처 확인 항목은 사이트 자동 게시 정책(2026-09-23)에 따라 PUBLISHED.
-- 단, 4번 항목(8월말 신청 현황)은 공식 보도자료 원문 URL을 확보하지 못해 보도기사 링크를
-- 기재했으므로 REVIEW 상태로 둔다. 공식 URL 확인 시 교체 후 PUBLISHED 전환.

-- 1) 2026-02-26 금융위원회 「새출발기금 추진사항 점검회의」 보도자료
INSERT INTO newstart_official_updates
    (source_type, source_name, title, summary, content, original_url, official_document_id,
     published_at, effective_from, effective_to, category, relevance_priority, review_status,
     featured, discovered_at, dedup_key, reviewed_by, reviewed_at, created_at, updated_at)
VALUES
    ('FSC', '금융위원회',
     '2026년 새출발기금 추진사항 발표 — 성실상환 인센티브·상환유예 확대',
     '금융위원회가 2026년 2월 26일 새출발기금 점검회의에서 올해 추진사항을 발표했습니다. 갚을 능력이 있는데도 높은 감면을 받는 경우를 줄이고, 성실히 갚는 분께 혜택이 돌아가도록 제도를 손봅니다. 매입형(부실차주)은 1년 이상 연체 없이 상환한 뒤 남은 채무를 한 번에 갚으면 잔액의 5~10%를 추가로 감면해 줍니다. 중개형(부실우려차주)은 1년 성실상환마다 금리를 낮춰 줍니다. 출산·육아휴직·가족 질병 등 상환유예 사유도 넓어지고, 취·창업 프로그램 이수 시 원금 추가 감면(최대 10%p)과 연계됩니다. 대부업권 4개사(리드코프·바로크레디트대부·써니캐피탈대부·저스트인타임대부)도 새로 협약에 참여해 해당 업체 채무도 지원 대상에 포함됩니다.',
     NULL,
     'https://www.fsc.go.kr/comm/getFile?srvcId=BBSTY1&upperNo=86357&fileTy=ATTACH&fileNo=2',
     'FSC-86357',
     '2026-02-26 16:00:00', NULL, NULL,
     'POLICY', 'HIGH', 'PUBLISHED',
     FALSE, NOW(6),
     'DOC:FSC:fsc-86357',
     'MUSE_TRACK_B', NOW(6), NOW(6), NOW(6));
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`original_url` = VALUES(`original_url`), `review_status` = VALUES(`review_status`),
`relevance_priority` = VALUES(`relevance_priority`), `updated_at` = NOW(6);

-- 2) 2026-06-25 금융위원회·캠코 「새출발기금 심사체계 개선」 발표
-- 가상자산('26.1월~)·비상장주식('26.5월~) 재산심사 반영, 감면기준 차등화(60~80% → 30~80%),
-- 사해행위 전수조사('26.2월~) + 신용정보법 개정('26.8월) 활용
INSERT INTO newstart_official_updates
    (source_type, source_name, title, summary, content, original_url, official_document_id,
     published_at, effective_from, effective_to, category, relevance_priority, review_status,
     featured, discovered_at, dedup_key, reviewed_by, reviewed_at, created_at, updated_at)
VALUES
    ('FSC', '금융위원회',
     '새출발기금 심사체계 개선 — 재산심사 강화·감면기준 차등화',
     '금융위원회와 캠코가 2026년 6월 25일 새출발기금 심사체계 개선안을 발표했습니다. 첫째, 그동안 확인이 어려웠던 가상자산(올해 1월부터)·비상장주식(올해 5월부터) 보유내역을 재산심사에 반영합니다. 둘째, 원금감면 기준이 달라집니다. 기존에는 순부채의 60~80%를 감면했지만, 앞으로는 변제능력에 따라 30~80%로 차등 적용됩니다. 갚을 능력이 충분한데도 높은 감면을 받으면 감면율이 낮아질 수 있습니다. 저소득·취약차주의 최대 90% 감면은 유지됩니다. 셋째, 신청 전 증여 등으로 재산을 줄인 사해행위는 올해 2월부터 전수조사가 진행 중이며, 8월부터는 개정 신용정보법으로 조사 범위가 넓어집니다. 적발되면 약정 해지·채무 회수 등 조치가 있을 수 있으니, 신청 전 재산 처분은 삼가야 합니다.',
     NULL,
     'https://www.fsc.go.kr/no010101/87188',
     'FSC-87188',
     '2026-06-25 00:00:00', NULL, NULL,
     'POLICY', 'CRITICAL', 'PUBLISHED',
     FALSE, NOW(6),
     'DOC:FSC:fsc-87188',
     'MUSE_TRACK_B', NOW(6), NOW(6), NOW(6));
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`original_url` = VALUES(`original_url`), `review_status` = VALUES(`review_status`),
`relevance_priority` = VALUES(`relevance_priority`), `updated_at` = NOW(6);

-- 3) 2026-08-11 신용정보법 개정 시행 — 채무조정기구의 재산정보 일괄 확보
-- 신용정보의 이용 및 보호에 관한 법률 시행령 [별표 2의4] <신설 2026. 8. 11.>
INSERT INTO newstart_official_updates
    (source_type, source_name, title, summary, content, original_url, official_document_id,
     published_at, effective_from, effective_to, category, relevance_priority, review_status,
     featured, discovered_at, dedup_key, reviewed_by, reviewed_at, created_at, updated_at)
VALUES
    ('PUBLIC_AGENCY', '법제처 국가법령정보',
     '신용정보법 개정 시행 — 채무조정기구의 재산정보 일괄 확보 가능',
     '2026년 8월 11일부터 시행된 신용정보법 개정으로, 새출발기금·새도약기금 같은 정부 채무조정기구가 채무자의 개별 동의 없이도 가상자산을 포함한 금융자산 정보를 수집해 상환능력 심사에 활용할 수 있게 됐습니다. 수집 사실은 채무자에게 나중에 알려야 합니다. 심사가 빨라지고, 재산을 숨기고 감면을 받는 경우를 가려내는 데 쓰입니다. 신청자 입장에서는 제출한 재산내역에 누락이 없도록 정확히 기재하는 것이 중요합니다.',
     NULL,
     'https://www.law.go.kr/LSW/flDownload.do?flSeq=168087993',
     'CREDIT-INFO-ENF-DECREE-APPX2-4-20260811',
     '2026-08-11 00:00:00', NULL, NULL,
     'POLICY', 'HIGH', 'PUBLISHED',
     FALSE, NOW(6),
     'DOC:PUBLIC_AGENCY:credit-info-enf-decree-appx2-4-20260811',
     'MUSE_TRACK_B', NOW(6), NOW(6), NOW(6));
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`original_url` = VALUES(`original_url`), `review_status` = VALUES(`review_status`),
`relevance_priority` = VALUES(`relevance_priority`), `updated_at` = NOW(6);

-- 4) 2026-09-18 캠코·신용회복위원회 8월말 신청 현황 발표
-- NOTE: 공식 보도자료 원문 URL을 웹 검색으로 확보하지 못함. 캠코·신복위 공동 발표를
-- 복수 언론이 동일 수치로 보도한 기사 링크를 기재. 공식 URL 확인 시 교체 필요.
-- 수치: 누적 신청 216,173명·34조1,371억원 / 약정 151,881명·14조295억원 /
-- 매입형 77,370명·평균 감면율 약 74% / 중개형 74,511명·평균 금리인하 약 5.5%p
INSERT INTO newstart_official_updates
    (source_type, source_name, title, summary, content, original_url, official_document_id,
     published_at, effective_from, effective_to, category, relevance_priority, review_status,
     featured, discovered_at, dedup_key, reviewed_by, reviewed_at, created_at, updated_at)
VALUES
    ('NEWSTART_FUND', '한국자산관리공사·신용회복위원회',
     '새출발기금 8월말 신청 현황 — 누적 21.6만명·34.1조원',
     '캠코와 신용회복위원회가 2026년 9월 18일 발표한 8월 말 기준 현황입니다. 누적 신청은 21만 6,173명(채무액 34조 1,371억 원), 이 중 15만 1,881명이 채무조정 약정을 체결했습니다(약정 채무원금 14조 295억 원). 매입형은 7만 7,370명이 약정해 평균 74%의 원금감면을 받았고, 중개형은 7만 4,511명이 확정돼 평균 5.5%포인트의 금리 인하를 받았습니다. (안내: 이 항목은 공식 보도자료 원문 URL을 확인하지 못해 보도기사 링크를 기재했습니다. 공식 URL 확인 시 교체가 필요합니다.)',
     NULL,
     'https://www.m-i.kr/news/articleView.html?idxno=1416136',
     NULL,
     '2026-09-18 00:00:00', NULL, NULL,
     'NOTICE', 'NORMAL', 'REVIEW',
     FALSE, NOW(6),
     'TITLE_DATE:NEWSTART_FUND:새출발기금 채무조정 신청 현황(26.8월말 기준):2026-09-18',
     NULL, NULL, NOW(6), NOW(6));
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`original_url` = VALUES(`original_url`), `review_status` = VALUES(`review_status`),
`relevance_priority` = VALUES(`relevance_priority`), `updated_at` = NOW(6);

-- 5) 캠코 공식 안내 — 매입형(부실차주) vs 중개형(부실우려차주) 채무조정 구조
-- 출처: 한국자산관리공사 홈페이지 「채무조정 지원내용」 (상시 안내 페이지, 발표일 없음)
INSERT INTO newstart_official_updates
    (source_type, source_name, title, summary, content, original_url, official_document_id,
     published_at, effective_from, effective_to, category, relevance_priority, review_status,
     featured, discovered_at, dedup_key, reviewed_by, reviewed_at, created_at, updated_at)
VALUES
    ('NEWSTART_FUND', '한국자산관리공사(새출발기금)',
     '새출발기금 채무조정 구조 — 매입형(부실차주)과 중개형(부실우려차주)',
     '연체 기간에 따라 두 가지 길이 있습니다. 90일 이상 연체한 부실차주는 새출발기금이 채권을 매입해 직접 조정하는 매입형으로, 보유재산을 반영해 순부채의 0~80%를 원금 감면받고(취약계층은 최대 90%), 이자와 연체이자는 감면됩니다. 90일 미만 연체의 부실우려차주는 신용회복위원회가 금융회사와 중개하는 중개형으로, 원금 감면은 없고 연체 기간에 따라 금리를 낮춰줍니다. 담보대출도 중개형과 같은 방식으로 조정됩니다. 채무조정 신청 즉시 다음 날부터 추심이 중단됩니다. 자세한 지원 내용은 캠코 공식 안내 페이지에서 확인할 수 있습니다.',
     NULL,
     'https://www.kamco.or.kr/portal/contents.do?mId=0203030000',
     NULL,
     NULL, NULL, NULL,
     'DEBT_ADJUSTMENT', 'NORMAL', 'PUBLISHED',
     FALSE, NOW(6),
     'URL:https://www.kamco.or.kr/portal/contents.do?mId=0203030000',
     'MUSE_TRACK_B', NOW(6), NOW(6), NOW(6));
ON DUPLICATE KEY UPDATE
`title` = VALUES(`title`), `summary` = VALUES(`summary`),
`original_url` = VALUES(`original_url`), `review_status` = VALUES(`review_status`),
`relevance_priority` = VALUES(`relevance_priority`), `updated_at` = NOW(6);
