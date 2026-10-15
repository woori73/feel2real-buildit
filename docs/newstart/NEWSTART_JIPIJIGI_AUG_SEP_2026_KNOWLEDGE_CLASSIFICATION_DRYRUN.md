# NewStart 지피지기 Agent — 2026년 8월 + 9월 카페 연구지식 분류 DRY RUN (2026-09-24)

> **상태: DRY RUN ONLY. DB 쓰기 0건.** 본 문서는 분류 결과 제안서이며, 승인 전까지 어떤 MySQL 스키마에도 INSERT/UPDATE/DELETE 를 실행하지 않았다.
> **범위**: 2026-08 + 2026-09 두 달 (이전 지시의 "8월만" 범위는 폐기됨).
> **WORK B(NewStart AI → 지피지기 Agent 브랜딩)는 본 문서/본 PR에 포함하지 않는다.** 별도 브랜치·PR.

---

## 0. 결론 요약 (실측값만)

| 항목 | 실측값 | 근거 |
|---|---|---|
| 8월 수집 단위 (article+comment+followup) | **150** ( 기사 44 / 댓글 96 / 후속 10 ) | `docs/newstart/research/article_json/` 44 files |
| 9월 수집 단위 | **205** ( 기사 63 / 댓글 121 / 후속 21 ) | 63 files |
| 합계 원문 단위 | **355** | 124 article JSON |
| 지식 레이어 채택 | **167** (8월 70 / 9월 97) | §5 |
| 노이즈 제외 | **188** (8월 80 / 9월 108) | §6 |
| DB에 이미 있는 8·9월 카페 행 | **0** | §3 (카페 마커 0건) |
| OFFICIAL로 분류된 항목 | **0** | §7 |
| 제안 INSERT | knowledge_items **167**, empirical_cases **17** | §17 |
| 제안 UPDATE / DELETE | **0 / 0** | §17 |
| 제안 relation | **68** | §17 |
| 제안 status_log | **167** | §17 |
| migration | **불필요 (없음)** — 단 표현 공백 2건은 §18에 증거와 함께 보고 | §18 |

---

## 1. 선읽기 자료 (기존 분류체계 재사용, 새 taxonomy 무작정 생성 안 함)

| 파일 | 재사용한 것 |
|---|---|
| `NEWSTART_JIPIJIGI_AUG2026_RESEARCH_HANDOFF_2026-09-24.md` (**미병합 docs 브랜치** — `feel2real-buildit-docs-aug2026-archive` worktree, main에 없음) | 8월 연구 범위, 6030·6031 발행 사실, §13 NEEDS_VERIFICATION 6건, "8월 연구분 DB 적재 증적 0건" 리스크 |
| `docs/newstart/research/knowledge/KNOWLEDGE_DOCUMENT_SCHEMA.md` | SOURCE/DERIVED 분리, `contentType` 11종, `infoNature` 5종, `outcome` 4종, kbTags 조인, "경험담을 정책으로 취급 금지" |
| `docs/newstart/research/knowledge/QUESTION_TAXONOMY.md` | 8대 주제 / 38 하위 분류 → 본 문서 주제 코드의 모체 (§8 매핑표) |
| `docs/newstart/research/knowledge/CASE_INDEX.md`, `CASE_CHAIN_RESULT.md` | 사례 체인(5단위)·CASE_ID 체계 |
| `docs/newstart/research/knowledge/POLICY_CLAIMS_TO_VERIFY.md`, `CROSS_SOURCE_LINKS.md` | 검증 대기 정책 주장, 출처 간 링크 |
| `docs/newstart/research-official/ccrs/k09_부실우려_부실차주_전환.json` 외 14종 | 공식 교차검증 원본 (§12) |
| `docs/newstart/jipijigi/OPERATING_PRINCIPLES_2026-09-23.md` (미병합 `origin/feat/newstart-jipijigi-agent-v1`) | RAW→Knowledge→Provenance→Topic→Status→Relation→Approved 사다리, `source_class` 값집합 |

---

## 2. 저장소·작업 격리

- repository: `woori73/feel2real-buildit`
- worktree: `feel2real-wt-newstart-knowledge-classification` / branch `feat/newstart-aug2026-knowledge-classification` @ `origin/main` 25ef07b7
- 다른 dirty worktree 미접촉. 금지 명령(reset/restore/clean/stash/rebase/force push) 미실행.
- MERGE: **NO** / DEPLOY: **NO**

---

## 3. DATABASE READ-ONLY AUDIT (쓰기 전 실측)

### 3.1 접속 대상

| 컨테이너 | 호스트:포트 | 버전 | 스키마 |
|---|---|---|---|
| `f2r_mysql_main` | localhost:**3308** | MySQL 8.4.11 | `f2r_db`(113t), `f2r_qa387`(130t), `f2r_v985_verify`(117t) |
| `f2r_mysql_fss_e2e` | localhost:**3309** | MySQL 8.4.11 | `f2r_fss`(113t) |
| `f2r_mysql_fix_pr390_v998` | localhost:**34782** | MySQL 8.4.11 | `f2r_fix_pr390_v998`(130t), `f2r_newstart_jipijigi`(139t) |

- 접근 방식: `docker exec -i <ct> sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N --table -uroot --default-character-set=utf8mb4' < scratch.sql` — 비밀번호 미출력, stdin 파일 방식으로만 실행.
- **PRODUCTION DB: 접속하지 않음, 존재하지도 않음.** `application-local.yaml:5` = `jdbc:mysql://localhost:3308/f2r_db`, profile local/dev/stg/prd 중 로컬 실행 프로파일에 실DB 없음.

### 3.2 newstart 테이블 실태 (핵심)

| 테이블 | `f2r_db` (쓰기 후보) | `f2r_v985_verify` | `f2r_qa387` | `f2r_fss` | `f2r_newstart_jipijigi` | `f2r_fix_pr390_v998` |
|---|---|---|---|---|---|---|
| `newstart_knowledge_items` | **82** | 82 | 0 | 82 | 0 | 0 |
| `newstart_empirical_cases` | **0** | 0 | 0 | 0 | 0 | 0 |
| `newstart_knowledge_relations` | **1** | 1 | 0 | 1 | 0 | 0 |
| `newstart_knowledge_status_log` | **0** | 0 | 0 | 0 | 0 | 0 |
| `newstart_member_profiles` | 0 | 0 | 0 | 0 | 0 | 0 |
| `newstart_chat_messages` | 0 | 0 | 0 | 0 | 0 | 0 |
| `newstart_official_discovery_run` / `_updates` | 없음 | 없음 | 0 / 0 | 없음 | 0 / 0 | 0 / 0 |
| `newstart_jipijigi_*` (questions, target_boards, drafts, draft_status_log, commands, answer_sessions, research_traces = **7종**) | **없음** | 없음 | 없음 | 없음 | **있음 · 전부 0건** | 없음 |

- `f2r_db`·`f2r_v985_verify`·`f2r_fss`의 knowledge_items 82행은 **전부 `source_type=OFFICIAL_CURRENT`** (f2r_fss는 GROUP BY로 직접 확인). 즉 같은 공식 위키 ingest가 세 스키마에 반복 적재되어 있고 **카페 행은 어느 스키마에도 없다.**
- **`f2r_db`에는 jipijigi 전용 테이블이 하나도 없다.** 카페 연구지식 전용 스키마는 미병합 브랜치용으로 만든 `f2r_newstart_jipijigi`(139t · newstart 15t)에만 있고 **전 테이블 0건** (questions 0 / drafts 0 / research_traces 0 / target_boards 0).
- `newstart_ka_conversations`는 **어느 스키마에도 없음.**
- 카페 마커(`source_url LIKE '%cafe.naver.com%'`) 재측정: `f2r_db` 0 / `f2r_fss` 0 / `f2r_fix_pr390_v998` 0.
- `f2r_db.newstart_knowledge_relations`의 유일한 1행 = `from=1220 → to=1219, relation_type=CONTRADICTS, created_by=INGESTION_BOT, 2026-09-21 16:37` → 이미 있는 relation 1건은 **공식 위키 ingest 내부의 모순 표시**이며 카페 자료와 무관. relation_type 값집합에 `CONTRADICTS`가 실제 사용 중 (§17.3 명명 근거).
- 모든 COUNT는 `information_schema.TABLE_ROWS` 추정치가 아니라 `COUNT(*)` 실측.

### 3.3 "8·9월 카페 자료가 이미 DB에 있다"는 전제 → **FALSE (반증)**

`f2r_db.newstart_knowledge_items` 82행 전체 대상:

| 판정 쿼리 | 결과 |
|---|---|
| `content LIKE '%newhopero%'` | **0** |
| `source_url LIKE '%cafe.naver.com%'` | **0** |
| `title/summary/content/tags/source_url` 중 `%31312978%` | **0** |
| `tags`/`related_case_ids` 중 기사ID 패턴 `(58xx|59xx|60xx)` | **0** |
| `source_date` 2026-08-01~08-31 | **0** |
| `source_date` 2026-09-01~09-30 | 16 → 전부 `OFFICIAL_CURRENT` + `authority='부실차주 Wiki REVIEW ingest'` (2026-09-21 수집 공식 페이지) |
| 82행 구성 | `source_type` 100% OFFICIAL_CURRENT / `status` 100% REVIEW / category: 부실차주 비교 35 · 부실우려 32 · 부실차주 신청 15 |

→ **82행은 새출발기금 공식 페이지 위키 ingest 이지 카페 연구자료가 아니다.** 8·9월 카페 연구자료는 어떤 로컬 MySQL 스키마에도 0건. 데이터셋은 Git 안의 124개 article JSON만 존재.

### 3.4 쓰기 전 선행 장애물 (승인 시 반드시 먼저 해결)

1. **`f2r_db`의 V985 실패 이력**: `flyway_schema_history`에 `installed_rank=104, version=985, description='add newstart knowledge wiki', success=0` (2026-09-23 10:11:39). 즉시 INSERT 시 Flyway baseline 충돌 가능 → 재적용/repair 판단 선행.
2. **미병합 카페 스키마**: `origin/feat/newstart-jipijigi-agent-v1`의 `V999__add_newstart_jipijigi_agent.sql`이 main의 `V999__add_easy_card_event_confirmation_date.sql`과 **버전 충돌**. 이 브랜치가 먼저 머지되면 본 분류의 저장 위치 재설계 필요(§19).
3. `f2r_newstart_jipijigi`는 main V992계열 + 브랜치 V999/V1000만 적용된 별도 스키마(139t) → base newstart 8종 + jipijigi 7종이 **모두 0건**. "카페용 스키마가 이미 살아있어서 거기에 넣으면 된다"고 오인하면 안 됨 — §19의 O2 선택 없이는 `f2r_db`에 적재해야 한다.

---

## 4. 데이터셋 식별 방법

1. `docs/newstart/research/article_json/` 124 files → 각 JSON의 `writtenAt`(또는 `extractedAt`) 월로 분기: **2026-08 = 44**, **2026-09 = 63**.
2. unit 평탄화: ARTICLE(본문+제목) / COMMENT(댓글 1개씩) / FOLLOWUP(`authorFollowupResult`) → 8월 150, 9월 205, 합계 355.
3. 중복 코퍼스 대조: `data/newstart-cafe/gamian/articles/` 11 files → **11/11이 article_json과 동일 articleId** (§16).
4. DB 대조: §3.3 반증 쿼리.
5. 미수집 영역: Cafe B(신용회복위원회 공식카페 clubId 27453843)는 article_json에 **0건** → 8월분은 발행된 정리글 6031에만 존재, 9월분은 코퍼스 없음 (§20 gap).

---

## 5. 분류 결과 — 채택 167건 분포

### 5.1 unit × 월

| unit | 8월 | 9월 | 합계 |
|---|---|---|---|
| ARTICLE | 27 | 35 | 62 |
| COMMENT | 33 | 41 | 74 |
| FOLLOWUP | 10 | 21 | 31 |
| **합계** | **70** | **97** | **167** |

### 5.2 artifact intrinsic sourceType (JSON 원본 값 기준)

| sourceType | 8월 | 9월 | 합계 |
|---|---|---|---|
| CAFE_CASE | 27 | 10 | 37 |
| CAFE_QA | 0 | 24 | 24 |
| CAFE_COMMENT | 33 | 14 | 47 |
| FOLLOW_UP | 10 | 37 | 47 |
| OPERATOR_GUIDE | 0 | 12 | 12 |
| **합계** | **70** | **97** | **167** |

### 5.3 role

AUTHOR 61 / MEMBER 33 / AUTHOR_FOLLOWUP 31 / AUTHOR_REPLY 26 / OPERATOR 16 = 167

### 5.4 게시판 (기사 단위)

| 게시판 | 8월 | 9월 |
|---|---|---|
| 자유게시판 | 15 | 18 |
| 질문게시판 | 6 | 6 |
| 감면율과 확정금액 예측 게시판 | 2 | 3 |
| 부실차주 신청 | 3 | 4 |
| 부실우려 | 1 | 0 |
| 부실차주 임시안부터 확정안 | 0 | 2 |
| 재취업 재창업 감면수업 + 정부 지원 | 0 | 1 |
| 공지사항 | 0 | 1 |

---

## 6. 노이즈 제외 188건 (RAW 삭제 아님 — 분류만 제외)

규칙: 기사 = 본문 40자 미만 또는 등업/가입인사 패턴; 댓글 = 20자 미만 또는 인사성(축하/감사/ㅇㅇ/수고/화이팅) + 수치 없음 + 60자 미만.

| 구분 | 8월 | 9월 | 합계 |
|---|---|---|---|
| ARTICLE 노이즈 | 17 | 28 | 45 |
| COMMENT 노이즈 | 63 | 80 | 143 |
| **합계** | **80** | **108** | **188** |

- 제외된 45개 기사 전부가 `article_json`에 그대로 보존(RAW 불변, §13).
- 학습목적: `primary=OTHER`로 남긴 7건 중 5894(가입인사), 5992(출석체크), 6005(가입인사)는 다음 라운드에서 노이즈로 강등 후보 — 본 드라이런에서는 지식 보존 + `REVIEW`로 표기하고 강등하지 않음(판단 근거를 §22에 남김).

---

## 7. PROVENANCE 분포

| provenance | 8월 | 9월 | 합계 | 정의 |
|---|---|---|---|---|
| MEMBER_CASE | 38 | 51 | **89** | 작성자 본인의 진행/처음/결과 서술 |
| COMMUNITY_OPINION | 28 | 34 | **62** | 동료 회원 답변·추측·경험 단편 |
| OPERATOR_EXPERIENCE | 4 | 10 | **14** | 운영자 답변·예측 (공식 유권해석 아님) |
| OPERATOR_LONG_TERM_EXPERIENCE | 0 | 2 | **2** | `5948:c1`(장기 운영 축약 예측), `6009`(공지사항 안내문) |
| **OFFICIAL** | **0** | **0** | **0** | 코퍼스 내 공식 문서 없음 |
| NEEDS_VERIFICATION | 15 | 19 | **34** | §10 status 축과 동시 보유(교차축) |

**핵심 규칙 준수**: Cafe B가 "신용회복위원회 공식카페"라는 이유로 회원 글을 OFFICIAL로 격상하지 않았다. 실제로 Cafe B 코퍼스 자체가 0건이고(§4-5), Cafe A(부실차주 카페) 운영자도 **16건 중 단 1건도** 본인 답변을 규정·공지로 표기하지 않았다(15건은 댓글 답변, 1건은 사이트 오픈 공지). → OFFICIAL 0건은 고의가 아니라 실측이다.

confidence: HIGH 47 / MEDIUM 72 / LOW 48.

---

## 8. TOPIC (주제) — 기존 QUESTION_TAXONOMY 8대 카테고리 매핑

기존 카테고리(QUESTION_TAXONOMY.md)를 폐기하지 않고 코드로 매핑:

| 코드 | 기존 8대 카테고리 |
|---|---|
| REDUCTION | 1 감면율·특수감면 |
| APPLICATION / REAPPLICATION / STATUS | 2 절차·타임라인 |
| COLLECTION / COURT | 3 추심·법적 대응 |
| ASSET | 4 재산·압류·자산 보호 |
| GUARANTEE / REPAYMENT | 5 차량·담보대출 |
| CREDITOR / CREDIT_RECOVERY / FSS / BUSINESS | 6 채무 유형별 편입·제외 + 8 법인·소상공인 |
| DELINQUENCY | 2(연체 기산)·3(부실전환) 횡단 |
| INCOME / OTHER | 7 생활·복지·기타 |
| CREDIT_INFO | 2 채권양도→신용정보 변동 |

### 8.1 primary topic (1건 = 1주제)

| primary | 8월 | 9월 | 합계 |
|---|---|---|---|
| ASSET | 11 | 21 | **32** |
| DELINQUENCY | 9 | 22 | **31** |
| TERMINATION | 19 | 11 | **30** |
| REDUCTION | 17 | 6 | **23** |
| COURT | 0 | 12 | **12** |
| APPLICATION | 7 | 4 | **11** |
| REAPPLICATION | 2 | 6 | **8** |
| OTHER | 2 | 5 | **7** |
| CREDITOR | 1 | 5 | **6** |
| COLLECTION | 2 | 3 | **5** |
| BUSINESS | 0 | 1 | **1** |
| STATUS | 0 | 1 | **1** |
| **합계** | **70** | **97** | **167** |

### 8.2 multi-label (ANY topic) — 167건에 붙은 라벨 총 **782개** (8월 284 / 9월 498)

라벨 수 분포: 1개 11 · 2개 26 · 3개 30 · 4개 26 · 5개 19 · 6개 22 · 7개 6 · 8개 7 · 9개 9 · 10개 4 · 11개 5 · 12개 2

| topic(any) | 8월 | 9월 | Δ | 합계 |
|---|---|---|---|---|
| APPLICATION | 60 | 68 | +8 | 128 |
| REDUCTION | 33 | 60 | +27 | 93 |
| STATUS | 35 | 38 | +3 | 73 |
| ASSET | 19 | 47 | +28 | 66 |
| DELINQUENCY | 25 | 41 | +16 | 66 |
| INCOME | 11 | 42 | +31 | 53 |
| CREDITOR | 12 | 36 | +24 | 48 |
| BUSINESS | 13 | 34 | +21 | 47 |
| GUARANTEE | 12 | 32 | +20 | 44 |
| TERMINATION | 20 | 18 | −2 | 38 |
| REPAYMENT | 16 | 19 | +3 | 35 |
| OTHER | 6 | 16 | +10 | 22 |
| CREDIT_RECOVERY | 16 | 3 | **−13** | 19 |
| COLLECTION | 2 | 13 | +11 | 15 |
| COURT | **0** | **12** | **+12** | 12 |
| CREDIT_INFO | 1 | 9 | +8 | 10 |
| REAPPLICATION | 2 | 7 | +5 | 9 |
| FSS | 1 | 3 | +2 | 4 |

---

## 9. 8월 ↔ 9월비교 (지시문 분류 8종)

| 분류 | 실측 | 판정 근거 |
|---|---|---|
| REPEATED_QUESTION | **텍스트 유사도 기준 0쌍** | bigram Jaccard ≥0.30 + 동일 primary 조건으로 355건 전수 대조 → 8월-9월 교차 pairs 0건. **수치를 만들지 않았다.** 단 "반복 질문"은 §9.1 주장 단위로는 존재 |
| NEW_IN_SEPTEMBER | **COURT(+12), COLLECTION(+11), CREDIT_INFO(+8)** | 8월 any=0이던 COURT 라벨이 9월 12건(가압류·지급명령·사해행위·공매). 9월 신규 쟁점 축 |
| DISAPPEARED | **CREDIT_RECOVERY −13 (16→3)** | 8월 신복위 워크아웃 연계 질문이 9월 급감. 단 "해결되어 사라짐" 증거는 없음 → §11의 NEEDS_VERIFICATION 남김 |
| FOLLOW_UP_CASE | **31건 (8월 10 / 9월 21)** | 같은 기사 내 `authorFollowupResult` 체인. **월을-crossing한 체인 0건** |
| POLICY_CHANGE | **9월 1건 주장** | 5982(9월 기사) "저번달(8월)부터 기본감면율 대폭 하향" — **작성자 주장 등급, 공식 확인 0건**. 8월→9월 정책 변경의 실측 증거로는 채택하지 않음 |
| CONFLICT | **4건** | §11 (C1~C4) |
| SAME_CASE_POSSIBLE | **7후보** | §9.2 (동일인 확정 없음) |
| CORRECTION_PROPAGATED | **0** | 8월 발행 정리글 6030/6031의 교정 표현이 9월 코퍼스로 전파된 정황 없음 |

### 9.1 주장 단위 반복 (text 유사도 아님 — 깃 기반)

| 반복 주장 | 8월 | 9월 | 계 |
|---|---|---|---|
| `CLAIM_5HWE_MINAP` (5회 미납→실효) | **4** (5857, 5860, 5868, 5869) | **2** (5965, 5968) | 6 |
| `DEDUCTION_AMOUNT_CLAIM` (지역공제·재산공제 금액) | 0 | **6** (5957:c1/c2/c3, 5971, 5971:followup, 5988:c3) | 6 |
| `PROGRAM_2027` (제도 2027 종료) | 1 (5868) | 1 (5971) | 2 |
| `ABSOLUTE_RULE_NONOFFICIAL` (단정형 서술) | 6 | 6 | 12 |
| `COMMERCIAL_FUNNEL` (전자책·단톡방 유도) | 0 | 1 (6005:c1) | 1 |

→ 8월→9월 반복되는 것은 **주제어가 아니라 "5회 미납"이라는 미검증 주장**이다. 이 주장이 9월에도 재생산됐으므로 propagation 감시 대상으로 지정.

### 9.2 SAME_CASE_POSSIBLE (닉네임 해시 8hex, 동일인 확정 없음)

8·9월 모두 활동한 작성자 6명:

| nickHash | 8월 기사 | 9월 기사 | 속성 |
|---|---|---|---|
| `14fab11f` | 5862 | 6001 | 회원 |
| `6d665a81` | 5866 | 5967 | 회원 |
| `74d0f534` | 5884 | 5956, 5957 | 회원 |
| `9e85c86f` | 5884 | 6009 | 회원(9월은 운영자 공지 글) |
| `abb61d96` | 5892 | 5985, 5993 | 회원 — 8·9월 모두 REAPPLICATION/재도전 주제로 **동일 사례 가능성 최고** |
| `daa1038d` | 5874, 5876, 5904 | 5947, 5948, 5957, 5965, 5994, 5997, 6004, 6005, 6009, 6010 | **운영자 단일 계정 → SAME_CASE 대상에서 제외** |

- 중복 제거 시 SAME_CASE_POSSIBLE relation 후보 = **7쌍** (`abb61d96`·`74d0f534`는 9월 2건씩).
- 68개 distinct author hash(지식 167건 기준) 중 **6개**만 양월 활동 → 8월 사례가 9월에 결론 나는 구간은 아직 넓지 않음.

---

## 10. STATUS 제안

| 제안 status | 8월 | 9월 | 계 |
|---|---|---|---|
| REVIEW | 55 | 78 | **133** |
| NEEDS_VERIFICATION | 15 | 19 | **34** |
| ACTIVE | 0 | 0 | **0** |
| CONFLICT / SUPERSEDED / DO_NOT_USE / CORRECTION_REQUIRED | 0 | 0 | **0** |

- ACTIVE 0건은 의도된 결과: 승격 규칙이 "공식자료 교차확인 + 2출처 이상"을 요구하는데 OFFICIAL이 0건이므로 어떤 항목도 승격 요건을 충족하지 못함.
- NEEDS_VERIFICATION 34건 구성 (실측): **깃 보유 24건** + **깃 없음 10건**(8월 5 / 9월 5 — 근거 없는 수치 추측·미확인 절차 진술). 깃 보유 항목은 총 25건이며 그중 `COMMERCIAL_FUNNEL` 1건(6005:c1)만 REVIEW로 분류.
  깃별: ABSOLUTE_RULE_NONOFFICIAL 12 · CLAIM_5HWE_MINAP 6 · DEDUCTION_AMOUNT_CLAIM 6 · PROGRAM_2027 2 · COMMERCIAL_FUNNEL 1 (중복 보유 항목 있음 → 합 25).
- **DB 인코딩과의 관계 (§17.1)**: 표의 133/34는 **분석 판정**이고, 실제 INSERT 시 `status`는 enum에 REVIEW만 존재하므로 **167건 모두 status=REVIEW**, 그중 34건은 `tags`에 `nv:true`로 표기. 즉 "status로 NEEDS_VERIFICATION을 표현하지 못한다"가 §18의 표현 공백 보고 내용.
- SUPERSEDED/DO_NOT_USE 0건: 해당 대상은 코퍼스가 아니라 **레퍼런스 문서·AI 프롬프트**에 존재(§12.4) → 지식행 UPDATE가 아니라 프롬프트/사전에 SUPERSEDED 표기를 해야 하는 문제.

---

## 11. CONFLICT / 검증 대기 쟁점

| # | 쟁점 | 충돌 당사자 | 판정 |
|---|---|---|---|
| C1 | 70세 이상 = "무조건 90% 감면" | `5876:c1`(OPERATOR_EXPERIENCE) vs `5876`(질문 자체)·k11 기본감면표 | 운영자 단정. 공식 근거 0 → NEEDS_VERIFICATION + ABSOLUTE_RULE_NONOFFICIAL |
| C2 | 기본감면율 "지난달부터 대폭 하향" | `5982`(MEMBER_CASE, 9월) vs 공식 공시 자료 부재 | 작성자 주장. 공식자료 미발견 → NEEDS_VERIFICATION |
| C3 | 공시지가 vs 시세 산정 기준 | `6010:c1`(OPERATOR) vs `5957:c1`/k11 | 출처별 불일치 → 공식 확정 전까지 병존 표기 |
| C4 | 5회 미납 시 실효 | §9.1 6건 vs 공식 `k09`(연체 90일=3개월) | **공식 코퍼스가 미지지** → OPERATOR_LONG_TERM_EXPERIENCE 등급 상한, OFFICIAL 승격 금지 |

---

## 12. 공식자료 교차검증 (오피셜 코퍼스 대상 실측)

### 12.1 사용 원본

- `docs/newstart/research-official/ccrs/k09_부실우려_부실차주_전환.json`: "전환선은 **연체 90일(3개월)**이다. 부실차주 = 금융회사 채무 중 어느 하나라도 3개월 이상 장기연체. 신청창구 분리: 부실우려차주=10일~89일… (상담센터 **1660-1378**)" — **현 worktree에서 재확인**
- `k02*`: 하한 10일 vs 31일 공존, `ai_usage_rule`: "AI 답변에서 아직 하나의 확정 규칙처럼 사용하지 않는다"
- `k11`: 실효사유·재조정·효력부활 (감면율표)
- `k06`: 거치·유예

### 12.2 판정

| 주장 | 공식 판정 | 근거 |
|---|---|---|
| **5회 미납 시 실효** | **NOT_SUPPORTED_BY_OFFICIAL_CORPUS** | 공식 코퍼스 82행 + ccrs 전체에서 "5회" 0건. k09는 90일/3개월만 명시 |
| **6개월 미납 → 실효** | **오기재 (mis-stated)** | 공식 "6개월"은 **휴업 요건** 문맥. 9월 실측에도 해당 패턴 0건 |
| 새출발기금 **1660-1378** | **SUPPORTS** | k09에 명시 |
| 신복위 **1600-5500** | SUPPORTS(간접) | 신복위 계열 자료 |
| 폐업·휴업 요건 | SUPPORTS | 9월 10건이 이 축에 물림 |
| 거치·유예 | SUPPORTS | k06 |
| 지역공제·재산공제 **금액** | **NEEDS_VERIFICATION** | 9월 6건(DEDUCTION_AMOUNT_CLAIM) 주장, 공식 코퍼스 0건 |
| 구 전화번호 1600-0610 / 1811-8100 | 코퍼스 0건 (재사용 금지 목록) | 삭제하지 말고 **SUPERSEDED/DO_NOT_USE 사전 등록** |
| 현행 1600-5500 / 1660-1378 | 코퍼스 내 0건(문의 권유로만 존재) | 프롬프트·UI 정답 경로 등록은 승인 후 |

### 12.3 공식 출처 우선순위 (진실 점수 아님 — 조회 순서)

새출발기금 공식 > 신복위 공식 > 금감원/법제처 > 카페 운영자 > 회원 경험. 이 순서는 **격자 상한**만 결정하고, 상위 출처가 없으면 하위 출처는 ACTIVE가 되지 못한다.

### 12.4 SUPERSEDED 처리 대상 (DB knowledge_rows 아님)

| 대상 | 현재 위치 | 처리 |
|---|---|---|
| "6개월 미납→실효" | 레퍼런스 사전·AI 프롬프트 | `SUPERSEDED / DO_NOT_USE`, 근거사용 금지, **RAW 삭제 금지** |
| 1600-0610, 1811-8100 | 구 연락처 목록 (레퍼런스) | `SUPERSEDED / DO_NOT_USE`, 재사용 금지, 삭제 안 함 |
| "5회 미납→실효" | 코퍼스 6건 | OFFICIAL 승격 금지 / `official_verified:false` |

---

## 13. RAW 불변 / 지식 레이어 분리

- `article_json/*.json` 124 files, `data/newstart-cafe/gamian/articles/*.json` 11 files → **수정 0건, 삭제 0건**.
- 분류·태깅·상태는 전부 metadata / derived knowledge / status / relation 레이어에 기록 (노이즈 188건도 RAW 유지 + 미채택 표기만).
- 본문 원문 보존: 운영자 예측 16건 등 긴 항목도 slice 없이 전문 보존 (예: 5948:c1).

---

## 14. 개인정보 / PRIVACY CHECK

- 저장한 식별자: `nickHash = SHA-1(닉네임).slice(0,8)` 68개. **닉네임 원문 저장 0건.**
- 코퍼스 전체 패턴 스캔: `010…` 0건 / `1600-####` 0건 / `1660-####` 0건 / `1811-####` 0건 / 계좌·카드번호 형태(11~16자리 연속) 0건.
- 원문 마스킹(000 등) 그대로 유지, 해제·추론 0건.
- 본 드라이런 문서에도 닉네임·URL 외 PII 없음. 승인 시 INSERT될 `content`는 원문 본문이므로 마스킹 유지 상태 그대로 이동.

---

## 15. 표본 검수 (분류 규칙 수정 후 전체 재실행함)

### 15.1 MEMBER_CASE (5+ 요구 → 6)
- `5855` 광주햇살론 대위변제→국민행복기금 이관, 사무위탁 통지 (CREDITOR/MEDIUM)
- `5856` 연체 40일, 추심위탁 전 새출발 신청 타이밍 (COLLECTION/MEDIUM)
- `5857` 부실우려 5회 미납 후 대기기간 (DELINQUENCY/MEDIUM, 5HWE)
- `5862` 사업자대출인 줄 알았던 차량담보 4,500만→잔존 2,500 이자 (TERMINATION/MEDIUM)
- `5866` 2026-03-25 신청 → 8/21 채무조정 완료 90% 탕감 (REDUCTION/MEDIUM, ABSOLUTE_RULE)
- `5965` 11/10 5회차 연체 후 신청 계획, 월세 보증금 이사 (DELINQUENCY/MEDIUM)

### 15.2 COMMUNITY_OPINION (5+ → 6)
- `5860` "5회 미납 후 실효되고나서 부실차주 가라고 하던데" (5HWE, **회원 전언**)
- `5862:c1` 부실우려 실효 후 차량담보 포함 문의
- `5866:c2` "네 90일 연체후 부실차주로 바로갔습니다"
- `5866:c3` "장기연체코드 몇일 정도 걸리셨어요?"
- `5866:c5` 임시안 5주차, 양도예정통지서 8/28
- `5866:c8` "정확한건 아니구요" 자기 불확실성 명시 → LOW

### 15.3 NEEDS_VERIFICATION (5+ → 6)
- `5857`(5HWE) · `5860`(5HWE) · `5866`(90% 단정) · `5876:c1`(70세 무조건 90%) · `5904:c1`(예상 감면율 40% 예측치) · `5957:c1`(지역공제·부동산 인정가액 좌우, AI보조 명시)

### 15.4 OPERATOR_EXPERIENCE / LONG_TERM (16건 전수 검토)
- 8월: 5874:c1, 5876:c1, 5904:c1, 5904:c3
- 9월: 5947:c1, 5948:c1(LONG_TERM), 5957:c1, 5957:c3, 5965:c3, 5994:c1, 5997:c1, 6004:c1, 6005:c1, 6010:c1, 6010:c3, 6009(ARTICLE, LONG_TERM)
- 운영자 답변 중 본인 발화 근거 문구: "위 내용은 인공지능으로 전자책 내용과 사례를 바탕으로 만들어…" (5957:c3), "차주 개인마다 다르더군요" (5997:c1) → **OFFICIAL 격상 불가 확인**

### 15.5 표본 검수에서 발견·수정한 오분류 패턴 (v1→v3)

| 발견 | v1 | v2 | v3(채택) |
|---|---|---|---|
| 기사 노이즈 과잉(26건 낙하) | 174/355 | 44/355 | **188 채택 / 45 기사만** |
| `??` 인코딩 손상 오판 (한국어 정상 물음표 `??`를 손상으로 취급) | 규칙 포함 → 5743, 5747, 5855, 5866, 5947, 5964, 5967 등 정상 글이 오염 판정 | 포함 | **규칙 삭제** — 재측정: article_json에서 `??`는 전부 정상 한국어 복수 물음표, 5949는 `??` 0건, DB(82행 전문 대상) `??` **0건** → 인코딩 손상 주장은 **성립하지 않음** |
| primary 단일 주제 우선순위 임의 | OTHER 과다 | 2주제 혼재 | **SPECIFICITY 오더**(COURT>COLLECTION>TERMINATION>DELINQUENCY>ASSET>…) |
| 운영자 글을 MEMBER로 흡수 | 있음 | 있음 | OPERATOR role 분리 + LONG_TERM 2건 별도 |

### 15.6 표본에서 남은 미해결
`primary=OTHER` 7건은 "질문 없는 근황/인사"로 지식 가치가 낮다. 강등 규칙을 확정하면 8·9월 재실행 시 지식 167 → 약 160±로 변동 가능. **본 숫자는 현 규칙 기준 실측**이며, 강등 여부도 승인 항목으로 남긴다.

---

## 16. 중복 판정

| 대상 | 판정 |
|---|---|
| `data/newstart-cafe/gamian/articles/` 11건 | **11/11 articleId 중복** (8월 5862, 5866, 5867, 5884, 5904 / 9월 5947, 5948, 5952, 5957, 5988, 6010). → 재수집 금지, relation `DUPLICATE_OF` 또는 CASE 재결합 |
| 82 공식 위키 vs 카페 167 | **중복 0** (§3.3 마커 0건). 카페↔공식 `CROSS_REF`는 대상 knowledge_id(1~82) 확정 필요 → **§17.3에서 0건으로 보류** (`CONTRADICTS` 4건과 동일 사유) |
| 같은 기사 내 본문 vs followup | 31쌍 (FOLLOW_UP relation) |
| 5971 ↔ 5971:followup | DEDUCTION 주장 본문+후속 → 1 CASE로 병합 |

---

## 17. PROPOSED DB MUTATIONS (승인 시 실행할 정확한 숫자 — 현재 0건 실행)

**대상**: `f2r_db` (localhost:3308, profile local). PRODUCTION 대상 작업 없음.

### 17.1 INSERT `newstart_knowledge_items` — 167건

| source_type (Java enum 실값) | 건수 | 매핑 규칙 |
|---|---|---|
| `CAFE_ARTICLE` | **61** | unit=ARTICLE 중 운영자 아닌 61건 |
| `CAFE_COMMENT` | **74** | unit=COMMENT 74건 |
| `CASE` | **31** | unit=FOLLOWUP 31건 |
| `OPERATOR_KNOWLEDGE` | **1** | 기사 6009(공지사항, 운영자 안내문) |
| **계** | **167** | |

- `status` = **167건 전원 REVIEW** (지시 §10의 NEEDS_VERIFICATION 34건은 enum에 없어 status로 표현하지 않고 `tags`의 `nv:true` + `authority` 접미 `… | needs_verification` 로만 표현 — §18). **ACTIVE로 승격하는 제안 없음**: ACTIVE는 승인 완료 의미이므로 NEEDS_VERIFICATION과 정반대다.
- `confidence` = HIGH 47 / MEDIUM 72 / LOW 48
- `category` = primary topic 코드 (§8) / `tags` = `;`구분 `dim:INCOME`,`dim:ASSET`(§8.2 multi-label) + `prov:MEMBER_CASE` + `official_verified:false` + `research_period:2026-08|2026-09` + (해당 시) `nv:true`, `superseded_ref:CLAIM_6MONTH_TERMINATION`

### 17.2 INSERT `newstart_empirical_cases` — 17건

| 원본 | 건수 |
|---|---|
| gamian 11건 (8월 5862, 5866, 5867, 5884, 5904 / 9월 5947, 5948, 5952, 5957, 5988, 6010) | **11** |
| 본 드라이런 신규 사례 후보 중 gamian과 **비중복** 6건 (8월 5868, 5883, 5901 / 9월 5950, 5965, 6001) | **6** |
| **계 (중복 제거 후 유니온)** | **17** |

- 유니온 17건 중 **명시적 감면율 열이 채워진 건 9건** (gamian 원본 8건: 5866=90, 5867=30, 5904=40(예측), 5948=61(예측), 5952=90, 5957=75~77(예측), 5988=0, 6010=0 + 신규 1건: 5901=0%). 나머지 8건은 `predicted/final/temp_reduction_rate` = NULL.
- 원문에 없는 열은 **NULL 유지** (추론 충전 금지). gamian 원본 자체가 미상 항목을 NULL로 두고 있어 그대로 INSERT 한다 (예: 5884는 금액·율 모두 NULL, 5947은 `managerPrediction`에 "감면예측 회피 사유"를 남긴 대신 율 NULL).
- 9월 `article_id`와 8월이 유니크 충돌하지 않으므로 그대로 사용.

- 원문에 없는 열은 **NULL 유지** (추론 충전 금지). gamian 원본도 `debtTotal` 등 다수 NULL → 그대로 INSERT.
- 9월 58xx-60xx 기사 ID를 `article_id`로 사용 (UNIQUE).

### 17.3 INSERT `newstart_knowledge_relations` — 68건

| relation_type | 건수 | 설명 |
|---|---|---|
| `FOLLOW_UP` | 31 | 기사 → followup (§16) |
| `DERIVED_FROM` | 17 | empirical_case → source knowledge |
| `SIMILAR_CLAIM` | 6 | CLAIM_5HWE_MINAP 6건 상호 (§9.1) |
| `SIMILAR_CLAIM` | 6 | DEDUCTION_AMOUNT_CLAIM 9월 6건 |
| `SAME_CASE_POSSIBLE` | 7 | §9.2 (운영자 해시 제외, **확정 아님**) |
| `DUPLICATE_OF` | 1 | gamian 5988 ↔ article_json 5988 (11건은 §16 표기, 대표 1건 INSERT) |
| `SUPERSEDES` | 0 | §12.4 대상이 코퍼스 밖 → 승인 전 생성 보류 |
| `CONTRADICTS` | **0 (유보)** | §11 충돌 4건의 상대편이 **기존 공식 위키 행**(knowledge_id 1~82)이라 대상 id 확정 후 별도 추가. DB 실측에서 이미 `CONTRADICTS`가 사용 중이므로 이 표기를 기준으로 함 (§3.2) |
| **계** | **68** | |

### 17.4 UPDATE — 0건
### 17.5 DELETE — 0건
### 17.6 INSERT `newstart_knowledge_status_log` — 167건

| from_status | to_status | changed_by | change_reason |
|---|---|---|---|
| DRAFT | REVIEW | `knowledge-classify-dryrun` | **167건 전부** (8월 70 / 9월 97). reason에는 §10 판정을 문자열로 기록 (`needs_verification` 34건 포함) |

- NEEDS_VERIFICATION 34건을 별도 to_status로 남기지 못하는 이유는 enum 부재 (§18). 단 `change_reason`에는 34건 모두 `needs_verification` 문자열을 넣어 조회 가능하게 한다.
- **ACTIVE/DRAFT/ARCHIVED 전이 로그 0건** — 승격·보관은 승인 후 별도 라운드.

**총 제안: INSERT 167 + 17 + 68 + 167 = 419 rows / UPDATE 0 / DELETE 0.**

---

## 18. 스키마 표현 공백과 우회 매핑 (MIGRATION 결정 전 보고)

| 지시 §10 요구 | enum 실측 | 표현 가능? | 제안 |
|---|---|---|---|
| `REVIEW` | `KnowledgeStatus.REVIEW` (Java enum; DDL은 VARCHAR(20)) | 가능 | status=REVIEW |
| `ACTIVE` | enum 존재 | 가능 | 조건부 |
| **`NEEDS_VERIFICATION`** | **없음** (DRAFT/REVIEW/ACTIVE/ARCHIVED 4개) | **불가** | (A) `confidence=LOW` + `authority='…needs_verification'` + tag `nv:true` / (B) enum 확장 마이그레이션 |
| `SUPERSEDED`/`DO_NOT_USE`/`CONFLICT`/`CORRECTION_REQUIRED` | 없음 (status_log의 from_status에도 없음) | 불가 | §12.4처럼 **코퍼스 밖 레지스트리**로 처리 (별도 테이블 또는 `newstart_knowledge_relations.relation_type` 확장) |
| multi-label topic | `category` 단일 컬럼 | 부분 불가 | `tags`에 `dim:` 나열로 우회 — 조회는 `LIKE` → 성능·인덱스 미비 |
| `official_verified` boolean | 없음 | 불가 | `authority` 문자열·tags 우회 (조회성 낮음) |
| `research_period` (8월/9월) | `source_date`(DATE) 있음 | 가능 | `source_date` = 작성월 첫날 + tag `research_period:` |

**결론**: (A) 우회로 INSERT 가능 → **마이그레이션 없이 진행 가능**. 단 §10 요구상태 NEEDS_VERIFICATION/SUPERSEDED류는 조회 시 tags LIKE 의존.
- 마이그레이션 대상 후보: V1004(8·9월 카페 분류용 CHECK/enum 확장) 또는 V1005 — **§3.4 충돌로 V999/V1000은 사용 불가.**
- **현재 실행한 DDL 없음.** 승인 시 (A)/(B) 중 선택 지시 요망.

---

## 19. 미병합 카페 스키마 대비 (승인 시 갈림길)

| 옵션 | 내용 | 리스크 |
|---|---|---|
| **O1 (본 문서 기본)** | main V985 `newstart_knowledge_items` + tags 우회로 419 rows | §18 상태 표현 공백, tags LIKE 조회 |
| O2 | `origin/feat/newstart-jipijigi-agent-v1`의 V999/V1000 + `newstart_jipijigi_*` 7종 머지 후 적재 | V999/V1000 충돌 해결 필수, 머지 승인 필요(본 지시 MERGE NO 위반) |

→ 분류 숫자(167/17/68/167)와 provenance·topic 판정은 **O1/O2 공통**이며, 갈리는 건 저장 위치와 우회 여부뿐.

---

## 20. 갭 (자료 없음) 목록

1. **Cafe B(27453843) 코퍼스 0건** — 8월은 정리글 6031에만 존재, 9월은 없음. → Cafe B 원문 수집 없이는 8·9월 비교가 Cafe A 편향.
2. **전자책 원문 0건** — 스키마 §4의 ebook 합성 조인 불가.
3. **9·10월 사이 확정안 결과** — 9월 followup 21건 중 상당수 IN_PROGRESS.
4. `newstart_ka_conversations` 없음 → 운영자-회원 대화 단위 재구성 불가.
5. ~~인코딩 손상~~ → **재측정에서 기각**: article_json·DB 어디에도 `??` 손상은 없음(§15.5). 이 항목을 이유로 한 재수집 불필요.

---

## 21. 승인 시 실행 순서 (제안)

1. §3.4-1 `f2r_db` V985 success=0 repair/re-apply 결정 (Flyway) — DDL 쓰기 전 필수.
2. §18 (A) 또는 (B) 선택.
3. §12.4 SUPERSEDED/DO_NOT_USE 사전(레퍼런스·프롬프트) 반영.
4. §17.1 knowledge_items 167 → §17.3 relations 68 → §17.2 empirical_cases 17 → §17.6 status_log 167 (이 순서가 id 의존에 안전).
5. 건수 증적 파일 생성 → §9/§3 표 갱신.
6. **PRODUCTION DB는 이 순서에 어디에도 포함되지 않는다.**

---

## 22. 승인 항목 (User Decision Required)

- [ ] §17.1 `source_type` 분배(CAFE_ARTICLE 61 / CAFE_COMMENT 74 / CASE 31 / OPERATOR_KNOWLEDGE 1) 수용 여부
- [ ] §17.2 gamian 11건 + 신규 6건 = 17건 수용 여부 (gamian 원문 수정 없음)
- [ ] §17.3 `SAME_CASE_POSSIBLE` 7건: 닉네임 해시 기반 — **동일 사례 확정 아님**을 전제로 INSERT할지
- [ ] §17.3 `CONTRADICTS` 0건 유보: §11 충돌 4건의 상대편이 기존 공식 위키 행이므로, 대상 `knowledge_id` 확정 후 추가 INSERT (DB에는 이미 `CONTRADICTS` 값이 1행 사용 중 — 명명 기준치)
- [ ] §18 (A) 우회 vs (B) enum 확장 마이그레이션 (V1004/V1005)
- [ ] §15.6 `primary=OTHER` 7건 강등 여부 (167 → 약 160 변동 가능)
- [ ] §19 O1 vs O2
- [ ] §10 NEEDS_VERIFICATION 34건을 `tags nv:true`로만 두는 것(=status는 전원 REVIEW)의 허용 여부 — (B) 선택 시에만 불필요

**DB WRITE: 0건 실행. 승인 대기.**

---

**작성일**: 2026-09-24 | **분석 주체**: AI 귀납 분류 (원문 355건 전수) | **분류기 버전**: v3 (`.classify3.cjs` — scratch, 리포 비포함)
