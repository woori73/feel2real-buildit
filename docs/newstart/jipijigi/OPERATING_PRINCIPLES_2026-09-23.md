# NewStart 지피지기 Agent 운영 원칙 (OPERATING PRINCIPLES, 2026-09-23)

지피지기 Agent의 모든 조사·답변·초안 작업에 적용되는 **계약**이다. 선택 사항이 아니다.
새 문서 체계를 만들지 않고 이 폴더(`docs/newstart/jipijigi/`)의 상시 문서로 둔다.
상태·구현 설명은 [`KNOWLEDGE_INVENTORY_2026-09-23.md`](KNOWLEDGE_INVENTORY_2026-09-23.md),
게시·API 실측은 [`NAVER_API_AUDIT_2026-09-23.md`](NAVER_API_AUDIT_2026-09-23.md)에 있다.

## 1. 답변 검색 순서 (고정 — STEP1 → STEP5)

`step_no`는 코드 상수와 1:1이다 — `JipijigiResearchTrace.STEP_INTERNAL..STEP_EXTRA_AGENCIES`
(`JipijigiResearchService.research()`가 이 순서대로 실행하고 `newstart_jipijigi_research_traces`에 남긴다).

| STEP | 소스 | 실제 조회 대상 | 강제 사항 |
| --- | --- | --- | --- |
| 1 | **NewStart 내부 DB/RAG** | `newstart_knowledge_items`(`status=ACTIVE`만) + **축적 답변 세션** `newstart_jipijigi_answer_sessions`(`review_state=ACCEPTED`만) | ACTIVE/ACCEPTED-only — REVIEW·CANDIDATE가 답변에 새지 않음 |
| 2 | **신용회복위원회(신복위)** | `newstart_official_updates`(`source_type=CCRS`, 발행분) + 신복위 계열 `newstart_knowledge_items` | 외부 사례 검색 **1순위**, 저장된 것만 근거로 씀 |
| 3 | **부실차주 카페** (새출발기금 부실차주 라이브러리, clubid 31312978) | `newstart_empirical_cases` (수집 사례 DB) | 외부 사례 **2순위**, 원자료는 `docs/newstart/research/article_json/` |
| 4 | **공식 기준 검증** | `newstart_official_updates`(`review_status=PUBLISHED`) 전 소스 | 수치·요건은 여기서 최종 검증 — **항상 수행**(STEP1 충분과 무관) |
| 5 | **추가 공식기관** | 법원·금감원·한국신용정보원·금융위 등 | V1 자동 조회 없음, 필요 기관 판정만 trace에 기록 |

- 각 단계의 결과와 **빈 단계**는 `newstart_jipijigi_research_traces`(`step_no` 1~5)에 남긴다.
  근거가 없는 단계는 목록을 비워두되 `note`에 사유를 기록한다(없는 걸 있는 것처럼 쓰지 않는다).
- STEP1이 기준(`INTERNAL_SUFFICIENT_THRESHOLD=3건`) 미만일 때만 STEP2·STEP3 외부 카페로 내려간다.

## 2. 신복위 카페 = 현재 가장 풍부한 외부 사례 공급원

확인된 바로는 **신용회복위원회 네이버 카페**가 부실우려·부실차주 실제 진행 후기 밀도가 가장 높은
외부 공급원이다. 따라서 외부 사례 검색은 항상 신복위 카페를 1순위로 두고, 부실차주 카페를 2순위로 둔다.
2순위에서 먼저 답을 찾았다고 1순위 확인 사실을 지우지 않는다 — 어떤 사전을 봤는지는 trace에 남긴다.

## 3. 주요 축적 분야 (이 9개 축으로 사례를 모은다)

1. 부실차주 자격
2. 신청 / 심사
3. 추심
4. 담보채무 (부동산·차량·근저당·선순위/후순위)
5. 감면율
6. 약정 (임시안·확정안·재약정)
7. 신용정보 (장기연체 등록·해제·신용점수)
8. 법원 / 지급명령
9. 실제 처리결과

## 4. 새 사례 저장 단위 (5단, 끝까지 채워야 사례)

`질문 → 답변 → 근거 → 댓글 → 실제 결과`

| 단위 | 저장 위치 |
| --- | --- |
| 질문 | `newstart_jipijigi_questions` (uk `club_id`+`article_id`, `body_status READABLE\|UNREADABLE`) |
| 답변 | `newstart_jipijigi_drafts` (본문 + 고정 9단 섹션) → 축적본은 `newstart_jipijigi_answer_sessions` |
| 근거 | `newstart_jipijigi_research_traces` (STEP별, 출처 클래스별) + 세션의 `evidence_*` 컬럼 |
| 댓글 | 카페 등록 댓글(운영자 계정)의 article/댓글 식별자를 trace에 기록 → 세션 `source_comment_id` |
| 실제 결과 | 회원 후속 댓글·재방문 결과 → 세션 `follow_up_ref`로 연결 후 `newstart_empirical_cases` 축적 |

- 결과까지 채운 건만 "처리결과" 근거로 쓴다. 진행 중인 건은 **진행중**으로 표시하고 결과를 단정하지 않는다.
- 댓글·답변을 정정했으면 정정 이력도 같은 사례에 남긴다(네이버 댓글은 원문 수정 UI가 없어 정정 댓글이 추가된다).

## 5. 내부 DB에 충분한 근거가 있으면 불필요한 외부 재검색을 하지 않는다

STEP1(내부 ACTIVE 지식 + 승인된 축적 답변 세션, `INTERNAL_SUFFICIENT_THRESHOLD=3건` 이상)이 기준을 채우면
**외부 카페 검색(STEP2 신복위·STEP3 부실차주)을 생략**하고 trace에 "생략 사유: 내부 근거 충분"을 남긴다.
외부 재검색은 시간·노출·오염(왜곡된 사례 유입) 위험만 키운다.

- 생략 대상은 외부 카페 단계뿐이다. **STEP4 공식 기준 검증은 우리 DB 조회이므로 항상 수행한다** —
  내부 사례가 충분해도 공식 수치를 검증하지 않고 넘기지 않는다.
- `ResearchPack.externalSearched=false`로 남아, 이 답변가 외부 사전도 봤는지 여부가 저장소에 정확히 기록된다.

## 6. 새로 확인한 가치 있는 사례는 검토 후 DB에 축적하여 다음 답변에서 재사용한다

- 외부(신복위·부실차주 카페·공식기관)에서 확인한 새 내용은 즉시 ACTIVE로 승격하지 않는다.
  `REVIEW`(지식) / `PUBLISHED 대기`(공식 자료)로 넣고 **운영자 검토 후** ACTIVE.
- 승인되는 순간 다음 답변의 STEP1에서 자동으로 잡힌다. 이것이 이 DB의 존재 목적이다
  — 검색 어딘가에 묻히는 사례를 없앤다.
- 출처끼리 충돌하면 덮어쓰지 않고 `newstart_knowledge_relations`의 `CONTRADICTS`로 보존한다.

## 7. 공식 규정과 카페 경험사례를 절대 같은 근거로 취급하지 않는다

- 라벨 5종을 유지한다: **[공식 기준] / [우리 DB 사례] / [신복위 카페 사례] / [부실차주 카페 사례] / [운영자 자료]**
- 커뮤니티 문장을 공식 규정처럼 쓰지 않는다. 엔티티에서 `SourceType.CAFE_ARTICLE/CAFE_COMMENT`를
  `OFFICIAL_*`로 승격하는 경로는 존재하지 않는다(`JipijigiResearchTrace.SourceClass`로 단순 매핑만).
- 사례 하나로 결과를 일반화하지 않는다. 감면율·승인 여부·법원 판단·채무조정 결과는 확정 예측하지 않는다.
- **운영자 구두 확인**은 [운영자 자료]로 두고, 공식 원문에서 확인하기 전까지 [공식 기준]으로 승격하지 않는다.

## 8. 답변 완료 시 그 세션을 자동 축적하고, 다음 질문에서 외부 검색보다 먼저 조회한다

구현: `JipijigiAnswerSessionService` + `newstart_jipijigi_answer_sessions` (V1005).

**저장 시점 — 답변 생성 성공 즉시.** `JipijigiWorkflowService.createDraft()`가 초안·trace를 저장한 뒤
같은 트랜잭션에서 세션 행을 만든다. **게시 성공 여부와 관계없이 답변 자체는 저장한다**
(`published_as=NONE`이어도 상관없다. V1은 NAVER 게시가 차단되어 있으므로 이게 정상 경로다).
소개글(INTRO, 조사 패키지 없는 초안)은 답변이 아니므로 저장하지 않는다.

**저장 항목**

| 구분 | 컬럼 |
| --- | --- |
| 원 질문 | `question_text` (+ `question_id`, `source_club_id`, `source_article_id`) |
| 최종 답변 | `answer_title`, `answer_body`, `answered_at` |
| 질문 유형·주제 | `question_type`(QUESTION_ANSWER/RESEARCH_REPORT), `question_topic`(§3의 9개 축) |
| 내부 DB 근거 | `evidence_internal` (JSON, STEP1) |
| 신복위 카페 사용 글·댓글·링크 | `evidence_ccrs` (refId/refUrl 포함) |
| 부실차주 카페 사용 글·댓글·링크 | `evidence_borrower` (refId=article 번호, refUrl=원문 링크) |
| 공식자료 출처·링크 | `evidence_official` |
| DB_hits | `db_hits` (STEP별 건수 + total + gap 건수) |
| 외부검색 여부 | `external_searched` |
| 근거 신뢰도·종류 | `confidence`(HIGH/MEDIUM/LOW) + 각 evidence의 `sourceClass`/`refType` |
| 운영자 수정 여부 | `operator_edited` (수정되면 `answer_body`도 갱신) |
| 실제 게시·댓글 여부 | `published_as`(NONE/POST/COMMENT), `published_url`, `source_comment_id` |
| 후속 연결 ID | `follow_up_ref` (이후 회원 후속 댓글·실제 결과 식별자) |

**dedupe / idempotency** — `fingerprint` = SHA-256(정규화 질문 + 근거 ref 세트). `uk_jipijigi_session_fp`로
동일 질문·동일 근거의 중복 저장을 막고, 저장 전 `findByFingerprint`로 재사용 여부를 판정한다.
**같은 질문이라도 근거 세트가 다르면 새 세션**이다(추가 조사는 보존해야 할 정보). `draft_id`도 UNIQUE라
한 초안은 한 번만 축적된다.

**재사용 규칙** — `JipijigiResearchService.research()`는 STEP1에서 `review_state=ACCEPTED`인 세션만 조회한다.
이 분류는 `SourceClass.INTERNAL_CASE` + `RefType.ANSWER_SESSION`으로 고정되며,
**Agent가 만든 답변을 `OFFICIAL_RULE`으로 승격하는 경로는 존재하지 않는다.** 승인 전 `CANDIDATE`,
반려된 `REJECTED`는 검색되지 않는다(§6의 "검토 후 축적"과 같은 규칙).

**노출 보고는 운영자 입력만** — V1에 게시 클라이언트가 없으므로 `POST /drafts/{id}/exposure-report`로
운영자가 보고한 실제 게시/댓글 URL·댓글 ID만 저장한다. Agent가 스스로 게시 성공을 기록하지 않는다.

## 2026-09-23 실측 결과 (이 원칙을 처음 적용한 답변)

- 질문 article 6024(부동산 차량 등 담보) — 차담보 신규 실행·부실우려 실효 후 부실차주 전환 3문항.
- STEP1 실패가 맞았다: 로컬 `f2r_db.newstart_knowledge_items` 82건 중 **ACTIVE 0건**(전부 REVIEW),
  `newstart_empirical_cases` 0건, `newstart_official_updates` 0건 → STEP3·STEP4·운영자 정리자료로 내렸다.
- 부실차주 카페 라이브 검색 `차담보` 7건 → 원문 3건 확인(149, 2792, 1240). 149·2792는 [부실차주 카페 사례],
  1240는 답변 없는 질문이라 근거로 쓰지 않았다.
- "자동차는 홈택스 조회 시세 4,000만원 이하 → 재산가액 0원"은 우리 DB(공식 정리표)에 없고
  **운영자 확인 사항**으로 접수되었다 → [운영자 자료]로 라벨링, 공식 원문 확인은 대기.
  (공식 정리표에서 홈택스는 비상장주식 항목에만 등장하고, 자동차는 "동산 조회 중심"으로만 기록됨.)
- 게시 주체: 운영자 계정. Agent는 초안·조사만 하고, 모든 답변 하단에 고지를 남긴다.

## §8 축적 저장소 실행 결과 (V1005, 같은 날)

- `JipijigiAnswerSessionDbTest`(H2 MySQL 모드, 실제 JPA)로 요청 흐름을 증명했다 — 6 tests / 0 failures:
  답변 생성 성공 즉시 자동 저장 → `review_state=CANDIDATE`에서는 RAG 미노출 → `approve` 후 `ACCEPTED`가 되어
  STEP1에서 `ANSWER_SESSION`+`INTERNAL_CASE`로 재검색 → 축적 3건이면 STEP2·STEP3 외부 카페 검색 생략
  (`externalSearched=false`, "생략 사유: 내부 근거 충분") → 동일 질문·동일 근거 재실행은 `fingerprint`로 1건 유지,
  근거가 다르면 2건째 저장 → `edit`는 `operator_edited=true` + 최종 답변 갱신, 게시/댓글 보고는 `published_as`/
  `source_comment_id`/`follow_up_ref`로 분리 기록, INTRO·`REJECTED`는 저장/재사용 대상 아님.
- MySQL DDL은 로컬 worktree 스키마 `f2r_newstart_jipijigi`에 직접 적용해 28개 컬럼·`uk_jipijigi_session_fp`·
  `uk_jipijigi_session_draft`·review/question/article 인덱스 실측 확인. **프로덕션 DB에는 적용하지 않았다.**
- 남은 구멍: 화면에서 운영자가 실제 게시/댓글 결과를 입력하는 UI가 아직 없다(API `POST /drafts/{id}/exposure-report`만 존재).
  그 전까지 `published_as`는 `NONE`으로 남으며, 이는 "게시 안 함"이 아니라 "미보고"다 — 둘을 혼동하지 않는다.

## 필수 참조

- 구현·테스트 상태: [`KNOWLEDGE_INVENTORY_2026-09-23.md`](KNOWLEDGE_INVENTORY_2026-09-23.md)
- 게시 API·정책: [`NAVER_API_AUDIT_2026-09-23.md`](NAVER_API_AUDIT_2026-09-23.md)
- 테이블 계약: [`../../../docs/DOMAIN.md`](../../../docs/DOMAIN.md) "NewStart 지피지기 Agent V1"
- ADMIN API 계약: [`../../../docs/API.md`](../../../docs/API.md) "NewStart 지피지기 Agent ADMIN API"
