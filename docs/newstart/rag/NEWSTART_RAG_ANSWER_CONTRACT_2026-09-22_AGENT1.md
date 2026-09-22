# NewStart RAG Answer Contract — Agent1 (2026-09-22, 학원 PC)

- Writer: Agent1 Claude (NewStart AI / RAG 전담)
- Base: `origin/main 1cd88f64` (PR #367 merge state)
- Status: **DESIGN ONLY. 코드/DB/migration/merge/deploy 없음.**
- Blocked on: AGENT2 CANONICALIZATION (미완료) + AGENT4 V-01~V-21 (NOT FOUND / WAITING)
- 이 문서는 canonicalization + official verification 완료 후 RAG 구현의 기준 계약이다.

---

## 1. ANSWER CONTRACT

NewStart AI가 회원 질문에 답할 때 조립하는 최종 구조. 8개 섹션, 정의된 순서.
섹션은 내용이 없으면 생략하되 **순서는 고정**한다(운영자 지식이 공식 자료보다 먼저 나와 오인되는 일이 없도록).

| # | 섹션 | 의미 | 있으면 항상 표시 |
|---|------|------|------------------|
| 1 | TOPIC | 질문을 어떤 카테고리/Guide로 라우팅했는지 1줄 재진술. 새 사실 생성 금지, 질문 재구성만 | 예 |
| 2 | OFFICIAL | 공식 기관이 직접 확인한 현재 규정·절차·정책. `source_type ∈ {OFFICIAL_CURRENT, OFFICIAL_HISTORICAL, AUDIT}` | 있을 때만 |
| 3 | OPERATOR | 카페 운영 과정에서 축적된 운영자 지식. `source_type = OPERATOR_KNOWLEDGE`. OFFICIAL과 **절대 병합 라벨 금지** | 있을 때만 |
| 4 | EBOOK | 전자책 정리 설명/가이드. `source_type = EBOOK` | 있을 때만 |
| 5 | CAFE/CASE | 실제 회원 사례·경험. `source_type ∈ {CASE, CAFE_ARTICLE, CAFE_COMMENT, DERIVED_RULE}` + result status(§4 참조) | 있을 때만 |
| 6 | DIFFERENCES | 위 섹션들 사이에 수치/조건/결과가 다를 때 병렬 표시(§5) | 충돌 있을 때만 |
| 7 | FOLLOW-UP/UNKNOWN | 근거 부족·미확인 항목 명시(§6) | 근거 부족 시 |
| 8 | SOURCE-LINKS | provenance 목록(§7) | 근거가 하나라도 있으면 |

**근거 0건**: 1~6 전부 생략, "현재 승인된(ACTIVE) 자료가 없다"는 문구만 출력하고 7(UNKNOWN)로 전환. 구체적 수치·조건·결과를 모델이 만들어내지 않는다.

---

## 2. SOURCE AUTHORITY CONTRACT

소스 유형을 섞지 않는다. 4개 authority는 서로 독립이며 하나가 다른 하나로 **승격되지 않는다**.

| Authority | 정의 | 승격 금지 방향 |
|---|---|---|
| OFFICIAL | 공식 기관이 직접 확인한 현재 규정/절차/정책 | — (최상위, 다른 걸 OFFICIAL로 올리는 것만 금지 대상) |
| OPERATOR | 카페 운영자가 축적한 운영 지식(예: 5568/4451, 83문답) | OFFICIAL로 승격 금지 |
| EBOOK | 전자책에서 정리된 설명/가이드 | OFFICIAL로 승격 금지, OPERATOR와도 동일 라벨 금지 |
| CAFE/CASE | 실제 회원 사례·경험 | 일반 규칙(RULE)으로 승격 금지, OFFICIAL/OPERATOR/EBOOK 어디로도 승격 금지 |

**명시적 GAP 기록**: PR #363의 `EBOOK_TYPES = Set.of("EBOOK", "OPERATOR_KNOWLEDGE")`는 OPERATOR_KNOWLEDGE를 `[EBOOK]` 라벨로 노출시켜 위 표의 "EBOOK과도 동일 라벨 금지"를 위반한다. 이 계약이 확정된 지금부터 수정 대상(§9-B)으로 기록하며, **아직 코드는 고치지 않는다.**

---

## 3. GUIDE 7종 설계 (G0~G6)

Guide는 **새 사실의 source가 아니다.** 질문 분류 → 검색 전략 → 답변 조립 → authority 구분 → difference 처리 → follow-up 처리 → source 표시를 지배하는 **운용 규칙**일 뿐이며, Guide 본문 자체에서 감면율·기간·조건 같은 사실을 새로 서술하지 않는다. Agent2 handoff의 G0~G6 코퍼스 구조(§ GUIDE 0~6 status)를 그대로 라우팅 기준으로 채택한다.

### G0 — PRE-DEFAULT (연체 전 사전 안내)
- PURPOSE: 아직 연체되지 않은 사용자의 사전 질문(제도 소개, 신청 전 체크리스트) 라우팅
- INPUT: 질문 키워드가 "신청 전/아직 연체 안 됨/제도가 뭔지" 류
- OUTPUT: TOPIC="사전 안내", OFFICIAL/EBOOK 우선 검색, CASE는 참고용 최소 노출
- ALLOWED SOURCES: OFFICIAL, EBOOK, (OPERATOR 있으면 별도 라벨)
- FORBIDDEN: 연체 이후 절차를 전제로 한 확정적 안내, 신청 자격을 Guide가 자체 판정

### G1 — FSS (금감원 민원 경로)
- PURPOSE: 추심 관련 금감원 민원 절차 질문 라우팅. Agent2 기준 가장 근거가 두꺼운 영역(EBK 방법+템플릿+5568/4451+CASE 이견)
- INPUT: "금감원", "민원", "추심 중단" 류
- OUTPUT: OFFICIAL(절차) + OPERATOR(템플릿/방법) + CASE(민원 후 결과, 이견 병존 5856) 순서로 조립
- ALLOWED SOURCES: OFFICIAL, OPERATOR, EBOOK, CASE
- FORBIDDEN: **"민원 넣으면 반드시 해결된다"류 결과보장 표현 생성 금지**(Agent2 원칙과 동일). CASE 결과가 RESULT_PARTIAL/UNRESOLVED인데 CONFIRMED처럼 서술 금지

### G2 — CREDIT4U (신용정보 조회/판독)
- PURPOSE: 채권양도·연체정보 표기 등 신용정보 조회 해석 질문 라우팅
- INPUT: "크레딧포유", "연체정보", "공공정보" 류
- OUTPUT: 현재 corpus가 질문(5920)만 있고 상세 가이드 없음 → OFFICIAL/EBOOK 부재 시 즉시 FOLLOW-UP/UNKNOWN 전환
- ALLOWED SOURCES: 현재는 CASE(미해결) 뿐
- FORBIDDEN: 상세 판독법을 모델이 추론해서 답변 생성 금지 — corpus gap을 그대로 노출

### G3 — COURT (지급명령/법원 절차)
- PURPOSE: 지급명령, 이의신청 등 법원 절차 질문 라우팅
- INPUT: "지급명령", "이의신청", "법원" 류
- OUTPUT: full-chain 결과 0건 상태이므로 OFFICIAL 절차 설명 + CASE(5987, 진행중) 은 RESULT_PARTIAL로만 노출
- ALLOWED SOURCES: OFFICIAL, CASE(진행중 표기 필수)
- FORBIDDEN: **"15일" 등 법정기간 확정 표현 생성 금지**(Agent2/3 원칙 — 근거 없는 회원 발언 1건을 법정기간으로 일반화한 전례 있음)

### G4 — HOME/GARNISHMENT (주거/압류)
- PURPOSE: 거주지 압류, 임대차 관련 질문 라우팅
- INPUT: "압류", "임차보증금", "거주" 류
- OUTPUT: L유형(5988/6010)은 판정 확정이지만 A·B·C·F·G·H·J·K 유형은 corpus 없음 → 유형별로 있는 것만 노출, 없는 유형은 UNKNOWN
- ALLOWED SOURCES: OFFICIAL, EBOOK, CASE(유형별 태깅 필수)
- FORBIDDEN: corpus에 없는 유형을 있는 유형 사례로 유추해서 답변 생성 금지

### G5 — MORTGAGE/HF (주택담보대출/HF 연장)
- PURPOSE: 주담대·HF 보증연장 질문 라우팅
- INPUT: "주담대", "HF", "보증연장" 류
- OUTPUT: 실측 결과 0건(성공/실패/조건부/유지 전부 없음) → EBOOK 서술만 OPERATOR/EBOOK 라벨로 노출, 결과 질문은 FOLLOW-UP
- ALLOWED SOURCES: EBOOK, OPERATOR (서술만, 결과 없음을 명시)
- FORBIDDEN: 실측 없는 상태에서 성공률/조건을 모델이 단정하는 것 금지

### G6 — NAVIGATOR (전체 절차 마일스톤 안내)
- PURPOSE: "지금 내가 어느 단계냐"류 전체 여정 질문 라우팅. 여러 G0~G5를 조합
- INPUT: 특정 사안이 아니라 전체 절차/순서를 묻는 질문
- OUTPUT: 단계별 마일스톤(신청→심사→확정→상환)만 OFFICIAL 기준으로 나열, SLA(소요기간)는 "경험치 표기"임을 명시하고 확정값처럼 말하지 않음
- ALLOWED SOURCES: OFFICIAL(단계), CASE(경험치 SLA, 반드시 "경험상" 명시)
- FORBIDDEN: 경험치 SLA를 공식 처리기한처럼 제시 금지

**라우팅 공통 규칙**: 질문 하나가 여러 Guide에 걸치면(G6이 전형적) 해당 Guide들의 OUTPUT을 순서대로 병합하되 ANSWER CONTRACT의 8섹션 틀은 유지한다. Guide가 결정하는 건 "무엇을 우선 검색하고 어떤 authority 혼합을 허용하는가"뿐이다.

---

## 4. DIFFERENCES CONTRACT

`hasConflict=true` 플래그 한 줄로 끝내지 않는다. OFFICIAL/OPERATOR/EBOOK/CASE 중 둘 이상이 같은 주제에서 수치·조건·결과가 다르면 **병렬 표시** 구조를 쓴다:

```
[DIFFERENCES]
OFFICIAL: 공식 기준은 ...
OPERATOR: 운영 경험상 ...
CASE: 실제 사례에서는 ...
→ 적용 시점/조건/사례별 결과가 다를 수 있습니다. 하나로 확정해 안내할 수 없습니다.
```

- 규칙: 각 근거를 **authority 라벨과 함께** 그대로 병기. 하나를 삭제하거나 평균/절충하는 synthetic truth 생성 금지
- 소스: `NewstartKnowledgeRelation.CONTRADICTS`로 이미 연결된 쌍 + (신규) 같은 category 내에서 수치 패턴이 다른 ACTIVE 항목들(Agent2 conflict C-01~C-04가 실사례)
- C-03류(재산축소 서술군)처럼 **권고 승격이 금지된 conflict**는 DIFFERENCES에 넣되 "권장" 어휘 사용 금지, 원문 병기만

---

## 5. FOLLOW-UP / UNKNOWN CONTRACT

두 개념을 구분한다.

- **FOLLOW-UP**: 추가 정보(회원이 제공 가능한 정보)가 있으면 판단 가능한 경우. 예: "정확한 소득 구간을 알려주시면 해당 감면율 사례를 더 좁혀드릴 수 있습니다."
- **UNKNOWN**: 현재 Knowledge DB 근거만으로 확인 불가. 회원이 추가 정보를 줘도 corpus에 없는 경우(예: G2 CREDIT4U 상세 가이드, G5 실측 결과). 예: "이 항목은 현재 승인된 자료에 없어 확인해드릴 수 없습니다. 공식 확인이 필요합니다."

```
[FOLLOW-UP/UNKNOWN]
- FOLLOW-UP: <추가로 필요한 정보>
- UNKNOWN: <corpus에 없어 확인 불가한 항목 + "공식 확인 필요">
```

- 근거 0건이면 답을 만들지 않고 이 섹션만 채운다(§1 근거 0건 규칙과 동일선상)
- Agent2/3가 표시한 follow-up target(5987/5988/6010/5904/5948/5957/5868/5968/5947 등)은 **corpus 메타데이터일 뿐, RAG가 이 목록을 직접 참조해 답을 만드는 건 아니다** — RAG는 오직 ACTIVE 데이터 유무로만 FOLLOW-UP/UNKNOWN을 판정한다

---

## 6. SOURCE-LINKS CONTRACT

가능하면 항상 출처를 보여준다. 최소 provenance 필드(PR #363 `SourceInfo`와 동일 — 재사용):

```
knowledgeId, sourceType, title, sourceUrl, sourceDate, authority, category
```

- CASE의 경우 개인식별정보(닉네임, 실명 등)는 **source 표시 과정에서도** 노출하지 않는다. `NewstartKnowledgeItem`에는애초 PII 필드가 없으므로(원본 `NewstartEmpiricalCase`를 직접 안 쓰는 구조, PR #363 방식 유지) 구조적으로 안전
- 표시 순서: OFFICIAL → OPERATOR → EBOOK → CASE (ANSWER CONTRACT 섹션 순서와 동일)

---

## 7. RELATION CONTRACT (SUPPORTED_BY / RELATED_CASE)

현재 `newstart_knowledge_relations.relation_type`은 VARCHAR(30)이며 Java enum(`NewstartKnowledgeRelation.RelationType`)은 `CASE, RULE, PREREQUISITE, SIMILAR, CONTRADICTS` 5종만 존재(origin/main 확인 완료). 아래는 의미 설계만이며 **아직 구현하지 않는다**.

- **SUPPORTED_BY**: 해당 knowledge item이 더 높은 authority 또는 원자료에 의해 뒷받침됨을 연결. 예: OPERATOR 항목이 특정 OFFICIAL 공문을 SUPPORTED_BY로 가리킬 수 있음
  - 금지: SUPPORTED_BY가 있다는 이유만으로 OPERATOR/EBOOK을 OFFICIAL로 라벨 변경하지 않는다. authority는 여전히 원본 `source_type` 기준
- **RELATED_CASE**: 규칙/설명 항목과 관련된 실제 CASE를 연결
  - 금지: RELATED_CASE로 연결됐다는 이유만으로 CASE를 일반 규칙(RULE)으로 승격하지 않는다

**DB migration 필요 여부**: **불필요** (컬럼이 VARCHAR라 새 값 저장 가능)
**최소 변경점(구현 시점 기준, 지금은 안 함)**:
1. `NewstartKnowledgeRelation.RelationType`에 `SUPPORTED_BY`, `RELATED_CASE` 추가
2. `NewstartKnowledgeRelationRepository`는 변경 불요(이미 from/to 기준 조회 메서드 보유 — PR #363 `findConflictingIds`와 동일 패턴 재사용 가능)
3. `NewStartKnowledgeContextService`에 SUPPORTED_BY chain을 따라가는 조회 1개, RELATED_CASE를 CASE 버킷에 우선순위로 반영하는 로직 1개 — 단, 위 금지 규칙 검증 테스트 동반 필수

---

## 8. PR #363 GAP MATRIX

### A. REUSE (그대로 재사용)
- `status='ACTIVE'` 하드코딩 필터 (retrieval + context 조립 이중 확인, defense-in-depth)
- `NewstartKnowledgeRetrievalService.retrieveForQuestion` 연결 구조
- CASE PII 차단 구조 (원본 `NewstartEmpiricalCase` 미조회, 파생 `NewstartKnowledgeItem`만 사용)
- 근거 0건 시 hallucination 방지 문구 패턴
- `SourceInfo` provenance record 필드 구성 (knowledgeId/sourceType/title/sourceUrl/sourceDate/authority/category) — §6과 완전 일치
- `CONTRADICTS` relation을 pool 내에서 찾아 우선순위 가산하는 메커니즘(`findConflictingIds`) — §4 DIFFERENCES의 데이터 소스로 재사용 가능

### B. MODIFY LATER (canonicalization 불필요, 계약 반영만 하면 되는 것)
- `EBOOK_TYPES = {"EBOOK", "OPERATOR_KNOWLEDGE"}` → OPERATOR 독립 버킷으로 분리 (§2 GAP)
- `toPromptBlock`에 TOPIC 섹션 추가
- `hasConflict` 단일 플래그 → §4 DIFFERENCES 병렬 표시 구조로 확장
- "근거 없음" 단일 문구 → §5 FOLLOW-UP/UNKNOWN 2분 구조로 확장
- `toSources` 순서를 OFFICIAL→OPERATOR→EBOOK→CASE로 재정렬(현재는 OFFICIAL→EBOOK→CASE, OPERATOR가 EBOOK에 묻혀 있어 순서상으로도 드러나지 않음)

### C. WAIT FOR CANONICALIZATION (Agent2/Agent4 산출물 없이는 손댈 수 없는 것)
- 실제 83건 operator Q&A ACTIVE 승격 데이터 (현재 production ACTIVE = 0건)
- `SUPPORTED_BY` / `RELATED_CASE` relation 실데이터
- canonical authority 판정(V-01~V-21 반영 결과)
- 최종 ACTIVE 데이터 기준 회귀 테스트(`NewStartTop10RagRegressionTest`는 현재 fixture 기반, 실 데이터 미검증)
- Official verification이 뒤집을 수 있는 C-01~C-04 conflict의 최종 authority 배치

---

## 9. AGENT4 V-01~V-21 STATUS (read-only 최종 확인)

전체 origin remote newstart 관련 브랜치(agent2/agent3 handoff, 365/368 curation head 포함)를 `git grep`으로 검색한 결과, V-01~V-21이 등장하는 곳은 **큐 정의뿐**이다:
- `docs/newstart/knowledge/NEWSTART_AGENT2_HANDOFF_2026-09-22_HOME_TO_ACADEMY.md` (V-01~V-21 목록 정의, 판정 아님)
- `docs/newstart/research/NEWSTART_AGENT3_HANDOFF_2026-09-22_HOME_TO_ACADEMY.md` (동일 목록, "확인 요청만")

실제 검증 **결과**(승인/반려/보류 판정)를 담은 문서·PR·브랜치는 발견하지 못함.

**AGENT4 V-01~V-21: NOT FOUND / WAITING**

Agent1은 이 판정을 대신 수행하지 않는다.

---

## 10. OUTPUT / WORKTREE 기록

- CLEAN WORKTREE: `C:/Users/VIEW LIFE/feel2real-buildit-agent1-rag` (기존 992-staged worktree와 완전 분리, 기존 worktree 무수정)
- BRANCH: `docs/newstart-agent1-rag-answer-contract-20260922` (origin/main 1cd88f64 기준 신규 브랜치)
- 이 문서 외 코드/migration/DB 변경 없음
