# HOME→ACADEMY HANDOFF — 2026-09-30 (A to Z Cafe Agent / PHASE 1 부실우려)

AGENT: A to Z Cafe Agent (부실우려 + 부동산·차량 등 담보 → NewStart DB / A to Z / 지피지기 retrieval)
PROJECT: woori73/feel2real-buildit

## [CURRENT TASK]

PHASE 1 STEP 1(부실우려, menuid=6) 파이프라인의 ①RAW 확보 단계.
확보된 249 articleId 중 본문/댓글이 없던 248건을 **article.cafe gw/v4 JSON API**로 1회씩 읽어
원문+댓글+운영자답변+이미지/첨부+정확한 타임스탬프를 보존완료. ②정보성 판정/Q&A 분해 직전 시점에서 종료.

## [COMPLETED] — 검증됨

1. board 6 articleId 249개 전수 목록 재확보 없이 재사용 (`docs/newstart/research/cafe-qa-phase1-b6/board6_ids_1.json`).
2. **248/248 수집 성공, 실패 0, 중복 없음** (`api_a01..a09 + api_p01`, err=[]).
3. 게시판 검증: 수집 전 건 `result.article.menu.name == "부실우려"`, `menuId == 6` → **248건 전부 일치** (오수집 0).
4. RAW 보존 산출물: `docs/newstart/research/cafe-qa-phase1-b6/articles/article_{id}.json` **248개 파일**, 1,864,410 bytes.
   - 필드: title/author/createdAt(ISO)+createdAtMs(epoch)/viewCount/commentCount/bodyRaw[]/bodyNormalized/**bodyHtml(원본)**/images/attachments/comments[]/collectionStatus/parseWarnings
   - 댓글 필드: commentId, parentCommentId(isRef→refId), author, memberGrade, createdAt, text, isAuthor, **isOperator**, isDeleted
5. 댓글 무결성: v4 인라인 댓글은 size=10 페이징 한도가 있어 `commentCount != 수집수` 9건(33,1841,1858,2051,2134,2676,3600,4050,5393)을
   `gw/v3/cafes/31312978/articles/{id}/comments?page=1&size=200`으로 덮어씀 → **최종 658건 댓글, commentCount 불일치 0**.
6. 코퍼스 규모: 658 댓글 중 **카페매니저(운영자) 답변 166건**, 운영자 답변이 1건 이상인 글 **103건**.
   본문 길이 중간값 181자, 300자 이상 65건. 작성일 범위 **2024-10-24 ~ 2026-09-21** (2025-09 이후 129건).
7. 개인정보/시크릿 스캔 (text 필드 한정: 휴대폰/유선/이메일/계좌/주민번호/9자리이상 숫자):
   **유일 매칭 1건 = article 33의 `02-2169-7028` → 신용회복위원회 공개 FAX 번호(기관 대표번호, 회원 안내 가치 있음).** 개인 휴대폰/계좌/주민번호/이메일 **0건**. token/secret 0건.
   (HTML/URL 제외 텍스트만 스캔. long-digit 매칭은 body/comments에서 0건.)
8. 수집 재현 스크립트 커밋: `docs/newstart/research/tools/build_board6_raw.py` (dump → per-article RAW, 결정적).
9. §22 카페 WRITE 금지 준수: **글쓰기 0, 댓글 0, 수정 0, 삭제 0, 임시저장 0**. 이전 세션의 공지글 초안(`work-output/notice_20260930_body.txt`)은 **미게시 상태로 폐기**.

## [PARTIAL] — 정확한 중단 지점

- **② 정보성 vs 노이즈 판정 + Q&A/사례 분해: 미착수.** RAW 248건은 파일로 완성돼 있으므로 브라우저 없이 로컬에서 진행 가능.
- ③ 중복검사(articleId/URL/fingerprint/기존 Q&A/기존 CASE): 미착수. (INSERT 직전 재확인 필수)
- ④ 전용 CAFE_QA 인제스트 신설(사용자 승인 완료된 1번 안): **코드 0줄**. 미착수.
- ⑤ 운영 DB 증분 / ⑥ A to Z 매핑(`atoz:7-x` 태그 방식) / ⑦ A_TO_Z_CONTENT_READY / ⑧ 지피지기 retrieval QA: 미착수.
- PHASE 2(부동산·차량 등 담보, menuid=7): **PHASE 1 PASS 전까지 착수 금지** 상태. 착수 0.

## [NOT STARTED]

- Java: `NewstartCafeQaIngestService` + `POST /api/admin/newstart/wiki/ingest/cafe-qa` + unit test (given/when/then)
- `newstart-seed.yml`에 CAFE_QA phase 추가(dry_run 기본 true)
- 로컬 MySQL(`localhost:3308/f2r_db`, `./scripts/run-local.sh`) 검증
- 운영 DB 증분: dry_run=true → 보고 → dry_run=true false 2단계 (REVIEW만, ACTIVE 직접 SQL 금지)
- A to Z STEP 7(부실우려→부실차주) 콘텐츠 READY 문서
- commit/push/PR (현재 브랜치 커밋 0건)

## [FAIL / BLOCKER]

1. **iframe 렌더링 수집은 폐기(속도·무결성)**. 재현 불가/타임아웃 빈발(15s 한도), 댓글에 UI 노이즈 섞임.
   → **사용할 것: `fetch('https://article.cafe.naver.com/gw/v4/cafes/31312978/articles/{id}?menuId=6&useCafeId=true',{credentials:'include'})`**
   30건/2.8초, JSON에 subject/writer/writeDate(ms)/readCount/commentCount/contentHtml/attaches/comments.items 전부 포함.
   - 댓글 10초과 글은 v4가 10개만 줌 → `gw/v3/.../comments?page=1&size=200` 사용 (v1 계열은 500 에러).
   - 다른 게시판: `menuId`만 바꾸면 됨(PHASE 2는 `menuId=7`), 응답의 `menu.name`으로 게시판 검증.
2. 본문 0자 2건: **540(변제금 납부개월수에 따른 미납실효), 3768(부실우려 접수했는데 이런문자가 왔어요)** — 둘 다 이미지 1장만 있는 이미지전용 글.
   collectionStatus=`EMPTY_BODY`로 보존. 이미지 OCR/수기 구조화는 미착수(필요 시 2건만 처리).
3. PC 세션: 로컬 AWS credential 없음(`aws sts` NoCredentials) → 운영 DB 조회/쓰기는 GitHub Actions 워크플로우로만.
4. 충돌 회피: `templates/newstart/a-to-z.html`은 다른 Agent 편집 중 → **수정 금지, A_TO_Z_CONTENT_READY까지만** (변경 없음).

## [DATA]

```
BEFORE (main 기준):
  RAW(board6 본문): 1건 (article_5869.json)
  STRUCTURED: 0
  DUPLICATE 검사: 0
  REVIEW: 0 / ACTIVE: 0 (이번 작업에서 건드린 DB 없음)
  FAILED: 0
AFTER (이번 세션):
  RAW: 248건 (docs/newstart/research/cafe-qa-phase1-b6/articles/), COMPLETE 246 + EMPTY_BODY 2
  STRUCTURED: 0 (미착수)
  DB: 0건 INSERT / 0건 UPDATE / 0건 DELETE   ← 운영 DB 불변
  knowledgeId/caseId/batchId: 없음 (DB 쓰기 0이므로)
```

- 다음 INSERT 대상 후보 articleId: 위 248개 전수 = `docs/newstart/research/cafe-qa-phase1-b6/INDEX.json` 의 `articles[].articleId`
- 이미 DB에 있을 수 있는 겹침: `newstart_empirical_cases.article_id`·`newstart_knowledge_items`와 **INSERT 직전 재확인 필수** (main의 article_json 5869는 board6 겹침 1건 확인됨).
- batchId 규칙(신설 시): `cafe-qa-b6-2026-09-30` 권장.

## [CODE]

- repository: `woori73/feel2real-buildit`
- branch: `feat/newstart-cafe-qa-phase1-20260930` (origin/main 50c7b9e2에서 분기)
- commit: `8983434f` docs(newstart): PHASE1 부실우려 게시판 248건 RAW 전수 보존 + ACADEMY handoff
- HEAD: `8983434f` — **origin에 push 완료**(tracking = origin/feat/newstart-cafe-qa-phase1-20260930)
- PR: 미생성 (docs/자료만; src·migration·template 변경 0건). 생성 URL: https://github.com/woori73/feel2real-buildit/pull/new/feat/newstart-cafe-qa-phase1-20260930
- CI: 미실행 (main 미병합, Build Impact 없음)

## [FILES]

생성(커밋 대상):
- `docs/newstart/research/cafe-qa-phase1-b6/articles/article_20.json … article_6012.json` (248)
- `docs/newstart/research/cafe-qa-phase1-b6/INDEX.json` (248 인덱스·상태·overlay 목록)
- `docs/newstart/research/cafe-qa-phase1-b6/board6_ids_1.json`, `board6_census.json` (출처/증거)
- `docs/newstart/research/tools/build_board6_raw.py`
- `docs/newstart/HOME_TO_ACADEMY_HANDOFF_2026-09-30_newstart-atoz-cafe-agent.md` (본 문서)

로컬 보존(미커밋, 재수집 아님 — 위 파일이 정식 사본):
- `work-output/batches/api_a01..a09.json`, `api_p01.json`, `api_comments_full.json` (브라우저 fetch 원 dump)
- `work-output/batches/raw_g01/g02/c01/w01..w09.json` (폐기한 iframe 수집물, 교차검증용)

## [WORKTREE]

- 상태: docs 범위 clean & push 완료. `work-output/` 만 untracked 잔존(원 dump라 유실돼도 RAW 재현 가능).
- untracked(커밋): `docs/newstart/research/cafe-qa-phase1-b6/`, `docs/newstart/research/tools/build_board6_raw.py`, 본 handoff
- untracked(미커밋): `work-output/**`
- unpushed: 없음 — 8983434f origin push 완료
- 다른 Agent 변경 건드림 없음.

## [BROWSER / ADMIN]

- 마지막 페이지: `https://m.cafe.naver.com/ca-fe/web/cafes/31312978/menus/6/articles/write` (이전 세션 잔여. **입력 0, 저장 0** — §22. 글쓰기 화면에 머물지 말고 게시판 목록/글 단위로 이동)
- 로그인: 네이버 카페 회원 세션 **ACTIVE(읽기 가능)**. gw/v4가 200을 반환함.
- ADMIN SESSION(feel2real.co.kr /api/admin/newstart): **FAIL / 미검증** — 이번 세션에서 운영 관리자 호출 0회, 로컬 AWS credential 없음.
- 학원 첫 동작: 새 탭에서 `fetch(gw/v4 …)` 1건으로 200 확인 후 진행.

## ACADEMY FIRST ACTION

`docs/newstart/research/cafe-qa-phase1-b6/articles/` 의 248개 RAW 파일만 입력으로 사용해, 브라우저 재수집 없이
본문 300자 이상 65건 + 운영자(isOperator) 답변이 붙은 103건을 우선으로 정보성/노이즈를 판정하고
QUESTION/ANSWER/ACTUAL_CASE/MEMBER_EXPERIENCE/COUNSELOR_CLAIM/OPERATOR_GUIDE 증거단위로 분해해
`docs/newstart/research/cafe-qa-phase1-b6/qa_units.json` 으로 내 놓아라 (INSERT는 중복검사 후 전용 CAFE_QA 채널 신설 이후에만).

## ACADEMY NEXT

1. 위 Q&A 분해 → evidence 라벨(OPERATOR_GUIDE/CAFE_QA/CAFE_CASE/MEMBER_EXPERIENCE/COUNSELOR_CLAIM/COMMUNITY_OPINION) + OFFICIAL_CONFIRMED/CONFLICT/NOT_CHECKED 분리 기록.
2. `newstart_knowledge_items`/`newstart_empirical_cases`/relations 스키마·dedup_key(V1012) 재확인 후 articleId/URL/fingerprint 중복검사.
3. 승인된 1번 안(전용 CAFE_QA ingest: 서비스+admin endpoint+seed workflow phase, status=REVIEW 강제) 구현 → 로컬 MySQL 검증 → unit test.
4. 운영 반영: dry_run=true → 보고 → dry_run=false, REVIEW만 생성, 직접 SQL ACTIVE 금지, 기존 자료 삭제/덮어쓰기 금지.
5. A to Z: STEP 7 계열 단계에 `atoz:7-N` 태그 + A_TO_Z_CONTENT_READY 문서(template 미수정) → 지피지기 LIKE retrieval QA(활성 ACTIVE 키워드 검색 실증).
6. PHASE 1 PASS 보고서(§14 카운터) 후에만 PHASE 2 menuid=7 착수 (gw/v4 menuId=7로 동일 방식).
