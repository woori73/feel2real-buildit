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
- **BLOCKED_BROWSER**: m.cafe.ca-fe 세션이 fetchMore 루프 후 renderer hung(1+1까지 타임아웃, reload 후에도 hydration busy). 사용자 지시로 재시도 중단.
  영향: 6월 articleId 매핑(DEDUPE 확정), P1→P2→P3 원문/댓글 수집, newhopero 6월 스캔(C), 카페 게시.
- **BLOCKED_DB**: 운영 MySQL(330 cases/356 knowledge, ACTIVE=0)은 HOME PC 로컬 docker(f2r_mysql_main :3308). 이 ACADEMY PC에 컨테이너 없음.
  영향: articleId DB 대조(SKIP/보완 판정), 6월 REVIEW INSERT, ADMIN 승인→ACTIVE, RAG QA.
  직접 SQL ACTIVE 금지 규칙 유지 — ADMIN 세션 필요.
- **798001 K1~K7 + relations 9 보존 확인**: 재INSERT/합병 없음. 제보·추정 UNCONFIRMED 유지.

## 재개 순서 (브라우저/DB 복구 시)
1. 브라우저: 새 탭에서 검색 API fetch 방식(스크롤 없는 페이징)으로 CCRS 6월 557건 articleId 매핑 → chunk1 경계 duplicate/누락 확정 → canonical DEDUPE 확정.
2. P1 상위→P2/P3 판별(원문)→gw/v4 원문+댓글 수집(1회 1~2건, PII 마스킹) → STRUCTURE(9유형 분리) → DRAFT 승격.
3. newhopero 6월 스캔(C) 동일 절차.
4. DB 대조·idempotent REVIEW INSERT(phase2 SQL, SOURCE_COMMUNITY 분리) + June↔348/349 relation.
5. ADMIN 승인 → ACTIVE → RAG TEST A~D → DRAFT_924on_roundup 게시(+6월편 작성·게시) → FINAL REPORT.
