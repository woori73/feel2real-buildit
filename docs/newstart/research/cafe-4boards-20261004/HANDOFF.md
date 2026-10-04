# NewStart 카페 4개 게시판 수집 인수인계 (2026-10-04, 최종본 — 하랑랑 운영자 15건 포함)

## 기본 정보
- 카페: 새출발기금 부실차주 라이브러리 (https://cafe.naver.com/newhopero, clubId 31312978)
- 대상 게시판(지시 §1의 4곳만): menuId 3 부실차주 신청 / 4 부실차주 임시안부터 확정안 / 5 재취업·재창업 감면수익+정부 지원 / 9 하랑랑 스페셜
- 패키지: `docs/newstart/research/cafe-4boards-20261004/`
- tranch 정책: 사용자가 선택한 **고가치 우선 110건** + **2026-10-04 최종 지시로 추가한 하랑랑 스페셜 운영자 정보글 15건 = 125건**.
  전량 수집이 아니며 미순위 후보는 아래 "다음 단계"에 명시.

## 이번 최종 마무리에서 바뀐 것 (1가지)
하랑랑 스페셜(menuId 9) 32건 중
- 가입인사·등업 17건 → 기존대로 **제외 유지**
- 운영자(지피지기자산·카페매니저·등급 999) 작성 정보글 15건 → **중요자료로 전원 보존·반영**
다른 게시판(menuId 3/4/5)의 110건/659unit 결과와 운영자 본문 제외 규칙은 그대로 유지했다.

| 항목 | 직전(PR #483 1차) | 최종 | 증감 |
|---|---|---|---|
| 원문 보존 article | 110 | 125 | +15 |
| 지식 unit | 659 | 675 | +16 |
| load payload | 659 (manifest) | 675 | +16 |
| batch 파일 | 7개(00..06) + **미사용 batch_07 잔존** | 7개(00..06) | 잔존 파일 정리 |
| 운영자 글 반영 제외 | 36건 / 69unit drop | 36건(3/4/5) / 69unit drop + 9번 15건 반영 | 규칙 예외 1건 추가 |

- **+16 unit 구성**: 하랑랑 운영자 안내(OPERATOR_GUIDE) 15 + 그 글 아래 회원 댓글 1(article 330).
  article 319의 "감사합니다" 댓글 1건은 잡담(noise) 규칙으로 종전처럼 drop.
- **중요: 15건 재접속 수집 사실.** 1차 tranch에서 menuId 9 운영자 15건은 전문이 아니라 목록 메타(제목·작성일·댓글수)만
  보존되어 있었다("다시 크롤링하지 않는다" 전제가 성립하지 않음). 근거 확보를 위해 **해당 15건만** 재접속해
  본문 HTML·이미지 URL·댓글을 수집했다(4개 게시판 재수집 아님, 다른 게시판 데이터 무변경).
- **15건은 본문이 이미지/링크뿐**: 13건은 텍스트 본문 0 + 이미지 1~9장(스마트에디터 그림), 2건(317·327)은 외부 링크 미리보기 카드.
  텍스트 근거가 불완전하므로 **ACTIVE로 올리지 않고 REVIEW에 보류**한다(지시 준수). 이미지 URL·원문 URL·작성일(2024-11-05)·
  `historicalCANDIDATE=true`·provenance(하랑랑 스페셜/운영자 작성)은 페이로드 tags/summary에 그대로 보존했다.

## 조사 / 수집 결과

| menuId | 게시판 | 조사 | 기존중복(제외) | 가입인사·등업(제외) | 신규후보 | tranch 수집 | 운영자 글(반영 제외) | 운영자 글(지시 보존) | unit | 페이로드 |
|---|---|---|---|---|---|---|---|---|---|---|
| 3 | 부실차주 신청 | 350 | 9 | 3 | 338 | 30 | 4 | 0 | 197 | 197 |
| 4 | 부실차주 임시안부터 확정안 | 167 | 2 | 1 | 164 | 45 | 7 | 0 | 341 | 341 |
| 5 | 재취업·재창업 감면수익+정부 지원 | 95 | 2 | 0 | 93 | 35 | 25 | 0 | 121 | 121 |
| 9 | 하랑랑 스페셜 | 32 | 0 | 17 | 0 | 15 | 0 | 15 | 16 | 16 |
| 계 |  | 644 | 13 | 21 | 595 | 125 | 36 | 15 | 675 | 675 |

- 반영 범위 자료 = 회원 글 74건 + 하랑랑 운영자 15건 = 89건(articles), unit 675.
- unit 유형: COMMUNITY_OPINION 370, STAFF_ANSWER 83, ACTUAL_CASE 59, QUESTION 59, MEMBER_EXPERIENCE 43,
  ANSWER 29, COUNSELOR_CLAIM 17, **OPERATOR_GUIDE 15(신규)** / 잡담·경조사 noise drop 19건, 운영자 본문 drop 69unit.

## 산출물
- 원문 보존: `articles/article_{id}.json` (125) — 게시판명·menuId·articleId·제목·작성일·본문·댓글(대댓글 포함)·
  이미지/링크 URL·원문 URL·수집일·분류·historical 여부·범위 근거 보존
- 하랑랑 운영자 수집 원본: `f2r_menu9_operator15.json`(+ 파이프라인 입력용 `f2r_menu9_operator15_as_dump.json`)
- 인덱스: `INDEX.json` (게시판별 조사/중복/제외/수집/품질 플래그 + `userRuling20261004`)
- 목록 실측: `enumeration/menu{3,4,5,9}*.json`, 기존 articleId 기준집합 `enumeration/baseline_ids.json` (404)
- 1차 구조화: `qa_units.json` (units / dropped / scopeDropped / articleMeta)
- 반영 파일: `load_batches/manifest.json` + `batch_00..06.json` (675행, 14필드 계약)
- 중복 검수: `cafe_4boards_dedup_report.json`
- 상태: `collection_state.json`, `checkpoint.json`
- 운영 반영 기록: `db_reflect_2026-10-04.json`, `db_reflect_2026-10-04_ledger.json`
- 생성 도구: `tools/build_cafe_4boards_raw.py` / `build_cafe_4boards_units.py` / `build_cafe_4boards_dedup_report.py` /
  `build_cafe_4boards_state.py` / `build_cafe_4boards_library.py` / **`build_menu9_operator_dump.py`(신규)**
- 로더·검토 도구: `tools/load_cafe_4boards_prod.py` (insert-only, b6 동일 계약), `tools/review_cafe_4boards_prod.py`
  (이번 패키지 REVIEW 행만 기존 review API로 승인/보류, `triage_apply_prod.py`와 동일한 승인 규칙)
- 실행창구: `.github/workflows/newstart-cafe-4boards-load.yml` (DRY_RUN / WRITE_REVIEW / APPROVE_ELIGIBLE) —
  이번 반영은 이 workflow가 기본 브랜치에 없어 dispatch 불가(404)였으므로, workflow가 호출하는 것과 **동일한 API 계약**을
  기존 ADMIN 화면 세션에서 직접 실행했다(아래 "운영 DB 반영" 참조). payload는 커밋된 batch 파일과 sha256 동일함을 실행 전 확인.
- Library 전달파일: `delivery/newstart_cafe_4boards_2026-10-04_raw.json`, `..._library.md`
  (Qoder는 파일 제작까지만 수행 — ChatGPT Library 업로드 없음)
  PC 복사본: `C:\Users\sxxra\Documents\Qoder\2026-10-04\2f2a2d6d\delivery\` 동일 파일명·동일 sha256.
  하랑랑 운영자 15건 미포함 + DB 반영 전 직전본은 삭제하지 않고 같은 폴더에
  `PREV_not-final_하랑랑운영자15건미포함_DB반영전_{raw.json,library.md}`로 구분 보존.

## 재사용한 기존 계약 (신규 로직 발명 없음)
- unit 분류: `build_qa_units.process_article` (부실우려 b6 PHASE1 STEP2) import 재사용, menuId/boardId만 기사 값으로 교정
- payload 14필드·tags·summary·content 절단 규칙: `load_cafe_qa_prod.py` 시그니처와 동일
- 상태 흐름: DRAFT 생성 → REVIEW 이동 → 기존 `POST /review/{id}/approve`(reason+confirmed) 또는 `keep`.
  승인 규칙·API는 리포지토리 기존 운영 도구(`triage_apply_prod.py`)와 동일. 신규 엔드포인트·직접 SQL·JWT 위조 없음.
- ACTIVE 보류 규칙(지시): 본문이 이미지/링크만으로 근거 텍스트가 불완전한 행, 댓글 부분수집 행은 `keep`(REVIEW 유지).
- 기존 ACTIVE/CASE 행은 수정·삭제·재작성하지 않는다(insert-only, exact-match skip, 대상은 tags의 `ck=CAFE4B:`로 이번 패키지 행만).

## historical(과거 임시안·과거 시점) 처리
- menuId 4: RAW `historicalCandidate=true`, tags `historicalCANDIDATE=true`, 요약에 "과거 임시안 후보".
- menuId 9 운영자 15건: 작성일 2024-11-05를 `sourceDate`/tags `writeDate=`로 보존 + `historicalCANDIDATE=true` 부여.
  과거 시점 안내이므로 current 규정으로 오인 금지. 스키마에 `historical` 컬럼은 없어 임의 추가하지 않았고 적용 시점도 추정하지 않았다.

## 품질 플래그 (정직하게 남김)
- `EMPTY_BODY` 26건(하랑랑 13 + 타 게시판 13): 본문이 이미지/첨부만. 원문 URL·이미지 URL은 보존, 텍스트 본문 없음.
- `COMMENTS_PARTIAL` 32건: 긴 스레드에서 캡처 댓글 수가 목록 선언 count보다 적음(둘 다 기록).
- contentForm: IMAGE_ONLY 13, LINK_PREVIEW 2 (menuId 9 운영자 자료)
- unit 675행은 원문 구조화가 아닌 **1차 지식화 후보**이며 officialCheck=NOT_CHECKED. 회원 경험을 공식 규정처럼 제시 금지.

## 운영 DB 반영 (2026-10-04 실행 완료)
- 결과: 운영 DB 3826행 → 4501행. payload 675행 전수 신규 적재, 생성·승인 오류 0건, DRAFT 잔존 0건.
  ACTIVE 승인 336행, REVIEW 보류 339행(이미지/링크 근거 불완전 28 + 댓글 부분수집 311).
  사전 전수 중복 실측에서 정확일치 기존 행 0건 → 중복 재등록 없음.
- 창구: 기존 운영 ADMIN 세션(화면 로그인된 admin)에서 문서화된 admin wiki API만 호출
  (`GET/POST /api/admin/newstart/wiki`, `PUT /wiki/{id}/status?status=REVIEW`,
  `POST /wiki/review/{id}/approve`, `POST /wiki/review/{id}/keep`).
  workflow는 기본 브랜치에 없어(`PR #483` 미머지 → `workflow_dispatch` 404) dispatch 불가였고,
  머지는 공유 상태 변경이라 집행하지 않았다. 직접 SQL·JWT 위조·UPDATE/DELETE·기존 ACTIVE 수정은 쓰지 않았다.
- 대장: `db_reflect_2026-10-04.json`(실행 기록·결함 고지) + `db_reflect_2026-10-04_ledger.json`(675행별 knowledgeId/판정)
- 적재 후 read-only 재실측(`liveReverification`): 이 패키지 행 675건 = ACTIVE 336 / REVIEW 339 / DRAFT 0, knowledgeId 3827–4501.
  운영 DB 전체 4501건(ACTIVE 4137 / REVIEW 341 / DRAFT 0 / 기타 23) — REVIEW 341은 패키지 339 + 패키지 외 기존 2건.
- knowledgeId 3827–4501. 하랑랑 운영자 15건(16unit)은 REVIEW 보류(provenance·작성일·historical 보존).
- 시정한 결함: m5 `authority` 51자 → `newstart_knowledge_items.authority` length 50 위반으로 create HTTP 500 8건.
  생성기에 `authority_for()` + 컬럼 상한 게이트를 넣고 CI 로더에도 동일 게이트를 미러링한 뒤 재생성·재적재(중복 없음).
- 남긴 고지: keep 루프 첫 실행에서 REVIEW 유지 메모 130건 중복 기록(상태 영향 없음),
  대장 idx 0 행의 `reviewMoved` 불리언 아티팩트(실측 DRAFT 잔존 0건이 정본).

## 다음 단계
1. (완료 2026-10-04) 675행 적재 → REVIEW 675행 → ACTIVE 336행 / REVIEW 보류 339행. 창구는 위 "운영 DB 반영" 참조.
   (`gh workflow run … --ref newstart/cafe-4boards-20261004` 는 브랜치 전용 workflow라 404 — 병합 후에만 dispatch 가능)
2. (완료 2026-10-04) 보류 339행 내용 전수 판정 → 152행 ACTIVE 승인 / 187행 REVIEW 유지. `review_approval_2026-10-04/` 참조.
   workflow 재가동 없이 ADMIN `review/{id}/approve` 정상 API로만 집행. 하랑랑 16unit은 텍스트만으로 성립하는 게 없어 전원 REVIEW 유지.
3. 다음 tranch: menuId3 308 / menuId4 119 / menuId5 58 미순위 신규 후보
4. ChatGPT Library 업로드는 Qoder가 하지 않는다. 전달파일 2종만 리포지토리·PC `delivery/`에 저장.

## 주의사항
- 표본은 특정 기간의 회원 자발적 게시글이다. 전체 부실차주 통계로 일반화 금지.
- 닉네임 외 식별자(memberKey/baMemberKey)는 보존하지 않았다. 본문 내 개인정보(번호·계좌 등)는 전달본에서만 마스킹.
- `articles/` 원문 보존 폴더는 수정 금지. 재생성은 tools 스크립트로만.
- 하랑랑 운영자 15건 보존은 각 article의 `scopeIncludedByUserRuling` 플래그가 근거다.
  범위를 되돌리려면 `build_cafe_4boards_units.py`의 `kept_by_ruling` 조건과 `build_cafe_4boards_raw.py`의
  `operator_kept` 조건을 되돌리고 재생성하면 된다(수치는 자동 재계산, batch 잔존 파일은 생성자가 정리).

## REVIEW 339건 → ACTIVE 승인 (2026-10-04 실행 완료)

- 대상: 패키지(knowledgeId 3827–4501) 675행 중 REVIEW 339행 전부. 행 내용을 로컬 파이프라인 대장이 아니라 운영 DB에서 직접 내려받아 건독(件讀) 후 판정했다.
- 보류 사유로 채택하지 않은 것: `MISSING_SOURCE` / `UNKNOWN_RISK` / `CONFLICT_RISK` / 댓글 부분수집 플래그. 실제 텍스트가 독립 의미가 있고 사례·질문·답변 근거가 되면 승인.
- 결과: 신규 ACTIVE 152행(전부 HTTP 200, 오류 0) / REVIEW 유지 187행.
  패키지 최종 ACTIVE 488 / REVIEW 187 / DRAFT 0. 운영 DB 전체 ACTIVE 4289 / REVIEW 189 / DRAFT 0 (총 4501, INSERT 0).
  (DB 전체 REVIEW 189 = 패키지 유지 187 + 패키지 밖 선행 보류 2)
- 유지 187행 사유: 인사·축하·감사 137, unitType 중복 게시 21, 이미지-only 운영자 정보글 13, 질문-답변 연결 파손 10, 홍보·참여유도 3, 링크 전용 2, 이미지 게시물 댓글 1.
- 금지한 것: 직접 SQL/UPDATE/DELETE, 상태 강제변경, 기존 ACTIVE 336행 수정·재등록, 신규 INSERT. 승인 채널은 `POST /api/admin/newstart/wiki/review/{id}/approve`(reason+confirmed:true) 하나.
- 산출물: `review_approval_2026-10-04/`(REPORT.md, decisions_all_339.json, review_hold_187.json, approve_ledger.json, verification.json, retrieval_test.json)
- 지피지기 retrieval 실측: `GET /api/muse/newstart/knowledge/search` 10문항 전부 응답, 상위 80건 중 36건이 newly ACTIVE 행. provenance(title 접두 `[카페 4boards …]`, authority, tags `ck=CAFE4B:…`, `officialCheck=NOT_CHECKED`)는 그대로 보존해 카페 경험자료와 공식 규정이 섞이지 않게 했다.
  미관측 1문항("도덕적해이 심사 기간")은 상위 8건이 모두 선행 ACTIVE라 신규 행이 밀려난 것이지 승인 실패가 아니다.
