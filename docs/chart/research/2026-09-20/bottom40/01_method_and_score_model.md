# BOTTOM40 재조사 방법론 + 점수 모델 (2026-09-20, READ ONLY)

- 대상: 운영 publication **35** rank 61~100 40명
- 원칙: 근거 확보 → 카드 보강 → 공식 산식 재계산. 점수·순위 목표 조사 금지, 산식·가중치 변경 금지, 점수 수동 입력 금지,
  조사 중 publication rebuild 금지, direct production SQL write 금지.
- branch: `research/chart-bottom40-enrichment-20260920` (base 31dca09 = STEP 3 검증 완료 지점)

## 1. BASELINE 확인 (운영 화면과 일치 → 진행)

`GET /api/chart/publication/current` → publicationId 35, entries 100, 전부 RANKED.

| 그룹 | rank | 인원 | score 집합 | 지시문 관찰 | 판정 |
|---|---|---|---|---|---|
| A | 76~100 | 25 | {17} | 25명 전원 17점 | 일치 |
| B | 65~75 | 11 | {19} | 11명 전원 19점 | 일치 |
| C | 61~64 | 4 | {31} | 4명 전원 31점 | 일치 |

전체 100명 score 분포: 17×25, 19×11, 31×4, 39/42/43/44/47×각 1, 49×3, 56×3, 58×2, 61×4, 63×3, 65×4, 66×2, 67×1, 68×4, 70×3, 72×1, 73×3, 75×9, 77×1, 79×2, 80×1, 84×6, 89×1, 94×1, 95×1.

raw data: `baseline_ranks61_100.json` / 표: `00_baseline_snapshot.md`

### 1.1 40명의 공통 결함 (조사 전 확인한 사실)

- 40명 전원 `educationPhilosophy` · `curriculum` · `distinctiveTeaching` · `recentActivity` **비어 있음**(A·B는 0점 항목 3개, C는 1개).
- A·B·C 전원 `officialChannels`(homepage/blog/youtube/social) **비어 있음**.
- evidence URL 55건 중 **26건이 `vertexaisearch.cloud.google.com/grounding-api-redirect/...`** — 즉 Gemini grounding 임시 redirect 로, 원 출처가 아니라 재검증 자체가 불가능한 URL.
  enrichment service 는 이 host 를 `NON_ORIGINAL_URL_MARKERS` 로 명시적으로 거부한다(`ChartCandidateEnrichmentService.java:50-53`).
- A 그룹은 evidence 1건, B 는 2건, C 는 2건(OFFLINE_ACTIVITY 1 + EDUCATIONAL_CONTENT 1)뿐.
- 40명 전원 `legacySource=V3_AUTO`, `eligibilityStatus=EVALUATABLE`(레거시라 철학·커리큘럼 게이트 면제) → **카드가 비어도 점수만 낮은 상태**.

## 2. 공식 산식 (변경 대상 아님 — 어디에 실점이 쌓였는지의 지도)

`ChartSiteToolService.score()`(:740) 가 `chart_candidates` 의 요약 컬럼과 `chart_candidate_activities` 행만으로 계산한다. 가중치 합 100.

| 항목 | 배점 | 계산 | 17점 그룹 현재값 | 끌어올리는 조건(실제 근거로만) |
|---|---|---|---|---|
| 교육자 고유성 | 35 | (identity / philosophy / curriculum / distinctiveTeaching 4개 요약 컬럼 중 non-blank 수) × 10 | 10 | philosophy·curriculum·distinctive 3종을 **공개 근거 기반 문장**으로 채워야 +25 |
| 수업·교육 실천 | 20 | {TEACHING_METHOD, STUDENT_GUIDANCE, OFFLINE_ACTIVITY} 중 존재하는 kind 수 × 7 | 0 | 해당 kind 의 원 출처 URL evidence 추가 |
| 교육 콘텐츠 지속성 | 15 | evidence 의 **서로 다른 `activity_date` 개월 수** × 5 | 0 | `activityDate` 가 실제로 적힌 서로 다른 달의 글 3건 이상 |
| 전문성 | 15 | {MATERIAL, SUBJECT_EXPERTISE, EDUCATIONAL_CONTENT} 중 존재 kind 수 × 5 | 5 | 교재·과목 전문성·교육자료 kind 의 실제 근거 |
| 검증 근거 | 10 | **중복 제거된 source_url 수** × 2 | 2 | 원 출처 URL 5건 이상(redirect 는 거부되므로 추가 불가) |
| 지역 활동 | 5 | OFFLINE_ACTIVITY kind 존재 시 5 | 0 | 대면 활동 근거 URL |

핵심 결론: 17점은 "자료가 없어서" 아니라 "**카드 요약 3종이 비고, 원 출처 URL 1건에 날짜도 없다**"에서 온다. 따라서 조사 성과는 ① 실재 문장(철학·커리큘럼·수업방식) ② 날짜가 보이는 원 출처 URL ③ 활동 kind 세 가지로 수집해야 점수에 반영된다.

## 3. enrichment 가 채울 수 있는 범위 (쓰기 가능 필드의 실제 스키마)

`POST /api/admin/chart/candidates/{id}/enrichment` ← `EnrichmentRequest`(서비스 `:287`):

- 요약 4종: `educationPhilosophySummary`, `curriculumSummary`, `distinctiveTeachingSummary`, `recentActivitySummary`
  (빈 칸만 채워지고, 기존 값은 `overwrite=true` 일 때만 교체 / `SUMMARY_COLUMNS` 외 컬럼은 어떤 경로도 불가)
- 리스트: `strengths`, `improvementSuggestions` (합집합 누적)
- `evidence[]`: `{contentType, contentSummary, sourceUrl, activityDate, evidenceKind, educationallyInformative}`
- **불가**: official_homepage·blog·youtube·social 컬럼, identity(이름·학원·지역), eligibility, score, rank, publication.
  → 카드에 채널 URL 을 표시하고 싶어도 enrichment 로는 못 넣는다. evidence 행의 `sourceUrl` 로만 남는다.
- `evidenceKind` 허용 집합(기존 규칙): INFORMATIVE = PHILOSOPHY, CURRICULUM, TEACHING_METHOD, MATERIAL,
  STUDENT_GUIDANCE, SUBJECT_EXPERTISE, EDUCATIONAL_CONTENT, OFFLINE_ACTIVITY / PROMO = ADVERTISEMENT, PRESS_RELEASE, RECRUITMENT
- `chart_candidate_activities` 는 INSERT IGNORE 이라 **같은 URL 은 1회만** 들어간다 → 새 URL 이어야 실질 증가.

## 4. 후보별 조사 절차 (5명 단위 배치)

1. **원 출처 복구**: 현재 evidence 요약에 인용된 글 제목·구문을 키워드로 검색해 실제 네이버 블로그·기관 페이지 URL 을 특정한다.
   redirect URL 은 그대로 재사용하지 않는다.
2. **본인 확인**: 실명(또는 학원 공식 명의) + 소속 + 지역 일치. 닉네임만 있는 글, 동명이인 suspected 는 UNRESOLVED 로 남긴다.
3. **출처 품질**: ①교육자 본인이 직접 공개한 콘텐츠 ②소속 기관 공식 페이지 ③공개 인터뷰·강연·출판물 ④신뢰 가능한 제3자.
   디렉터리(학원 정보앱·지도)는 주소·전화·존재 확인용으로만. 광고·보도자료는 철학·전문성 근거로 쓰지 않는다.
4. **필드 연결**: 각 근거에 대해 "어떤 카드 필드를 채우는가"를 명시. 못 채우는 URL 은 수집만 하고 판정 근거로 쓰지 않는다.
5. **문장 작성**: 공개된 본문에서 직접 확인한 내용만 2~3문장. 추론·일반 문구·광고 복붙 금지.
6. **dryRun 금지 순서**: 조사 완료 전까지 `scores/preview`·`reevaluate` 호출만 허용(읽기), enrichment 등 쓰기는 전량 금지.

## 5. 기록 형식

후보 1명당 `candidates/{candidateId}_{rank}_{이름}.md` 파일에 지시문 §5 템플릿(IDENTITY / NEW EVIDENCE / CARD ENRICHMENT / UNRESOLVED / EXPECTED SCORE) 그대로 기록.
5명 배치마다 checkpoint commit + push.

## 6. 쓰기 가드 (기존 규칙 유지)

내가 작성하는 모든 텍스트는 저장 직전 `/[\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\u0400-\u04ff]/`
(일본 가나·CJK 확장·CJK 통용·키릴) 로 검사해 매치 시 제거한다. 한국어 음절(가~힣)은 정상.
운영 DB 의 알려진 오타 3건(candidateId 66·80·82 요약 내 비한국어 문자 혼입)은 정정 endpoint 가 없어 이번 범위 밖.
