# ACADEMY 세션 상태 — 2026-09-30 (after HOME 8a0086c8)

AGENT: NewStart 기간별 Collector — ACADEMY 연속 세션
BRANCH: feat/newstart-case-collector-20260930 (HOME HEAD 8a0086c8에서 연속)

## HANDOFF RECOVERY: PASS
- remote branch/commit 8a0086c8 확인, 별도 worktree `feel2real-wt-newstart-case-collector` 생성(기존 worktree 무건드림), handoff 읽기 완료.

## 이번 세션 진행
1. **KW2 192/191**: 기존 dump 정적 분석으로 preview개행/parser/파일内duplicate 제외.
   남은 후보 = chunk1 경계(행49/50, 06.24→06.23) 쓰기기록 누락 또는 검색레벨 duplicate.
   articleId 확인 필요 → **UNRESOLVED / NON-BLOCKING으로 종결 기록** (재수집 없음, `june2026/scan/kw2_diff_status.txt`).
2. **2차 relevance 분류 DRAFT** (`june2026/pass2_classify.js` → `scan/june_relevance_pass2_draft.txt`):
   ACCEPTED 490 = P1 170 / P3 180 / LOW(P4·NOISE 구분보류) 140.
   P2(상담사·직원 답변 Q&A)는 제목으로 판정 불가 → 원문 확인 후 확정. 제목 휴리스틱은 DRAFT이며 이대로 INSERT 금지.
3. **SOURCE A/B/C 인벤토리**:
   - A(newhopero 9/25~): 원문 9건 확보(6037·6038·6040·6043·6044·6046·6047·6050·6055, raw/ + increment_phase1.sql 실행済み). 9/24 스캔은 없음(전용 게시판 실체 없음).
   - B(CCRS 9/24~): 798001(knowledge 348 + K1~K7 350~356, relations 9) + 799029 = phase1 16건에 포함, 전부 REVIEW.
   - C(newhopero 6월): **미착수 0건 확정** (CCRS 6월 dump와 혼동 금지).
   - D(CCRS 6월): dump만 존재, 원문 0건.
4. **카페 정리글 [9/24 이후] 초안**: `cafe_editorial/DRAFT_924on_roundup.md` — 원문 11건 근거, 사례 먼저/공식기준 뒤, 9/7 마이데이터·9/24 게시물 중복통제(링크만), CLAIM/UNCONFIRMED 라벨 유지. **미게시**.

## BLOCKED (환경)
- **BLOCKED_BROWSER (session-scoped)**: m.cafe.ca-fe 탭이 fetchMore 루프 후 renderer hung(1+1까지 타임아웃, reload 후에도 hydration busy). 사용자 지시로 재시도 중단. 이 browser session의 blocker이지 collector 전체 blocker가 아님.
  영향: 6월 articleId 매핑(DEDUPE 확정), P1→P2→P3 원문/댓글 수집, newhopero 6월 스캔(C), 카페 게시.
- **[REV-2 폐기] "운영 DB = HOME :3308라 ACADEMY 불가" 가정**: 폐기. HOME docker(:3308 f2r_db)는 HOME local로 추정, production과 구분 필요.
  Production 경로 실측(READ ONLY curl, credential 출력/우회 없음):
  - `https://feel2real.co.kr/api/admin/newstart/wiki/stats` → **401** (endpoint alive, ROLE_ADMIN JWT gate)
  - `https://feel2real.co.kr/api/admin/newstart/jipijigi/inventory` → **401** (동일)
  - 정상 승인 경로 존재: `POST /api/admin/newstart/wiki/review/{id}/approve` (REVIEW→ACTIVE, reason 필수;
    NewstartKnowledgeAdminController.java:123, NewstartKnowledgeService.java:254-269, SecurityConfig.java:406 ROLE_ADMIN).
    직접 SQL ACTIVE 금지는 서비스 로직으로도 강제(update()가 ACTIVE 직접 설정 거부 :149-153).
  - ingest API: `POST /api/admin/newstart/wiki`는 DRAFT 생성 → `PUT /{id}/status`로 REVIEW 전환 필요.
    `NewstartIngestionAdminController` curated/official ingest는 서버측 디렉터리 읽기 방식(remote payload 주입 불가).
    empirical_cases는 status workflow 없음(읽기 전용) → case INSERT는 SQL/migration 경로 유지.
  - **미해결(ADMIN 세션 후 GET /stats 대조)**: phase1 16건+K1~K7이 HOME local docker에만 있는지 production RDS에도 있는지.
    handoff의 330/356/ACTIVE=0 카운트가 어느 DB인지 미명시. production에 없으면 승인·RAG 무효 → 대조 우선.
- **ADMIN AUTH**: 미확보. 우회 없이 사용자 로그인 대기.
- **798001 K1~K7 + relations 9 보존 확인**: 재INSERT/합병 없음. 제보·추정 UNCONFIRMED 유지.

## A/B/C/D 버킷 상태표 (REV-2)
| 단계 | A newhopero 09/25~30 | B CCRS 09/24~30 | C newhopero 06월 | D CCRS 06월 |
|---|---|---|---|---|
| RAW/원문 | 9건(6037~6055) | 2건(798001,799029) | 부실우려 board 8건 확보(타 브랜치 S6, INVENTORY 참조) | 557 UNIQUE 목록 확보, 본문은 저장소 전체에 없음 |
| ARTICLE_ID 매핑 확정 | 9/9 | 2/2 | 8건(부실우려 한정) | 0/557 미확정(dump에 필드 없음) |
| ORIGINAL CHECKED | 9 | 2 | 8 | 0 |
| STRUCTURED | phase1 SQL(designated5+A9+B2) | +K1~K7, relations9 | 0 | 0 |
| DEDUPED | articleId unique | articleId unique | 0 | title-level DRAFT, canonical 미확정 |
| EDITORIAL READY | DRAFT_924on_roundup(A·B통합) | 동 좌 | 0 | 0(원문 필요) |
| DB REVIEW | 실행 대상 DB 미검증(HOME-local 추정) | 동 좌 | 0 | 0(원문 0건 — INSERT 금지) |
| DB ACTIVE | 운영 미존재(census 확인) | 운영 미존재 | 운영 미존재 | 0 |
| RAG VERIFIED | NOT RUN | NOT RUN | NOT RUN | NOT RUN |

9/24 newhopero 기존 게시물: **EXISTING_PUBLISHED** — 신규 게시 제외, DB 누락 여부 대조는 미実施(ADMIN/browser 필요).
분류 수치(P1 170/P3 180/LOW 140/EXCLUDED 67)는 제목 휴리스틱 **DRAFT / NEEDS_ORIGINAL** — 확정 통계 사용 금지.


## 재개 순서 (브라우저/ADMIN 세션 복구 시)
0. ADMIN 세션(사용자 로그인) 확보 후 `GET /api/admin/newstart/wiki/stats`+`/review` 큐로 phase1 16건·K1~K7의 실재 DB 확인(HOME-local vs production) — 대조 결과에 따라 SKIP/재ingest 결정.
1. 브라우저: 새 탭에서 검색 API fetch 방식(스크롤 없는 페이징)으로 CCRS 6월 557건 articleId 매핑 → chunk1 경계 duplicate/누락 확정 → canonical DEDUPE 확정.
2. P1 상위→P2/P3 판별(원문)→gw/v4 원문+댓글 수집(1회 1~2건, PII 마스킹) → STRUCTURE(9유형 분리) → DRAFT 승격.
3. newhopero 6월 스캔(C) 동일 절차.
4. DB 대조·idempotent REVIEW INSERT(phase2 SQL, SOURCE_COMMUNITY 분리) + June↔348/349 relation.
5. ADMIN 승인 → ACTIVE → RAG TEST A~D → DRAFT_924on_roundup 게시(+6월편 작성·게시) → FINAL REPORT.


## REV-3 (인벤토리·운영 census)
상세: `ARTIFACT_INVENTORY_2026-09-30.md`. 운영 RDS = cases 107 / knowledge 119 ALL ACTIVE / REVIEW 0 (HOME 로컬 330/356과 별개). phase1 16건·K1~K7·relations 9는 운영에 없음 → ADMIN 세션 후 정상 REVIEW ingest 필요.
