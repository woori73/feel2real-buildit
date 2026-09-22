# NEWSTART CANONICAL → AGENT1 HANDOFF (2026-09-22)

- SOURCE: `docs/newstart-canonical-integration-20260922` HEAD `4b807bd7` (Canonical JSON 무수정).
- VALIDATION: `NEWSTART_CANONICAL_VALIDATION_2026-09-22.md` — validator 47 checks 0 FAIL 2 WARNING(문서화된 기지 상태).
- GOLDEN QA: `NEWSTART_CANONICAL_GOLDEN_QA_2026-09-22.json` — A~H ID 참조 manifest.
- 본 문서는 Agent1 코드를 수정하지 않는다. Agent1 브랜치(`feat/newstart-canonical-rag-20260922`, checkpoint `7b4b769e`)는 읽기 전용으로만 조사했다.

## 1. Consumption contract (deterministic 소비 규칙)

| 항목 | 계약 |
|---|---|
| ID 안정성 | `FAQ-OP-*`(83) / `FAQ-CHAT-*`(26) / `FAQ-OFFICIAL-*`(21) / `CASE-CAFE-*`(30) / `CASE-CHAT-*`(12) / `CASE-CCRS-*`(20) / `REL-*`(186) / `PAT-0*` / `C-0*`. ID prefix가 집합 구분의 정석이다. |
| classification 주의 | CHAT FAQ 26은 classification=OPERATOR로 묶여 있다. CHAT 판별은 ID prefix + sourceType(`CHAT_TRANSCRIPT_PRIVATE`/`CHAT_TRANSCRIPT`)으로만 한다. CCRS 20은 classification=CAFE_CASE + sourceType=CAFE이므로 CAFE TRUE GAP과 섞지 말고 ID prefix로 분리한다. |
| sourceType 안정성 | FAQ {OPERATOR_KNOWLEDGE, CHAT_TRANSCRIPT_PRIVATE, OFFICIAL_SITE}, CASE {CAFE, CHAT_TRANSCRIPT}. OFFICIAL classification은 전원 OFFICIAL_SITE. |
| status 안정성 | FAQ-OFFICIAL officialStatus ∈ {OFFICIAL_CURRENT(16), NOT_FOUND(3: V-05/V-08/V-20), INSUFFICIENT_EVIDENCE(2: V-07/V-21)}. V-11은 CURRENT + note에 INSUFFICIENT 병기(혼합판정, entry 우선). CASE caseStatus ∈ {RESULT_PARTIAL, UNRESOLVED_CASE, OPERATOR_GUIDANCE, RESULT_CONFIRMED, THIRD_PARTY_RESULT}. ONGOING/PENDING 리터럴은 없다 — 진행중은 RESULT_PARTIAL/UNRESOLVED로 인코딩되어 있다. |
| relation endpoints | REL from/to 186건 전부 canonical 집합 내(dangling 0). 집합 밖 참조는 unresolvedRefs 108 pointer로 분리되어 ingest 대상이 아니다. |
| provenance 존재 | 전 entry 보유. OFFICIAL은 site+citations+verificationDoc(PR #370), CCRS는 gradedFrom(branch+commit), CHAT은 file+lineRefs+REVIEW_ONLY. |
| nullable 의미 | null = 결함이 아니라 의미다. FAQ-CHAT answer=null 26건은 UNVERIFIED(REVIEW_ONLY) — 답변으로 제시 금지. CASE observedResult=null 31건은 미추적 — 채우지 않는다. |
| UNKNOWN 표현 | predictionVsActual은 `UNKNOWN` 외 접미사 변형 2종 + 서술형 9종이 혼재한다. `startswith("UNKNOWN")` → UNKNOWN으로 취급하고, 서술형은 상세 텍스트로만 사용한다(스키마 변경 없이 ingest 시 정규화). |
| UNRESOLVED 표현 | caseStatus UNRESOLVED_CASE 16건 + thirdParty=True 1건(CASE-CAFE-5743-P3). thirdParty null 32건은 NOT_ASSESSED로扱い False-safe로 처리한다(제3자로 단정 금지). |
| judgment | 전 REL judgment="PROHIBITED — 판정·정답 확정 금지, 병존 기록". RAG도 동일 금지선을 상속한다. |

## 2. Agent1 코드 정합성 (읽기 전용 조사, 수정은 Agent1 몫)

`683233ba`/`7b4b769e` 기준, 다음 3건은 ingest/QA 전에 Agent1 측에서 additive로 해결해야 한다.

1. **RelationType에 CONTRADICTS·NEEDS_MORE_CASES 미등재** (가장 중요).
   Agent1 enum은 6종(SUPPORTED_BY/PARTIALLY_SUPPORTED_BY/RELATED_CASE/RELATED_FAQ/UNEXPLAINED_BY_OFFICIAL/CONFLICT_REVIEW)만 추가.
   Canonical은 CONTRADICTS 3건(REL-009/032/073, GOLDEN-D)과 어휘 NEEDS_MORE_CASES(PAT-02)를 사용한다.
   Gating 없이 ingest하면 3건이 유실된다. enum 2종 추가 + VARCHAR 길이 재확인(UNEXPLAINED_BY_OFFICIAL 22자).
2. **EvidenceBundle cases 단일 묶음**: CAFE_CASE와 CHAT(미검증)이 한 `cases` 리스트에 합쳐진다.
   CHAT 미검증이 확정 사례처럼 보일 위험이 있으므로, cases를 CAFE/CHAT로 분리하거나 CaseEvidence에 sourceType을 추가하는 additive 변경을 권장.
   GOLDEN-C/GOLDEN-G가 이 케이스를 커버한다.
3. **sourceLinks 후속 연결**: Bundle에 출처 링크 섹션이 없다. provenance의 citations/URL(article 784675 등)/verificationDoc을
   SOURCE_LINKS 섹션으로 전달하는 배선을 권장. FOLLOW_UP은 GOLDEN의 expectedRelationBehavior/RESEARCH_NEEDED에서 생성한다.

정합 확인(수정 불필요): 빈 섹션 생략 +全空→UNKNOWN, ask()→askWithEvidence 위임(기존 동작 불변),
판정문구 스캔(FORBIDDEN_VERDICT_PHRASES), 미확정 사례 통과 retrieval — 정책 §6·§7과 일치.

## 3. Answer Contract alignment (§17)

- Canonical이 강제하는 것은 "매 답변 全섹션 출력"이 아니다. 관련 evidence만 선택하되 provenance/classification을 잃지 않는다.
- Agent1의 빈 섹션 생략 설계는 이 계약과 일치한다. 단 §2-2의 CAFE/CHAT 분리 + §2-3의 SOURCE_LINKS가 갖춰져야 완전 alignment다.
- EBOOK 섹션: canonical은 ebook 본문이 아니라 `relatedDocs` pointer 5종으로 보존한다. EBOOK 섹션 출력 시 pointer + "내부 정리 문서, 공식 아님" 표기로만 사용한다.

## 4. Golden QA 사용법

- `NEWSTART_CANONICAL_GOLDEN_QA_2026-09-22.json` A~H를 RAG QA 입력으로 사용한다.
- 각 fixture의 forbiddenConclusions 중 하나라도 출력되면 FAIL. expectedRelationBehavior 누락도 FAIL.
- GOLDEN-H(judgment-request)는 사용자 명시 요청이 있을 때만 판단 문구 포함 여부를 검사한다. 요청 없이는 GOLDEN-E형 inform만 정답이다.
- 정답 문장 하드코딩 없음 — 판정은 금지선 위반 여부로만 한다.

## 5. 금지선 재확인

- Production DB write 0, ACTIVE promotion 0, merge 0, deploy 0. QA 통과 전까지 ACTIVE 승격 논의 금지.
- 새 리서치 금지. GAP 발견 시 VALIDATION §12 RESEARCH_NEEDED 4항으로만 기록한다.

## NEXT EXACT STEP (Agent1, home PC)

1. `feat/newstart-canonical-rag-20260922`에서 §2의 3건(enum 2종 + cases 분리 + sourceLinks)을 additive로 반영.
2. `./gradlew compileTestJava` → `test --tests "*newstartknowledge*" --tests "*newstart.service.NewStartAiChat*"` (전체 test 금지, MySQL 요구).
3. Golden QA A~H로 RAG QA 실행 → 결과 첨부 후 checkpoint commit + push.
