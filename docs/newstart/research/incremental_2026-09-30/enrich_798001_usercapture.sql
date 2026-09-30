-- ENRICH 798001 (USER-PROVIDED VERIFIED CAPTURE / 추가 조사 금지)
-- 원 자료: CCRS_CAFE articleId 798001 (이미 수집됨: empirical 798001, knowledge 348)
-- 중복 empirical INSERT 없음. knowledge 7개 분리 항목만 보강. status=REVIEW (ACTIVE 금지).
SET NAMES utf8mb4;

-- K1: 금융마이데이터 시행 정보 (출처: 카페 정보글 캡처 / 기관=신용회복위원회)
INSERT INTO newstart_knowledge_items
(source_type, category, title, summary, content, authority, tags, source_url, source_date, version, status, confidence, related_case_ids, created_by)
SELECT 'CAFE_ARTICLE', '재산심사',
'[CCRS-798001-K1] 신복위 금융자산 심사: 금융마이데이터 시범 2026-08-31~09-04(서울중앙지부), 정식 2026-09-07(전 지부)',
'신용회복위원회 채무조정 심사에서 금융마이데이터 기반 금융자산 조회가 2026-09-07부터 전 지부 정식 시행됐다(시범은 8/31~9/4 서울중앙지부). 이 자료는 신복위( 신용회복위원회) 대상이며 새출발기금 자체의 공식 정책으로 저장하지 않는다.',
'PROVENANCE: CAFE_INFORMATION_POST (CCRS_CAFE articleId 798001, 게시 2026-09-24 13:13, 게시자 솔라리스)\nEVIDENCE: USER_PROVIDED_CAPTURE (사용자 캡처 3장 대조 검증됨)\n\n[확정 범위]\n- 시범 운영: 2026-08-31 ~ 2026-09-04, 서울중앙지부\n- 정식 운영: 2026-09-07부터 전 지부\n- 수단: 금융마이데이터\n- 대상 기관: 신용회복위원회(신복위) 채무조정 심사\n\n[금지 사항]\n- OFFICIAL_CONFIRMED로 임의 승격 금지 (카페 게시글 기반)\n- 기관 분리: 이 신복위 자료만으로 "새출발기금도 2026-09-07부터 예금·보험·주식을 조회한다" 규칙 생성 금지\n- 상태: OPERATIONALLY_REPORTED / REVIEW',
'신용회복위원회 (카페 게시 자료, 공식 확인 대기)',
'재산심사,금융자산,금융마이데이터,신복위,운영변화,CCRS',
'https://cafe.naver.com/ArticleRead.nhn?clubid=27453843&articleid=798001',
'2026-09-24', 1, 'REVIEW', 'USER_CAPTURE', '798001', 'USER_CAPTURE_20260930'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM newstart_knowledge_items WHERE title='[CCRS-798001-K1] 신복위 금융자산 심사: 금융마이데이터 시범 2026-08-31~09-04(서울중앙지부), 정식 2026-09-07(전 지부)');

-- K2: 조회 금융자산 항목 목록
INSERT INTO newstart_knowledge_items
(source_type, category, title, summary, content, authority, tags, source_url, source_date, version, status, confidence, related_case_ids, created_by)
SELECT 'CAFE_ARTICLE', '재산심사',
'[CCRS-798001-K2] 신복위 금융자산 조회 항목: 은행(예·적금·펀드·신탁·ISA), 보험, 금융투자(예수금·주식·파생), 휴면예금·보험',
'금융마이데이터로 조회된다는 금융자산 범주 4개: ① 은행(예금·적금·펀드·신탁·ISA) ② 보험 ③ 금융투자(예수금·주식·파생) ④ 휴면예금·휴면보험. 토픽: 재산심사 > 금융자산.',
'PROVENANCE: CAFE_INFORMATION_POST (CCRS_CAFE articleId 798001)\nEVIDENCE: USER_PROVIDED_CAPTURE (캡처 대조)\n\n[조회 항목 목록 (게시글 기재 그대로)]\n1. 은행: 예금, 적금, 펀드, 신탁, ISA\n2. 보험\n3. 금융투자: 예수금, 주식, 파생\n4. 휴면예금, 휴면보험\n\n[주의]\n- 게시글에 적힌 항목 목록이며 각 채권·자산의 실제 반영 방식(평가액, 면제 범위 등)은 별도 확인 필요.\n- OFFICIAL_CONFIRMED 승격 금지.',
'신용회복위원회 (카페 게시 자료, 공식 확인 대기)',
'재산심사,금융자산,조회항목,예적금,보험,주식,휴면예금,CCRS',
'https://cafe.naver.com/ArticleRead.nhn?clubid=27453843&articleid=798001',
'2026-09-24', 1, 'REVIEW', 'USER_CAPTURE', '798001', 'USER_CAPTURE_20260930'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM newstart_knowledge_items WHERE title='[CCRS-798001-K2] 신복위 금융자산 조회 항목: 은행(예·적금·펀드·신탁·ISA), 보험, 금융투자(예수금·주식·파생), 휴면예금·보험');

-- K3: 게시자의 제도변화 설명 (배경 해석)
INSERT INTO newstart_knowledge_items
(source_type, category, title, summary, content, authority, tags, source_url, source_date, version, status, confidence, related_case_ids, created_by)
SELECT 'CAFE_ARTICLE', '재산심사',
'[CCRS-798001-K3] 게시자 설명: 감사원 지적을 배경으로 신복위가 재산심사에 금융자산을 반영하기 시작했다는 2차 해석',
'게시자(솔라리스)는 감사원 지적 → 신복위 재산심사 개선 흐름으로 제도변화를 설명한다. 이는 게시자의 2차 해석(SECONDARY)이며 감사원과 신복위 제도변경의 직접적 공식 인과관계로 확정 저장하지 않는다.',
'PROVENANCE: CAFE_AUTHOR_EXPLANATION\nEVIDENCE LEVEL: SECONDARY (게시자 설명, 1차 공식 문서 아님)\n\n[내용]\n- 게시자는 감사원 지적(재산 미반영 문제)을 배경으로 신복위가 금융자산 조회·반영을 시작했다고 서술.\n\n[금지]\n- "감사원 → 신복위 제도변경"의 직접적인 공식 인과관계로 확정 저장 금지.\n- 인용된 fsc.go.kr / ccrs.or.kr 링크는 게시자가 제시한 참고 링크일 뿐, 해당 페이지 내용과 본 글의 대응관계는 미검증.',
'게시자 해석 (2차 정보)',
'재산심사,금융자산,제도변화,감사원,게시자해석,SECONDARY',
'https://cafe.naver.com/ArticleRead.nhn?clubid=27453843&articleid=798001',
'2026-09-24', 1, 'REVIEW', 'SECONDARY', '798001', 'USER_CAPTURE_20260930'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM newstart_knowledge_items WHERE title='[CCRS-798001-K3] 게시자 설명: 감사원 지적을 배경으로 신복위가 재산심사에 금융자산을 반영하기 시작했다는 2차 해석');

-- K4: 3,500만원 초과 상담 제보
INSERT INTO newstart_knowledge_items
(source_type, category, title, summary, content, authority, tags, source_url, source_date, version, status, confidence, related_case_ids, created_by)
SELECT 'CASE', '재산심사',
'[CCRS-798001-K4] 상담 제보: 금융자산 3,500만원 초과 시 접수 불가 사례 (2026-09-25 업데이트, 미확정)',
'게시글 9/25 업데이트에 따르면 금융자산이 3,500만원을 초과해 접수가 불가했다는 상담 제보. SUBTYPE: COUNSELING_REPORT / STATUS: UNCONFIRMED_THRESHOLD. 개별 상담 사례일 뿐 일반 규칙이 아니다.',
'PROVENANCE: CAFE_INFORMATION_POST 내 상담 제보 (9/25 업데이트 부분)\nEVIDENCE: COUNSELING_REPORT (상담 제보, 단일 사례)\nSTATUS: UNCONFIRMED_THRESHOLD\n\n[내용]\n- 금융자산 3,500만원 초과 접수 불가 제보.\n\n[금지]\n- "금융자산 3,500만원 초과 = 신복위 신청 불가"라는 일반 규칙으로 변환 금지.\n- 게시글 본인도 "확실치 않음"으로 기재한 추정 영역과 혼동하지 말 것.',
'미확정 (상담 제보)',
'재산심사,금융자산,3500만원,상담제보,UNCONFIRMED_THRESHOLD',
'https://cafe.naver.com/ArticleRead.nhn?clubid=27453843&articleid=798001',
'2026-09-25', 1, 'REVIEW', 'UNCONFIRMED', '798001', 'USER_CAPTURE_20260930'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM newstart_knowledge_items WHERE title='[CCRS-798001-K4] 상담 제보: 금융자산 3,500만원 초과 시 접수 불가 사례 (2026-09-25 업데이트, 미확정)');

-- K5: 2,000만원 초과 상담 제보
INSERT INTO newstart_knowledge_items
(source_type, category, title, summary, content, authority, tags, source_url, source_date, version, status, confidence, related_case_ids, created_by)
SELECT 'CASE', '재산심사',
'[CCRS-798001-K5] 상담 제보: 금융자산 2,000만원 초과 관련 접수 제한 사례 (2026-09-25 업데이트, 미확정)',
'게시글 9/25 업데이트에 금융자산 2,000만원 초과 관련 접수 제한 상담 제보가 있다. SUBTYPE: COUNSELING_REPORT / STATUS: UNCONFIRMED_THRESHOLD. 공식 일률적 cutoff로 변환 금지.',
'PROVENANCE: CAFE_INFORMATION_POST 내 상담 제보 (9/25 업데이트 부분)\nEVIDENCE: COUNSELING_REPORT (상담 제보, 단일 사례)\nSTATUS: UNCONFIRMED_THRESHOLD\n\n[금지]\n- "2,000만원 = 공식 cutoff"로 변환 금지.\n- K4(3,500만원) 제보와 수치·기준이 다르다. 두 제보를 하나의 규칙으로 MERGE 하지 말 것.',
'미확정 (상담 제보)',
'재산심사,금융자산,2000만원,상담제보,UNCONFIRMED_THRESHOLD',
'https://cafe.naver.com/ArticleRead.nhn?clubid=27453843&articleid=798001',
'2026-09-25', 1, 'REVIEW', 'UNCONFIRMED', '798001', 'USER_CAPTURE_20260930'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM newstart_knowledge_items WHERE title='[CCRS-798001-K5] 상담 제보: 금융자산 2,000만원 초과 관련 접수 제한 사례 (2026-09-25 업데이트, 미확정)');

-- K6: 재산+금융자산 vs 총채무 관계 추정
INSERT INTO newstart_knowledge_items
(source_type, category, title, summary, content, authority, tags, source_url, source_date, version, status, confidence, related_case_ids, created_by)
SELECT 'CASE', '재산심사',
'[CCRS-798001-K6] 커뮤니티 추정: 기존 재산 + 금융자산이 총채무 이하로 반영되는 구조 (게시자 "확실치 않음" 명시)',
'게시자는 기존 재산에 금융자산이 더해져 총채무 이하로 집계되는 구조로 추정한다고 서술하면서 "확실치 않음"을 명시했다. COMMUNITY_INFERENCE / UNCONFIRMED.',
'PROVENANCE: CAFE_AUTHOR_INTERPRETATION\nEVIDENCE: COMMUNITY_INFERENCE\nSTATUS: UNCONFIRMED\n\n[내용]\n- 추정: 심사 시 (기존 재산 + 금융자산) 이 총채무를 넘지 않는 범위에서 반영될 수 있다는 게시자 해석.\n\n[금지]\n- RULE 생성 금지. 이 추정을 계산 규칙·심사 기준으로 승격하지 말 것.',
'미확정 (커뮤니티 추정)',
'재산심사,금융자산,총채무,추정,UNCONFIRMED,COMMUNITY_INFERENCE',
'https://cafe.naver.com/ArticleRead.nhn?clubid=27453843&articleid=798001',
'2026-09-25', 1, 'REVIEW', 'UNCONFIRMED', '798001', 'USER_CAPTURE_20260930'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM newstart_knowledge_items WHERE title='[CCRS-798001-K6] 커뮤니티 추정: 기존 재산 + 금융자산이 총채무 이하로 반영되는 구조 (게시자 "확실치 않음" 명시)');

-- K7: 채무과중도/감면율 반영 추정
INSERT INTO newstart_knowledge_items
(source_type, category, title, summary, content, authority, tags, source_url, source_date, version, status, confidence, related_case_ids, created_by)
SELECT 'CASE', '재산심사',
'[CCRS-798001-K7] 커뮤니티 추정: 금융자산이 채무과중도·감면율 산정에 반영될 수 있다는 게시자 해석 (미확정)',
'게시자는 newly 조회된 금융자산이 채무과중도 판단과 감면율 산정에 반영될 수 있다고 해석한다. CAFE_AUTHOR_INTERPRETATION / UNCONFIRMED. 공식 감면율 산식으로 변환 금지.',
'PROVENANCE: CAFE_AUTHOR_INTERPRETATION\nEVIDENCE: COMMUNITY_INFERENCE\nSTATUS: UNCONFIRMED\n\n[내용]\n- 추정: 금융자산 증가 → 재산 평가 → 채무과중도/감면율에 영향 가능.\n\n[금지]\n- 공식 감면율 산식(수식)으로 변환 금지.\n- 이 항목만으로 "금융자산 X원이면 감면율 Y%" 식의 생성 답변 금지.',
'미확정 (게시자 해석)',
'재산심사,금융자산,채무과중도,감면율,추정,UNCONFIRMED',
'https://cafe.naver.com/ArticleRead.nhn?clubid=27453843&articleid=798001',
'2026-09-25', 1, 'REVIEW', 'UNCONFIRMED', '798001', 'USER_CAPTURE_20260930'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM newstart_knowledge_items WHERE title='[CCRS-798001-K7] 커뮤니티 추정: 금융자산이 채무과중도·감면율 산정에 반영될 수 있다는 게시자 해석 (미확정)');

-- RELATIONS: 각 K -> 348 (원본 캡처 SOURCE 보존 연결), SIMILAR 캐리어 (RELATED_TOPIC semantics)
INSERT IGNORE INTO newstart_knowledge_relations (from_knowledge_id, to_knowledge_id, relation_type, created_by)
SELECT k.knowledge_id, 348, 'SIMILAR', 'USER_CAPTURE_20260930'
FROM newstart_knowledge_items k
WHERE k.title LIKE '[CCRS-798001-K%';

-- RELATED_TOPIC / ASSET_REVIEW_CHANGE: K1 <-> 346(6050 보증금 보완), K1 <-> 156(5834 폐업자 임대차 요구 경험) — 인과 아닌 토픽 연결
INSERT IGNORE INTO newstart_knowledge_relations (from_knowledge_id, to_knowledge_id, relation_type, created_by)
SELECT k.knowledge_id, t.target_id, 'SIMILAR', 'USER_CAPTURE_20260930'
FROM newstart_knowledge_items k
JOIN (SELECT 346 AS target_id UNION SELECT 156) t
WHERE k.title LIKE '[CCRS-798001-K1]%';
