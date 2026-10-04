# NewStart 카페 4개 게시판 수집 인수인계 (2026-10-04)

## 기본 정보
- 카페: 새출발기금 부실차주 라이브러리 (https://cafe.naver.com/newhopero, clubId 31312978)
- 대상 게시판(지시 §1의 4곳만): menuId 3 부실차주 신청 / 4 부실차주 임시안부터 확정안 / 5 재취업·재창업 감면수익+정부 지원 / 9 하랑랑 스페셜
- 패키지: `docs/newstart/research/cafe-4boards-20261004/`
- tranch 정책: 사용자가 선택한 **고가치 우선 110건**. 전량 수집이 아니며 미순위 후보는 아래 "다음 단계"에 명시.

## 조사 / 수집 결과

| menuId | 게시판 | 조사 | 기존중복(제외) | 가입인사·등업(제외) | 신규후보 | tranch 수집 | 원문 보존 | 운영자 글(반영 제외) | 반영 후보 회원 자료 | unit | 페이로드 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 3 | 부실차주 신청 | 350 | 9 | 3 | 338 | 30 | 30 | 4 | 26 | 197 | 197 |
| 4 | 부실차주 임시안부터 확정안 | 167 | 2 | 1 | 164 | 45 | 45 | 7 | 38 | 341 | 341 |
| 5 | 재취업·재창업 감면수익+정부 지원 | 95 | 2 | 0 | 93 | 35 | 35 | 25 | 10 | 121 | 121 |
| 9 | 하랑랑 스페셜 | 32 | 0 | 17 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 계 |  | 644 | 13 | 21 | 595 | 110 | 110 | 36 | 74 | 659 | 659 |

- menuId 9: 32건 중 15건은 24.11.05에 등급 999 운영자가 올린 동일 형식의 정보 링크 정리글, 17건은 가입인사·등업글.
  운영자 정리글은 NewStart 수집 범위(회원 정보글만 취합)에서, 가입인사·등업은 지시 §5에서 제외 → **수집 0건**.
  §5 문언(경험·정보글 포함)과 충돌하므로 사용자 확인 필요.
- **운영자(agent) 작성 게시글 36건**(등급 999 / 카페매니저 '지피지기자산'): 원문은 `articles/`에 보존하되
  본문 단위·그 글 내 운영자 단위 **69unit를 반영 후보에서 제외**했다(수집 범위 = 회원 정보글만 취합).
  해당 글 아래의 **회원 댓글 단위**(94unit)는 취합했다. 제외 목록은 `qa_units.json` `scopeDropped`,
  글 목록은 전달본 MD 부록에 표로 남겼다.
- unit 유형(반영 후보 659): COMMUNITY_OPINION 369, STAFF_ANSWER 83, QUESTION 59, ACTUAL_CASE 59,
  MEMBER_EXPERIENCE 43, ANSWER 29, COUNSELOR_CLAIM 17 / 잡담·경조사 noise drop 18건

## 산출물
- 원문 보존: `articles/article_{id}.json` (110) — 게시판명·menuId·articleId·제목·작성일·본문·댓글(대댓글 포함)·원문 URL·수집일·분류·historical 여부 보존
- 인덱스: `INDEX.json` (게시판별 조사/중복/제외/수집/품질 플래그)
- 목록 실측: `enumeration/menu{3,4,5,9}*.json`, 기존 articleId 기준집합 `enumeration/baseline_ids.json` (404)
- 1차 구조화: `qa_units.json` (units / dropped / scopeDropped / articleMeta)
- 지식화·반영 대기: `load_batches/manifest.json` + `batch_00..06.json` (659행, 14필드 계약)
- 중복 검수: `cafe_4boards_dedup_report.json`
- 상태: `collection_state.json`, `checkpoint.json`
- 생성 도구: `docs/newstart/research/tools/build_cafe_4boards_raw.py` / `build_cafe_4boards_units.py` /
  `build_cafe_4boards_dedup_report.py` / `build_cafe_4boards_state.py`
- 로더: `docs/newstart/research/tools/load_cafe_4boards_prod.py` (b6 로더와 동일 계약, 기대 개수는 manifest에서 읽음)
- 실행창구: `.github/workflows/newstart-cafe-4boards-load.yml` (기본 DRY_RUN, insert-only, approve 호출 없음)
- Library 전달파일: `newstart_cafe_4boards_2026-10-04_raw.json`, `newstart_cafe_4boards_2026-10-04_library.md`
  (Qoder는 파일 제작까지만 수행 — ChatGPT Library 업로드 없음)

## 재사용한 기존 계약 (신규 로직 발명 없음)
- unit 분류: `build_qa_units.process_article` (부실우려 b6 PHASE1 STEP2) import 재사용, menuId/boardId만 기사 값으로 교정
- payload 14필드·tags·summary·content 절단 규칙: `load_cafe_qa_prod.py` 시그니처와 동일
- 상태 흐름: DRAFT 생성 → REVIEW 이동만. ACTIVE 승인은 ADMIN 화면에서 사람만 가능(API로 승인하지 않는다)
- 기존 ACTIVE/CASE 행은 수정·삭제·재작성하지 않는다(insert-only, exact-match skip)

## historical(과거 임시안) 처리
- menuId 4 반영 후보(회원 글 38건 / 341 unit)는 RAW에서 `historicalCandidate=true`, 페이로드 tags에 `historicalCANDIDATE=true`.
- `과거 임시안~확정안` 게시판 성격상 현재 시행 규정이 아닌 **역사 자료**로 취급.요약문에도 "과거 임시안 후보" 표기.
- 스키마에 `historical` 컬럼은 존재하지 않아 임의 추가 하지 않았고, 적용 시점도 추정하지 않았다(effectiveFrom/To 미부여 → 판정은 사람).

## 품질 플래그 (정직하게 남김)
- `EMPTY_BODY` 9건: 본문이 이미지/첨부만인 글. 원문 URL과 첨부 메타는 보존했으나 텍스트 본문은 없음.
- `COMMENTS_PARTIAL` 32건: 스레드가 긴 글에서 캡처 댓글 수가 목록 선언 count보다 적음(둘 다 기록).
- unit 659행은 원문 구조화가 아닌 **1차 지식화 후보**이며 officialCheck=NOT_CHECKED. 회원 경험을 공식 규정처럼 제시 금지.

## 다음 단계 (사용자 전용 실행)
1. PR 검수 → `newstart-cafe-4boards-load.yml` **DRY_RUN** 실행 (운영 DB read-only exact-match 실측)
2. 결과 확인 후 **WRITE_REVIEW** 실행 (미존재 행만 REVIEW 적재)
3. ADMIN 화면에서 REVIEW → ACTIVE 승인 (사람)
4. 다음 tranch: menuId3 308 / menuId4 119 / menuId5 58 미순위 신규 후보

## 주의사항
- 표본은 특정 기간의 회원 자발적 게시글이다. 전체 부실차주 통계로 일반화 금지.
- 닉네임 외 식별자(memberKey/baMemberKey)는 보존하지 않았다. 본문 내 개인정보(번호·계좌 등)는 활용 전 마스킹 필요.
- `articles/` 원문 보존 폴더는 수정 금지. 재생성은 tools 스크립트로만.
- 운영자 글 36건은 삭제하지 않고 원문을 보존했다. 각 글의 `scopeExcluded="OPERATOR_AGENT_POST"` 플래그가 제외 근거라서,
  범위를 되돌리려면 `build_cafe_4boards_units.py`의 filter 조건 한 줄만 바꾸고 재생성하면 된다(수치는 자동 재계산).
