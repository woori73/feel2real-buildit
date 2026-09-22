# NEWSTART CANONICAL KNOWLEDGE INTEGRATION — HANDOFF (2026-09-22)

- 작성: FINAL TAKEOVER 세션 (canonical integration 전담)
- 브랜치: `docs/newstart-canonical-integration-20260922` (base `origin/main` 1cd88f64)
- 수신자: Agent1 (Claude, RAG — WAITING 중), Agent2, Agent3, Agent4
- 상태: docs만. Knowledge DB ingest NO / ACTIVE 승격 NO / production DB write NO / deploy NO / merge NO.

## Mandatory RAG Answer Policy

**Agent1은 응답 파이프라인을 구성·수정하기 전에 반드시 다음 파일을 전문으로 읽어야 한다:**

> `docs/newstart/knowledge/NEWSTART_KNOWLEDGE_AND_ANSWER_POLICY.md`

핵심 (원문 우선):
- **CLASSIFY FIRST. INFORM SECOND. JUDGE ONLY WHEN ASKED.**
- OFFICIAL에 없음 ≠ 존재하지 않음. `NOT_FOUND`는 "현재 확보한 공식자료에서 이 세부사항을 확인하지 못했다"이며 FALSE가 아니다.
- CASE→OFFICIAL 승격 금지. OFFICIAL 근거 부재로 CASE 삭제 금지.
- 판단이 요청되면 다음 문구를 붙인다: "현재 확보한 공식 규정에서 확인된 사항은 아니며, 수집된 실제 사례에 근거한 참고 분석입니다."
- REPEATED_CASE_PATTERN ≠ OFFICIAL_RULE. 예측값과 실제값을 분리 표기.
- TRUE GAP(미해결·진행중·부분·제3자)를 완료로 변환하지 않는다.

## 1. 산출물

| 파일 | 내용 | 규모 |
|---|---|---|
| `NEWSTART_KNOWLEDGE_AND_ANSWER_POLICY.md` | 답변·판단·분류 정책 (source of truth) | — |
| `NEWSTART_CANONICAL_FAQ_2026-09-22.json` | canonical FAQ | 130 entries = OPERATOR 83 + CHAT 26 + 공식 검증 claim 21 |
| `NEWSTART_CANONICAL_CASE_2026-09-22.json` | canonical CASE | 62 = TRUE GAP CAFE 30 + CHAT 12 + CCRS5500 20 |
| `NEWSTART_CANONICAL_RELATIONS_2026-09-22.json` | 관계·충돌·패턴 | relations 186 + patterns 3 + conflictRegistry C-01~C-04 + unresolvedRefs 108(pointer) |

## 2. 입력 (전부 기존 수집분 재사용 — 신규 조사 없음)

- A. Muse TRUE GAP PM: branch `docs/newstart-agent3-truegap-20260922-pm` commit `6dac421a` (`NEWSTART_TRUEGAP_COLLECTION_2026-09-22_PM.md`) — CAFE 30행 상태·grade·gap 그대로 전사
- B. Qoder CCRS5500: branch `research/newstart-ccrs5500-sample-20260922` commit `0af5d11c` — 20 기사 구조화 필드 전사(본문 원문은 canonical에 포함하지 않음)
- C. Agent4 공식 검증: PR #370 (`NEWSTART_AGENT4_OFFICIAL_VERIFICATION_2026-09-22.md`, branch `docs/newstart-agent4-official-verify-20260922` commit `ee209870`) — V-01~V-21 판정·인용 전사
- D. 기존: 4451/5568 운영자 Q&A 83(PR #368 계열 산출물), CHAT FAQ 26 + CASE 12(REVIEW_ONLY), EBOOK·research knowledge 문서군은 pointer로 `relatedDocs`에 보존

## 3. V-01~V-21 상태 보존 (원본 그대로)

- OFFICIAL_CURRENT = 15
- INSUFFICIENT_EVIDENCE = V-07(재산공제 세부 예규), V-11(민원 실효성; 추심중단 원칙은 CURRENT), V-21(소득 산정)
- NOT_FOUND = V-05(제도 종료일 날짜), V-08(차량), V-20(주담대 유예·연장 세부; 원칙은 CURRENT)
- 혼합 판정(V-11·V-20)은 PR #370 판정표 원문이 우선 — canonical `officialStatusNote`에 병기

### OFFICIAL NOT_FOUND + CASE PRESENT (핵심 병존 슬롯)

| V | CASE 증거 (OPERATOR 측 소스 수) | relation |
|---|---|---|
| V-05 제도기한 | 2 | UNEXPLAINED_BY_OFFICIAL (경유: 지급명령 사례는 V-04로 분리 표기) |
| V-08 차량 | 2 + CASE-CAFE-5862, CASE-CHAT-004, CASE-CCRS-784675, FAQ-CHAT-020 | UNEXPLAINED_BY_OFFICIAL |
| V-20 주담대 | 2 + TRUE GAP §6 8행(5901/6010/5947/5957/5868/5883/5749/5968) | UNEXPLAINED_BY_OFFICIAL |

→ "공식 미확인 + 사례 존재"는 삭제·판정 대상이 아니라 RAG에서 그대로 inform 되어야 하는 상태다.

## 4. TRUE GAP 레지스트리 (과대포장 금지선)

- FSS(금감원 민원) 풀체인 RESULT_CONFIRMED = 0 (코퍼스 124 중 금감원·민원 키워드 0)
- COURT 풀체인 RESULT_CONFIRMED = 0, '15일' 공식 출처 = 0 (동료 '2주' 발언 1건뿐, 5752)
- Credit4U 상세 화면 가이드 = ABSENT (가장 근접: 5997 토스 알림 관찰)
- 주담대/HF 연장 성공·실패 결과 = 0 (HF 키워드 0건)
- CASE 집합의 doNotCanonicalize 필드가 이 금지선을 행 단위로 보존

## 5. 반복 패턴 (OFFICIAL_RULE 아님)

- PAT-01 재산가액≥채무 → 0%·체결 보류 (6 사례)
- PAT-02 예측 감면율 제시 후 실제 미추적 (6 사례, predictionVsActual=UNKNOWN)
- PAT-03 공식 처리기간(2주·3~4개월) 대비 장기 체감 타임라인 (5 사례)

## 6. 충돌 레지스트리 (병존, 판정 없음)

- C-01 감면율 수치 — OPERATOR 서술 3건(5568-Q09/Q32, 4451-Q41) ↔ 공식 V-06 인용 → CONTRADICTS relation(근거 인용만, 판정 금지)
- C-02 민원 실효성 — 5988·5856 ↔ V-11 → CONFLICT_REVIEW (5856: CASE_INDEX '민원' 서술이 JSON 코퍼스에 미지원 — pipeline conflict 보존)
- C-03 재산축소 서술군 — 11건 ↔ V-07/V-12 반대근원 → CONFLICT_REVIEW (권고 승격 금지)
- C-04 수식 표현 — 4451-Q20 원문 보존 (relation 대상 결측은 정상이며 conflictRegistry에만 기록)

## 7. VALIDATION 결과 (전 항목 0)

| 항목 | 결과 |
|---|---|
| JSON parse error | 0 (3파일 `json.loads` 통과) |
| duplicate canonical ID | 0 (FAQ 130 / CASE 62 / REL 186 전체 고유) |
| dangling relation | 0 (from/to 전부 canonical 집합 내; 집합 밖 참조는 unresolvedRefs pointer 108로 분리) |
| missing provenance | 0 (전 entry cafeKey/article/board/date 또는 line refs + collected 보유) |
| PII/닉네임/연락처/RAW 누출 | 0 (authorNickname=null 확인, contentRaw 본문 미포함 — 시그니처 대조 통과) |
| CASE→OFFICIAL 승격 | 0 (classification=OFFICIAL은 FAQ-OFFICIAL-* 21건뿐, 전원 sourceType=OFFICIAL_SITE+인용) |
| NOT_FOUND=FALSE 오독 | 0 (UNEXPLAINED_BY_OFFICIAL 22건 병존 보존) |
| OFFICIAL 부재 사유 CASE 삭제 | 0 (입력 62건 전수 수록) |
| CASE를 보편 규칙으로 제시 | 0 (pattern은 REPEATED_CASE_PATTERN+officialRule=false) |
| TRUE GAP 과대포장 | 0 (부분·진행중·제3자 상태 원본 유지, doNotCanonicalize 보존) |

## 8. 다음 단계 (제안)

1. Agent1: POLICY 파일 정독 후 RAG 응답 조립 규칙(classify→inform) 구현. ACTIVE 승격은 이관된 QA 후에만.
2. Agent2: CCRS5500 20행 caseStatus 규칙 파생분(CCRS-20) 원문 재확인(RESULT_CONFIRMED는 finalRate 존재분만).
3. 추적 이관(기존과 동일): V-05 종료일(FSS 보도자료), V-07/V-08 예규 PDF, V-20 유예 세부, 5904/5948/5957 예측 대비 실제, 6010/5988/5997 후속.
4. PR #365/#366/#368/#370 모두 OPEN 유지 — 본 브랜치 merge·deploy 금지.

## 9. commit 이력

- `c4b989d0` STEP1 POLICY + FAQ
- `6a0e2051` STEP2 CASE
- `1d9e9869` STEP3 RELATIONS
- (STEP4 본 문서 + 최종 검증)
