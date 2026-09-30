# HOME→ACADEMY HANDOFF 2026-09-30 (13:10 클로즈) — NEWSTART CCRS 6월 Collector

AGENT: NewStart 기간별 Collector (이 세션)
PROJECT: feel2real-buildit / NEWSTART 지피지기 CASE COLLECTOR
BRANCH: feat/newstart-case-collector-20260930

## [CURRENT TASK]
CCRS 카페(clubId 27453843, SOURCE_COMMUNITY=CCRS_CAFE) 2026-06-01~06-30 3키워드 전수 스캔 → UNION/DEDUPE → ACCEPTED/EXCLUDED 분류 → ACCEPTED 원문(본문+댓글) 수집 → DB REVIEW 증분.

## [COMPLETED]
- Step A 798001 보강: knowledge 350~356 (K1~K7, REVIEW), relations 9건(K→348 SIMILAR ×7, K1→346, K1→156). **완료 상태 유지 — 재INSERT/합병 금지.** 2천/3500 제보·K6/K7 추정은 UNCONFIRMED 유지 중.
- kw2(부실우려)×mi=38 6월 스캔 PASS — 파일 `june2026/scan/kw2_b38_june_rows.txt` (191행 저장, 헤더 FOUND=192 기록과 1행 차이; 파일이 SOURCE OF TRUTH, 재수집 금지 지시로 차이만 기록).
- kw3(새출발기금)×mi=38 6월 스캔 PASS — `kw3_b38_june_rows.txt` 296행 = FOUND 296 일치 (6/30 통과→tail 26.05.23, seen=1160).
- kw1(부실차주)×mi=38 재grind PASS — `kw1_b38_june_rows.txt` 396행 = FOUND 396 일치 (cp2 누락 1행[거니이니맘] 보완 완료). seen=1520, mounted tail 26.05.26 → **6/1 전체 통과 + 5/31 이하 경계 관측**.
- UNION/DEDUPE: `june2026/scan/union_unique.txt` — UNION BEFORE DEDUPE 883 → UNIQUE AFTER DEDUPE 557 (키=date|title|댓글|author, kw2 5번째 preview 필드 정규화 후). 근데어플 1쌍: 「새출발기금」/통통한나 @26.06.13 vs 26.06.17 — 서로 다른 날짜 게시, articleId 클릭 시 동일 글 여부 확인 필요(임의 병합 금지).
- ACCEPTED/EXCLUDED 휴리스틱 초안: `accepted_draft.txt` 490 / `excluded_draft.txt` 67 (규칙: 댓글≥3 또는 (정보 키워드 제목 + 댓글≥1)). **DRAFT — 원문 확인 없는 확정 아님.**
- kw1×전체(mi=0) 패스: seen=1359에서 서버 스로틀 스톨(tail 26.06.29) — PARTIAL 문서화됨. mi=38 게시판 필터 패스가 6월 창 전수 커버.

## [PARTIAL]
- ACCEPTED 원문 CLICK 수집: **0건** (미착수).
- 6월 structured JSON / DB 증분 SQL: **0건** (원문 없이는 INSERT 금지 — PARTIAL 상태에서 DB 증분 금지 규칙 준수).
- 16건(+798001 K1~K7 7건) REVIEW→ADMIN 승인→ACTIVE→RAG TEST A~D: ADMIN 세션 필요, 미착수.

## [NOT STARTED]
- ACCEPTED 원문/댓글 RAW 보존 → STRUCTURE → DB 중복검사(CCRS 6월 DB 0건이라 신규 충돌 없음) → REVIEW INSERT.
- June↔9/24 트랙(348/349) relation/timeline 연결.
- RAG TEST A~D + 근거 UI provenance, FINAL REPORT.

## [FAIL / BLOCKER]
- BLOCKED_BY_ADMIN_AUTH: ACTIVE 전환·RAG LIVE 검증은 ADMIN 로그인 세션 필요 (우회 금지 — 사용자 로그인 대기 규칙).
- kw1×전체(mi=0)는 스로틀로 6월 하부 미도달 → mi=38로 커버 완료(증거: kw1_b38 396행). 기관별 커버리지 차이로 "전체 게시판"이 아닌 "채무관련 궁금해요(mi=38)" 기준 PASS임 최종 보고서에 명시.

## [DATA] (checkpoint 보고 양식)
KW1 SCANNED: 1520 / KW1 JUNE FOUND: 396 / KW1 DUMP ROWS: 396 / OLDEST DATE REACHED: 26.05.26
KW2 JUNE: 192(기록) / 191(파일) — 파일 기준 보존
KW3 JUNE: 296 (FOUND= Dump= 296 일치, oldest 26.05.23)
UNION BEFORE DEDUPE: 883
UNIQUE AFTER DEDUPE: 557
ACCEPTED DRAFT: 490 / EXCLUDED DRAFT: 67
KW1 JUNE WINDOW: PASS
CCRS JUNE COLLECTION(스캔): **PASS** (3키워드 모두 6/30→6/1 통과 + 5/31 이하 경계 관측)
PHASE 2 COMPLETE: **아님** (ACCEPTED 원문 수집·DB 증분·RAG LIVE 검증 잔존)
DB 상태 변화 없음 (330 cases / 356 knowledge, ACTIVE=0) — 이번 세션 DB INSERT 0건.
NEWSTART_BORROWER_CAFE 이번 phase 신규: 0건 (5568은 newhopero 자료로 CCRS 건수 미포함 유지).

## [CODE]
repository: woori73/feel2real-buildit, branch feat/newstart-case-collector-20260930.
HEAD: 이번 세션 스캔 파일 커밋 후 push (커밋 해시는 `git log -1`로 확인).

## [FILES]
- docs/newstart/research/incremental_2026-09-30/june2026/scan/{kw1,kw2,kw3}_b38_june_rows.txt
- docs/newstart/research/incremental_2026-09-30/june2026/scan/union_unique.txt, accepted_draft.txt, excluded_draft.txt, {kw1,kw2,kw3}n.txt
- docs/newstart/research/incremental_2026-09-30/enrich_798001_usercapture.sql (기 실행 완료, 재실행 불필요)
- 본 handoff: docs/newstart/research/incremental_2026-09-30/HOME_TO_ACADEMY_HANDOFF_2026-09-30.md

## [BROWSER STATE] (인계 시점)
- browser-use 페이지: m.cafe 검색결과 kw1×mi=38 패스 유지 중 (window.__seen=1520, window.__june=396 보존).
- URL: https://m.cafe.naver.com/ca-fe/web/cafes/27453843/search?q=%EB%B6%80%EC%8B%A4%EC%B0%A8%EC%A3%BC&mi=38&ta=ARTICLE_COMMENT&pc=ALL&od=NEW
- 로그인 상태: 카페 검색 열람 가능 상태(비로그인 세션, 우회 nothing). navigate 시 window 전역 변수 소실 — dump 완료済み이므로 손실 없음.
- 원문 진입 방법(증명됨): `.article_box` click → `/ca-fe/web/cafes/27453843/articles/{id}?menuId=38` → innerText 캡처 → history.back().
- ADMIN SESSION: FAIL (ADMIN 로그인 없음)

## [DB CHECKPOINT]
BEFORE = AFTER (이번 세션): newstart_empirical_cases=330, knowledge=356, ACTIVE=0, 마지막 처리 knowledgeId=356 ([CCRS-798001-K7]).
6월 ACCEPTED INSERT는 원문 수집 후 착수 — articleId 기준 중복검사 필수(동일 articleId 재INSERT 금지, 새 Q&A/댓글만 보강).

## [WORKTREE]
commit/push 후 clean. 이번 세션 변경: june2026/scan/* + 본 handoff + raw/article_798001_raw.txt(append) + enrich_798001_usercapture.sql.

---
ACADEMY FIRST ACTION:
"accepted_draft.txt 상단(댓글 수 내림차순 상위 ~60건)부터 m.cafe kw1×mi=38 검색 페이지에서 .article_box click → articleId/본문/댓글 innerText 캡처 → history.back() 루프로 ACCEPTED 원문 RAW 수집을 재개한다 (navigate 금지, click/back만; 브라우저 탭에 kw1 패스 상태 유지됨)."

ACADEMY NEXT:
1. ACCEPTED 원문/댓글 RAW 수집(PII 마스킹: 실명/전화/계좌 제외, 본문 보존) + structured JSON.
2. 기존 DB articleId 중복검사 후 6월 증분 idempotent INSERT SQL(REVIEW, SOURCE_COMMUNITY=CCRS_CAFE) 실행 + 348/349 timeline relation.
3. ADMIN 세션 확보 → 16건+798001 K1~K7+6월 신규 승인 → ACTIVE → RAG TEST A~D + 근거 UI 검증 → FINAL REPORT(소스별 건수 분리).
