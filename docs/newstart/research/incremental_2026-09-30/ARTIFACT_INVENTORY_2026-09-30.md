# ARTIFACT INVENTORY & 버킷 매핑 — 2026-09-30 (ACADEMY, SAFE HEAD 9bf4b1ba 이후)

원칙: 재수집 0. Naver 재접속/로그인 0. 전부 기존 산출물(워크트리 + 원격 브랜치 + 운영 read-only census 실행 로그) 조사 결과.

## 1. 소스별 확보 자료 (있는 것)

| # | 위치 | 내용 | 카페/범위 |
|---|---|---|---|
| S1 | 이 브랜치 `raw/` | article 3401/3438/4078/4451/5568/6037/6038/6040/6043/6044/6046/6047/6050/6055 + 798001 + 799029 RAW(본문+댓글) 16건 | newhopero 14 + CCRS 2 |
| S2 | 이 브랜치 `increment_phase1.sql`, `enrich_798001_usercapture.sql` | phase1 case 16건 + 798001 K1~K7(knowledge 350~356) + relations 9 | HOME 로컬 docker용 SQL |
| S3 | 이 브랜치 `june2026/scan/*` | CCRS 6월 3키워드 dump (kw1 396 / kw2 191 / kw3 296) → UNION 883 → **UNIQUE 557**. 필드 = `날짜\|제목\|댓글수\|작성자` (**articleId·본문 없음**) | CCRS(27453843) 6월 |
| S4 | `june2026/scan/june_relevance_pass2_draft.txt`, `accepted_draft`, `excluded_draft`, `june_collection_queue.jsonl` | 제목 휴리스틱 분류 DRAFT (ACCEPTED 490 / EXCLUDED 67; P1 170 / P3 180 / LOW 140) | 동 |
| S5 | `cafe_editorial/DRAFT_924on_roundup.md` | 9/24 이후 정리글 초안(미게시) | A+B |
| S6 | origin `feat/newstart-cafe-qa-phase1-20260930` (**다른 에이전트, 미머지**) | newhopero **부실우려 board(menuId=6) 248건 article JSON**(본문·댓글·운영자 여부) + `qa_units.json` 1,000 + `cafe_dedup_report.json` + `cafe_qa_db_ready.json`(status=REVIEW 강제, batchId `cafe-qa-b6-2026-09-30`) | newhopero, 2024-10-24~2026-09-21 |
| S7 | origin main + `newstart/cafe-qoder-*` + `research/*ccrs5500-sample*` | article_json 136건 (자유/질문/부실차주 신청/감면율 등) | newhopero, **2026.07.22~09.19** |
| S8 | GitHub Actions run `36680265179` (main, 2026-09-30 06:49Z, read-only census) | **운영 RDS f2r_db 실측** (아래 §3) | production |

## 2. 버킷별 매핑 (정정된 표현)

"원문 0건"은 수집 실패 의미가 아니다. 아래는 **현재 후속 구조화 단계에서 articleId↔원문 매핑이 확정된 범위**다.

| 버킷 | 검색/목록 결과 | articleId↔원문 매핑 확정 | 구조화 자료 | 비고 |
|---|---|---|---|---|
| A newhopero 09/25~09/30 | (게시판 스캔 없음) | **9/9** (6037~6055, S1) | phase1 SQL + roundup 초안 | 9/24 기존 게시자료 = EXISTING_PUBLISHED, 신규 제외 |
| B CCRS 09/24~09/30 | — | **2/2** (798001, 799029) | K1~K7 + relations 9 | 2천/3,500/감면율 = UNCONFIRMED 유지 |
| C newhopero 06월 | (전 게시판 스캔 없음) | **부실우려 board 한정 8건 확정** (S6): 5369, 5372, 5374, 5388, 5393, 5429, 5451, 5582 | S6 qa_units에 이미 분해됨 (다른 에이전트 산출물, 중복 작업 금지) | 그 외 게시판 6월분은 확보 자료 없음 (S7은 7월~) |
| D CCRS 06월 | **557 UNIQUE 확보** (S3) | **0/557 — 미확정** (dump에 articleId 필드 없음; 저장소·전 브랜치 조사 후 동일) | 제목 휴리스틱 DRAFT만 (NEEDS_ORIGINAL) | 전체 재수집 대상 아님. 아래 §4 보충 후보만 |

이 인벤토리 후에도 D에 대해 다른 곳에 숨은 본문은 **발견되지 않았다**(main, cafe-qoder 4개, research/*, operator-knowledge, cafe-qa-phase1 전수 확인). 따라서 D는 "일부만 미매핑"이 아니라 **본문 자체가 이번 저장소 자료에 없음**이 확인된 상태다. 나머지 6월 P1/P3를 임의 INSERT 하지 않는다.

## 3. 운영 DB 실측 (S8, read-only, 06:49Z) — 핵심 정정

- DB: `f2r_db` @ **RDS** (`DB_HOST_CATEGORY=RDS`), 앱 jar 06:19Z 배포.
- `newstart_empirical_cases` = **107건** (전부 article_id 5855~6010, newhopero). 중복 0.
- `newstart_knowledge_items` = **119건, 전부 ACTIVE / REVIEW 0** (OFFICIAL_CURRENT 82, EBOOK 19, CASE 11, DERIVED_RULE 5, CAFE_ARTICLE 1, OPERATOR_KNOWLEDGE 1). 제목 중복 0.
- `newstart_knowledge_relations` = **1건**.
- **기존 "330 cases / 356 knowledge / ACTIVE=0"은 운영이 아니라 HOME 로컬 docker 수치로 확정.** 운영과 별개 DB.
- 결론(read-only 근거): phase1 16건, K1~K7(350~356), relations 9는 **운영에 없음** (case 107건이 5855~6010 범위로 정확히 일치, knowledge에 REVIEW 0건).
  → 정상 REVIEW 경로로 **새로 ingest 필요**. "이미 있음/SKIP" 대상 아님.
  (단 knowledge에 CAFE_ARTICLE 1·OPERATOR_KNOWLEDGE 1이 있어 5568/4451 계열은 ingest 직전 title/dedup_key 대조 필수.)
- 배포 jar 상태: `ingest/cases/repair` 엔드포인트 포함(GET=401). cafe-qa 전용 ingest는 미배포(S6 문서와 일치). admin API는 전부 401.
- 이 census는 기존 workflow의 과거 run 로그를 읽은 것이며 이번 세션에서 새 run을 트리거하지 않았고 쓰기는 0.

## 4. 원문 보충이 정말 필요한 D 후보 (전체 재수집 아님)

`june_collection_queue.jsonl` P1 중 댓글 30+ 5건, 20+ 10건, 10+ 41건. 사용자 승인 후 특정 글만 확인 대상으로 검토:
1. 06.01 새출발기금 부실차주 접수완료 후기 (댓글 43)
2. 06.29 생으로 90일 버티시는분 계신가요? (37)
3. 06.09 개인워크아웃 합의서 체결완료 (32)
4. 06.23 부실차주 신청 전 연체 방법 고민 (32)
5. 06.25 새출발부실차주 실효되신분 계신가요 (32)
(그 외 20+ 5건은 queue 참조)

## 5. 다음 단계 (게이트)

1. **정상 ADMIN session 필요** — S2 phase1 + K1~K7 + relations를 운영에 REVIEW로 ingest할 방식 확정:
   `POST /api/admin/newstart/wiki`(DRAFT) → `PUT /{id}/status`(REVIEW); case는 배포된 `ingest/cases/repair` 경로 또는 `newstart-seed.yml`(dry_run 기본 true). **운영 쓰기이므로 사용자 확인 후 진행.**
2. S6(다른 에이전트)와 ingest 충돌 조정: 같은 newhopero 부실우려 글을 이 브랜치가 다시 ingest하지 않는다. 6월 C 8건은 S6 batch로 처리.
3. 승인은 `POST /api/admin/newstart/wiki/review/{id}/approve` (ADMIN, 직접 SQL ACTIVE 금지).
4. 카페 게시(지피지기 Agent)는 9/25 이후 신규만. **게시는 대외 발행이므로 게시 직전 사용자 확인.**
