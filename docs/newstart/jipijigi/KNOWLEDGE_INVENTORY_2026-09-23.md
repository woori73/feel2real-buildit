# NewStart 지식 DB/RAG 실제 상태 (inventory, 2026-09-23)

지피지기 Agent가 "우리 DB를 먼저 검색한다"를 증명하려면 무엇이 실제로 있는지부터 기록해야 한다.
이 문서는 추측("모아놨을 것")을 배제하고 **코드 위치와 파일 실측**으로만 썼다.
실행 시점의 행 수는 ADMIN API `GET /api/admin/newstart/jipijigi/inventory`가 반환하는 값이 정본이며
(프로덕션 DB는 이 브랜치에서 조회하지 않았다 — 로컬 worktree DB 기준), 아래는 그 계산근거다.

## 1. 저장소(테이블) — 무엇이 있고 무엇으로 쓰이나

| 테이블 | 생성 migration | 용도 | 상태/구분 컬럼 |
| --- | --- | --- | --- |
| `newstart_knowledge_items` | `V985__add_newstart_knowledge_wiki.sql` | 공식/감사/전자책/카페/사례/규칙 지식 | `status DRAFT|REVIEW|ACTIVE|ARCHIVED`, `source_type` 9종 |
| `newstart_empirical_cases` | V985 | 감면율 카페 게시글 1건=1행 실제 사례 | `article_id` UNIQUE, `final_reduction_rate` |
| `newstart_knowledge_relations` | V985 | 지식 간 연결(`CASE/RULE/PREREQUISITE/SIMILAR/CONTRADICTS`) | `CONTRADICTS`로 출처 충돌 보존 |
| `newstart_knowledge_status_log` | V985 | 상태 변경 provenance | from/to/actor/reason |
| `newstart_official_updates` | `V989`(V991 보완) + `V998` content 컬럼 | 새출발기금/금융위/신복위/FSS/공공기관/희망리턴패키지 공식 자료 | `review_status REVIEW|PUBLISHED|REJECTED|ARCHIVED`, `source_type` 6종, `content`(원문 본문) |
| `newstart_official_discovery_run` | V989/V991 | 자동 discovery 실행 이력 | run 단위 감사 |
| `newstart_chat_messages` | (기존) | 회원 채팅 저장. **RAG 용도로 쓰지 않음(문서 명시)** | — |
| 지피지기 Agent 6 테이블 | `V999__add_newstart_jipijigi_agent.sql` | allowlist/명령/질문/초안/상태로그/조사추적 | 이 PR에서 추가 |

`NewstartKnowledgeItem.SourceType`(entity, `NewstartKnowledgeItem.java:98`) =
`OFFICIAL_CURRENT, OFFICIAL_HISTORICAL, AUDIT, EBOOK, CAFE_ARTICLE, CAFE_COMMENT, CASE, OPERATOR_KNOWLEDGE, DERIVED_RULE`.
Agent의 6종 근거 분류(`JipijigiResearchTrace.SourceClass`)는 이 enum에서 **단순 매핑**된다
(`JipijigiResearchService.classifyKnowledge`) — 커뮤니티(`CAFE_*`)가 `OFFICIAL_RULE`로 승격되는 경로가 없다.

## 2. 검색(retrieval) 능력 — 실제로 있는 것과 없는 것

있다고 확인한 것:
- `NewstartKnowledgeRetrievalService`(`.../newstartknowledge/service/NewstartKnowledgeRetrievalService.java:28`)의
  모든 predicate가 `status = 'ACTIVE'`를 강제(line 48, 80, 118, 145 …) — REVIEW가 RAG로 새지 않는다.
- 조회 수단은 **키워드 LIKE**: `title/content/tags/category`에 `%kw%` (`extractKeywords`는 2글자 이상 토큰).
- `NewstartKnowledgeAdminController`(`/api/admin/newstart/wiki/**`)의 REVIEW 검토함 → 승인/보류/보관 workflow,
  `findEffective()`는 ACTIVE + `effective_from<=today<=effective_to`.
- 공식 자료 상세: `NewStartOfficialUpdate.content`(V998)가 저장된 원문 본문.

**없다고 확인한 것**(저장소 전체 grep 결과):
- 벡터 저장(pgvector 등) / embedding 파이프라인 / 유사도 검색 → 없음.
- MySQL FULLTEXT 인덱스 사용 → 없음(키워드 LIKE가 유일).
- 신복위 카페 라이브 자동 크롤링 → 없음. 있는 것은 `NewStartOfficialDiscoveryScheduler` 계열의
  **공식 사이트** discovery뿐이며 결과도 REVIEW까지만 생성(자동 발행 없음).
- 질문게시판 원문 전용 테이블 → V999 이전에는 없음(감면율 사례와 knowledge rows만).

→ 그래서 Agent의 STEP1~4는 "검색 어딘가"가 아니라 위 테이블에 대한 LIKE 조회로 구현됐다.
→ 근거가 없는 단계는 빈 목록으로 두지 않고 `research_traces.note`에 사유를 남긴다.

## 3. 수집된 원자료(corpus) 실측

| 자료 | 개수 | 위치 |
| --- | --- | --- |
| 부실차주 라이브러리 게시글 JSON | **124** 파일(articleId 5737–6010), 댓글 **271** | `docs/newstart/research/article_json/` |
| 감면율 사례 curated | **11** (article_5862 등) | `data/newstart-cafe/gamian/articles/` |
| 신복위(CCRS) 공식/조사 자료 | 15 파일(`k01…` 등 + MANIFEST) | `docs/newstart/research-official/ccrs/` |
| 카페 인벤토리/사전조사 문서 | `CAFE_INVENTORY.md`, `knowledge/*`(BOARD_STRUCTURE, CASE_INDEX, QUESTION_TAXONOMY 등) | `docs/newstart/research/` |

카페 전체 게시글 수(2026-09-23 조사 기준 5,251) 대비 124건 표본 수집 상태다.
**"전수 수집 완료"가 아니다** — Agent는 이 사실을 gap note로 남긴다.

## 4. ingest 경로 (누가 이 테이블을 채우나)

- `NewstartDataIngestionService` — `data/newstart-cafe/gamian` JSON → empirical cases.
- `NewstartCuratedReviewIngestService` — 전자책 19개 섹션 → knowledge(REVIEW).
- `NewstartOfficialReviewIngestService` — `docs/newstart/research-official/`(신복위 JSON 포함) → knowledge(REVIEW, title 멱등).
- 공식 discovery adapter 5종(CCRS/FSC/NewStartFund/NewStartFundFaq/HopeReturnPackage) → `newstart_official_updates` REVIEW.
- LLM: `ClaudeChatService`(기본 `anthropic.enabled=false`, key는 env), `/api/newstart/ai/chat`은
  `WikiAiRouterClient` 경유. **지피지기 Agent V1은 LLM 산문을 생성하지 않는다** — 초안은 저장된 근거 조립만.

## 5. Agent 관점의 결론

1. 내부 DB가 실제로 가진 것: ACTIVE 지식 + 감면율 실제 사례 + 게시된 공식 자료(원문 포함).
2. 내부 DB가 못 가진 것: 신복위 카페 라이브 답변, 질문게시판 최신글, 벡터 시맨틱 검색.
   → STEP2/STEP3의 라이브 조사는 V1에서 운영자 승인 브라우저 세션으로 수집해
   `POST /api/admin/newstart/jipijigi/questions`로 주입하는 설계(자동 크롤링 없음).
3. 어떤 근거로 답했는지는 `newstart_jipijigi_research_traces`가 STEP별로 남긴다 — 감사 가능.
