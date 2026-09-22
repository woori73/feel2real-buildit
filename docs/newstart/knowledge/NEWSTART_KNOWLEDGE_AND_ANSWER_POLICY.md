# NEWSTART KNOWLEDGE AND ANSWER POLICY

- 버전: 1.0 (2026-09-22)
- 적용 대상: NewStart canonical knowledge(FAQ/CASE/RELATIONS)와 이를 사용하는 모든 RAG·답변 파이프라인
- 위치: `docs/newstart/knowledge/NEWSTART_KNOWLEDGE_AND_ANSWER_POLICY.md`
- 이 파일은 canonical knowledge와 RAG 답변의 source of truth 이다. 답변 로직을 변경할 때 이 정책을 먼저 읽어야 한다.

## 1. 최우선 원칙

> **CLASSIFY FIRST. INFORM SECOND. JUDGE ONLY WHEN ASKED.**

1. CLASSIFY: 모든 지식은 출처와 증거 성격부터 분류한다. 판단해서 자료를 버리지 않는다.
2. INFORM: 사용자에게 그 분류 상태를 그대로 알린다. "공식자료에서 확인됨 / 사례에서 관찰됨 / 공식자료에서 확인 못 함 / 증거 불충분"을 구분해서 전달한다.
3. JUDGE ONLY WHEN ASKED: 사용자가 명시적으로 판단·추천을 요구할 때만 분석적 판단을 덧붙인다.

## 2. 분류(classification) 어휘

| 값 | 의미 |
|---|---|
| OFFICIAL | 새출발기금·금융위·관련 공공 공식 자료 본문에서 직접 확인된 내용 |
| OPERATOR | 운영자/담당자 계정의 가이드, 예측, 상담 답변 (공식 규정 아님) |
| EBOOK | 수집된 내부 정리 문서(ebook_sections.json 등)의 서술 |
| CAFE_CASE | 카페 게시글에 기록된 실제 경험 진술(작성자 본인 또는 제3자 서술) |
| REPEATED_CASE_PATTERN | 여러 독립 CASE에서 반복 관찰되는 패턴. **OFFICIAL_RULE 이 아니다.** |
| UNRESOLVED | 질문·상황만 기록되고 답변·결과가 없는 상태 |
| UNKNOWN | 값·출처·성격을 확정할 수 없는 상태. 추측으로 채우지 않는다. |

- CASE 를 OFFICIAL 로 승격하지 않는다.
- 반대로 OFFICIAL 근거가 없다는 이유로 CASE 를 삭제하지 않는다.
- 혼합 상태가 정상이다. 예: `officialStatus=NOT_FOUND` + `caseEvidence=PRESENT`.

## 3. OFFICIAL NOT_FOUND 의 의미

**OFFICIAL 에 없음 ≠ 존재하지 않음.**

- `NOT_FOUND` 는 "현재 확보한 공식 자료에서는 이 세부사항을 확인하지 못했다" 는 기록이다.
- FALSE 로 해석하거나, FALSE 로 답변에 사용해선 안 된다.
- `INSUFFICIENT_EVIDENCE` 는 근거의 존재는 알되 현재 수집 범위에서 검증 불충분임을 뜻한다.
- OFFICIAL 부재가 CASE 삭제·축소의 이유가 될 수 없다.

## 4. 판단이 요구될 때 (JUDGE ONLY WHEN ASKED)

사용자가 명시적으로 판단·예측·추천을 요청한 경우에만 다음 문구를 답변에 붙인다:

> "현재 확보한 공식 규정에서 확인된 사항은 아니며, 수집된 실제 사례에 근거한 참고 분석입니다."

- REPEATED_CASE_PATTERN 을 OFFICIAL_RULE 처럼 제시하지 않는다.
- 예측값(report/operator prediction)과 실제값(observed result)을 항상 분리 표기한다.
- 사례 기반 분석은 분석 대상 사례 ID 들과 수집 시점을 함께 밝힌다.

## 5. DIFFERENCES ARE KNOWLEDGE

출처 간 차이·충돌은 제거 대상이 아니라 기록·연결 대상이다. 관계 어휘:

| relation | 의미 |
|---|---|
| SUPPORTED_BY | A 의 내용을 B 공식 자료 근거가 지지함 |
| PARTIALLY_SUPPORTED_BY | 일부만 지지됨(차이 구간 명시) |
| RELATED_CASE | 관련 CASE |
| RELATED_FAQ | 관련 FAQ |
| CONTRADICTS | 두 기록이 양립불가능하게 충돌 (병존 기록, 정답 판정 아님) |
| UNEXPLAINED_BY_OFFICIAL | CASE 는 존재하나 현행 공식 자료로 설명되지 않음 |
| CONFLICT_REVIEW | 판정 보류 충돌(C-01~C-04 등) |
| NEEDS_MORE_CASES | 패턴 후보이나 사례 수 부족 |

## 6. 답변 구성 규칙 (RAG)

1. 먼저 해당 항목의 classification/officialStatus/caseStatus 를 inform 한다.
2. OFFICIAL 근거가 있으면 인용 출처(문서 ID, URL, 날짜)를 붙여 먼저 제시한다.
3. OPERATOR/EBOOK/CAFE 는 "공식 규정이 아닌 가이드/사례" 임을 명시하고 제시한다.
4. TRUE GAP(부분·미해결·진행중·제3자) 결과를 완료로 바꾸지 않는다. 미해결은 미해결로 inform 한다.
5. PII(닉네임, 연락처, 계정, RAW dump)는 답변· 산출물 어느 곳에도 내보내지 않는다.
6. 없는 값은 null. 추측 금지.

## 7. 검증 상태 어휘 (CASE)

| 값 | 의미 |
|---|---|
| RESULT_CONFIRMED | 게시자/참여자가 후속 결과를 직접 공유한 완료 체인 |
| RESULT_PARTIAL | 일부 단계 결과만 확인됨(제목 주장·타이밍만 등은 부분으로 취급) |
| UNRESOLVED_CASE | 질문/상황만, 답변·결과 없음 |
| ONGOING | 진행 중, 종결 전 |
| THIRD_PARTY_RESULT | 본문 작성자 외 제3자가 서술한 결과 (작성자 체인과 분리 보관) |
| OPERATOR_GUIDANCE | 운영자 계정 가이드/예측 (결과 아님) |
| NOT_CHECKED | 추적 표적이나 미확인 |

- 예측 대비 실제(predictionVsActual) 는 실제값이 추적되기 전까지 UNKNOWN 유지.

## 8. canonical 산출물과 고정 경로

| 파일 | 역할 |
|---|---|
| `NEWSTART_CANONICAL_FAQ_2026-09-22.json` | FAQ canonical (OPERATOR 83 + CHAT 26 + 공식 검증 claim 21) |
| `NEWSTART_CANONICAL_CASE_2026-09-22.json` | CASE canonical (CHAT 12 + CAFE TRUE GAP + CCRS5500 + 기존 research knowledge 참조) |
| `NEWSTART_CANONICAL_RELATIONS_2026-09-22.json` | 출처 간 관계·충돌 병존 기록 |
| `NEWSTART_CANONICAL_HANDOFF_2026-09-22.md` | Agent1/RAG 통합을 위한 핸드오프 |

이 정책 파일은 HANDOFF 가 지정하는대로 Canonical/RAG 가 반드시 읽어야 한다.

## 9. 금지 사항 (hard prohibitions)

- CASE → OFFICIAL 승격 금지
- OFFICIAL 부재 사유 CASE 삭제 금지
- TRUE GAP(미해결·진행중·부분·제3자) → 완료 과대포장 금지
- peer(비운영자) 댓글을 OPERATOR_GUIDANCE 로 격상 금지
- 예측 감면율을 기준·규정으로 canonical화 금지
- 판단 문구 없는 사례 기반 추천 금지
- PII/RAW/credential 포함 금지
