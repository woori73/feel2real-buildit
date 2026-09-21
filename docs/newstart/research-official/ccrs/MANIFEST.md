# CCRS 부실차주 Wiki Knowledge 후보 — 영구 매니페스트 (REVIEW)

- 수집일: 2026-09-21 (UTC). 새로운 웹조사는 이 작업에서 수행하지 않음.
- Temp 원본: `C:\Users\sxxra\AppData\Local\Temp\opencode\ccrs-busil-wiki\` (k01~k12, source_registry.json, REPORT.md — 전수 검증 완료)
- 영구 경로(본 디렉토리): `docs/newstart/research-official/ccrs/`
  - 기존 `docs/newstart/research/article_*.json`은 카페 원문 자산이므로 섞지 않고 공식자료용 별도 경로 사용. 중복 구조 아님.
- Temp가 유일한 원본이 아니도록 본 커밋으로 GitHub에 보존.

## Source Registry

`source_registry.json` — 신용회복위원회 / OFFICIAL_CURRENT / HIGH-OFFICIAL / ccrs.or.kr / ACTIVE(출처 활성 의미).
Knowledge 사용 승인과 분리: `knowledge_status: REVIEW`, `auto_active: false`.

## Knowledge Items: 12 + evidence 1

| 파일 | title | category |
|---|---|---|
| k01 | [부실우려차주 정의] | 부실우려 |
| k02 | [부실우려차주 연체기준] — CONFLICT_REVIEW | 부실우려, 부실차주 자격·연체·추심 |
| k02_evidence | [부실우려 연체하한 evidence: 10일 vs 31일] — CONFLICT_REVIEW | 부실우려, 부실차주 자격·연체·추심 |
| k03 | [부실우려차주 신청자격·지원대상] | 부실우려, 부실차주 자격·연체·추심 |
| k04 | [부실우려차주 대상채무·제외대상] | 부실우려, 부실차주 자격·연체·추심 |
| k05 | [부실우려차주 채무조정방법·금리조정] (scope 한정) | 부실우려, 감면율 |
| k06 | [부실우려차주 상환기간·거치기간·유예] | 부실우려 |
| k07 | [부실우려차주 신청절차·추심중단·합의] | 부실우려, 부실차주 신청 |
| k08 | [부실우려차주 필요서류] | 부실우려, 부실차주 신청 |
| k09 | [부실우려→부실차주 전환·창구분리] | 부실우려, 부실차주 자격·연체·추심 |
| k10 | [새출발기금 중개형·매입형 구분] (scope 한정) | 부실우려, 부실차주 신청 |
| k11 | [부실우려 실효·재조정·효력부활] | 부실우려, 부실차주 임시안부터 확정안 |
| k12 | [부실우려와 일반채무조정 비교] (참고용, scope 한정) | 부실우려, 부실차주 자격·연체·추심 |

전 Item: STATUS=REVIEW, auto_active=false, source_type/source_name/source_url/source_date/collected_at/category/tags/authority/evidence/relations 보존.

## 10일 / 31일 이슈 — CONFLICT_REVIEW (확정 규칙으로 사용 금지)

- evidence_a(10일): 홈페이지 MENU_ID=550 운영안내. 적용대상=중개형(부실우려) 신청대상 안내.
- evidence_b(31일): 신용회복지원협약 제39조 제2항(2025-09-04 개정). 적용대상=협약상 신청대상.
- 실제 충돌인지 다른 대상/조건의 서술인지 미확정. AI 답변에서 하나의 확정 규칙처럼 사용하지 않는다.

## 원금감면 서술 — 출처·적용조건 한정 (일반화 금지)

- k05의 원금감면 서술은 새출발기금 중개형(부실우려) 범위에 한정.
- 근거 2건 병기: (1) 부실우려 지원내용표에 원금감면 항목 없음(부실차주 탭에는 있음), (2) 금융위 2022-08-29 정책문답(현행 일반화 금지).
- 매입형(부실차주) 수치(k10)는 중개형에 적용하지 않는다.

## NOT-COMPARED SOURCES

- CAFE_37: NOT COMPARED (이번 조사에서 실물 미비교. "충돌 없음" 아님)
- EBOOK: NOT COMPARED (동일)

## Guide 연결 (REVIEW 근거)

- 부실우려차주란? → k01, k12
- 신청 조건 → k02(+evidence), k03, k04, k08
- 부실우려/부실차주 차이 → k09, k10, k12
- 채무조정 방법 → k05, k06
- 신청 후 과정 → k07, k11

## Retrieval 규칙 (ADMIN 승인 전까지)

- 전부 STATUS=REVIEW, AUTO ACTIVE=NO. ADMIN 승인 전 NewStart AI의 ACTIVE Knowledge로 사용하지 않는다.
- 승인 후: 부실우려 질의 시 CATEGORY 부실우려 + OFFICIAL_CURRENT + 신용회복위원회 우선, k02 하한은 evidence 양쪽 병기.
