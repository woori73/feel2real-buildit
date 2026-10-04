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
- 생성 도구: `tools/build_cafe_4boards_raw.py` / `build_cafe_4boards_units.py` / `build_cafe_4boards_dedup_report.py` /
  `build_cafe_4boards_state.py` / `build_cafe_4boards_library.py` / **`build_menu9_operator_dump.py`(신규)**
- 로더·검토 도구: `tools/load_cafe_4boards_prod.py` (insert-only, b6 동일 계약), `tools/review_cafe_4boards_prod.py`
  (이번 패키지 REVIEW 행만 기존 review API로 승인/보류, `triage_apply_prod.py`와 동일한 승인 규칙)
- 실행창구: `.github/workflows/newstart-cafe-4boards-load.yml` (DRY_RUN / WRITE_REVIEW / APPROVE_ELIGIBLE)
- Library 전달파일: `delivery/newstart_cafe_4boards_2026-10-04_raw.json`, `..._library.md`
  (Qoder는 파일 제작까지만 수행 — ChatGPT Library 업로드 없음)

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

## 운영 DB 반영
- 실행: `gh workflow run newstart-cafe-4boards-load.yml --ref newstart/cafe-4boards-20261004 -f mode=<DRY_RUN|WRITE_REVIEW|APPROVE_ELIGIBLE>`
- 결과 수치(적재/승인/보류/오류)는 `collection_state.json` `dbReflection`에 기록.

## 다음 단계
1. (완료) DRY_RUN → WRITE_REVIEW → APPROVE_ELIGIBLE 실행 및 결과 검증
2. ADMIN 화면에서 보류(REVIEW 유지) 자료의 승인 여부 판단 — 근거 보강(이미지 내용 확인) 필요 자료
3. 다음 tranch: menuId3 308 / menuId4 119 / menuId5 58 미순위 신규 후보
4. ChatGPT Library 업로드는 Qoder가 하지 않는다. 전달파일 2종만 PC `delivery/`에 저장.

## 주의사항
- 표본은 특정 기간의 회원 자발적 게시글이다. 전체 부실차주 통계로 일반화 금지.
- 닉네임 외 식별자(memberKey/baMemberKey)는 보존하지 않았다. 본문 내 개인정보(번호·계좌 등)는 전달본에서만 마스킹.
- `articles/` 원문 보존 폴더는 수정 금지. 재생성은 tools 스크립트로만.
- 하랑랑 운영자 15건 보존은 각 article의 `scopeIncludedByUserRuling` 플래그가 근거다.
  범위를 되돌리려면 `build_cafe_4boards_units.py`의 `kept_by_ruling` 조건과 `build_cafe_4boards_raw.py`의
  `operator_kept` 조건을 되돌리고 재생성하면 된다(수치는 자동 재계산, batch 잔존 파일은 생성자가 정리).
