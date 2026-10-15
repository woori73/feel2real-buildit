# KNOWLEDGE_DOCUMENT_SCHEMA — NewStart 합성 지식베이스 문서 규격 (v0.1, 2026-09-19)

> 목적: 부실차주 전자책 + 네이버 카페 + 실제 사례/Q&A + 공식 최신 정책자료를 동일 Knowledge Base에서 합성.
> 전자책 원문은 아직 확보되지 않았으므로 전자책 내용을 생성·추측하지 않는다. 본 스키마는 카페 FIRST BATCH(10+5994) 실측에서 도출한 최소 규격이다.

## 1. 저장 분리 원칙

- `raw_text/article_{id}.json` = SOURCE ONLY (원문 metadata + 원문 본문 + 원문 댓글). AI 문장 1자도 섞지 않는다.
- `documents/article_{id}.md` = SOURCE 인용 + DERIVED(분석) 섹션 분리. DERIVED는 `원문 아님` 표기 필수.
- 전자책 corpus는 `docs/newstart/research/ebook/` 별도 보존 예정. 카페 corpus와 합성 시 원본 ID를 반드시 유지한다.

## 2. SOURCE 스키마 (raw_text JSON)

| 필드 | 필수 | 설명 |
|---|---|---|
| articleId / articleNumber | Y | 카페 articleid (안정 키; menuid는 이동 가능) |
| boardName / menuId | Y | breadcrumb/화면 표시 기준. URL 추측 금지. 근거를 breadcrumbsBasis에 기록 |
| breadcrumbsBasis | Y | 어떤 화면 요소로 판정했는지 |
| title | Y | 원문 제목 그대로 |
| writtenBy / memberGrade | Y | 표시명 그대로 (마스킹된 이름도 그대로) |
| writtenAt | Y | `2026.09.18. 18:15` 형식 그대로 |
| viewCount / commentBadge | Y | 수집 시점 값. 변동 가능하므로 extractedAt과 쌍으로 해석 |
| url / originalUrl | Y | canonical(f-e + menuid) + ArticleRead 원형 |
| originalBody | Y | 문단 배열, 순서 유지, 요약·수집 금지. `[이미지 있음]`은 별도 필드로 |
| originalComments[] | Y | {order(작성순), author(표시명), writtenAt, text(전문), imageNote?} |
| hasImage / imageNote | Y | 이미지 다운로드 금지. 있으면 `[이미지 있음]` + 위치(본문/댓글)만 기록 |
| provenance.history[] | 5994류 | board 이동·댓글 증감 이력은 배열로 보존, 덮어쓰기 금지 |
| source / clubId | Y | `naver-cafe-newhopero` / 31312978 |
| extractionMethod / extractedAt / loginMethod | Y | bridge read-only, 시각, 세션재사용(자격증명 미저장) 명시 |

## 3. DERIVED 스키마 (documents MD 하단)

| 필드 | 값 집합 | 설명 |
|---|---|---|
| contentType | QUESTION_PREDICTION_REQUEST, QUESTION_PROPERTY_VALUATION, QUESTION_TRANSFER, QUESTION_PROCESS_ORDER, QUESTION_POLICY_CHANGE, QUESTION_FEE_OBLIGATION, QUESTION_LEGAL_RESPONSE, CASE_PROGRESS_LOG, CASE_DISPUTE, CASE_TRACK_QA, OPERATOR_GUIDE | FIRST BATCH 실측 11유형에서 도출. 추가 시 본 문서 개정 |
| infoNature | OFFICIAL_POLICY, OPERATOR_GUIDE, PERSONAL_EXPERIENCE, UNVERIFIED, NEEDS_VERIFICATION (복수 가능) | 카페 경험담·답변을 정책으로 취급 금지. 운영자 답변도 OPERATOR_GUIDE로만 기록 |
| keyFacts | — | 원문에 있는 사실만 bullet. 수치에는 `작성자 주장` 꼬리표 |
| outcome | RESOLVED_SURFACE, UNRESOLVED, IN_PROGRESS, UNKNOWN | 댓글 종결 여부. 표면 종결도 정책 확정 아님 |
| needsPolicyVerification[] | — | 공식 확인이 필요한 항목만 열거 (예: 연체일수 기산, 공시지가 선정, 배우자명의 승계) |
| kbTags[] | — | `감면율예측`, `장기연체코드`, `국민신문고` 등. 합성 조인 키 |
| warnings | — | 전언·추정·이미지 미수집 등 한계 명시 |

## 4. 합성 조인 설계 (전자책 대비)

- 조인 키: kbTags + contentType + needsPolicyVerification. 본문全文 매칭이 아니라 태그·쟁점 단위 연결.
- 전자책 문서도 동일 DERIVED 표를 갖되 source=`ebook`, provenance에 판·페이지·개정일을 기록한다 (원문 확보 후).
- 공식 정책자료는 infoNature=OFFICIAL_POLICY로만 표기하고, 카페 DERIVED와 충돌 시 정책이 우선하되 충돌 자체를 needsPolicyVerification으로 남긴다.
- PII: 원문 마스킹(000 등)을 그대로 보존. 마스킹 해제·추론 금지. 합성 출력에도 마스킹 유지.

## 5. FIRST BATCH 적용 결과

- 10건 + 5994 provenance copy에 본 스키마 적용 완료.
- contentType 분포: 자유게시판 7(질문 4 + 사례/안내 3), 질문게시판 2, 부실차주 신청 1, 임시안~확정안 1. 질문게시판 한정 수집 지시와 달리 breadcrumb 실측상 자유게시판이 다수인 점이 발견 — 차기 배치 선정 기준에 반영 필요.
- 공통 needsPolicyVerification 상위: 감면율 산식(소득·재산 반영), 장기연체 등록 시점, 공시지가 선정, 배우자명의 취득, 지급명령 대응.
