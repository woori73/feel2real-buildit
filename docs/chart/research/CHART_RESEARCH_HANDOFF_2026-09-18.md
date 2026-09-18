# Chart Research Handoff — 2026-09-18 (FIRST PASS #1~#109 COMPLETE)

> 조사 결과의 실제 저장처는 운영 Chart DB이며, 이 문서는 작업 상태·인계 정보만 기록한다.
> DB dump·개인정보 원본·secret/token/cookie/.env를 포함하지 않는다.

## 1. 범위

- FIRST PASS #1~#109 COMPLETE (마지막 후보 #109 최정윤, #110 이후 404)
- 방식: FAST RESEARCH (빈 카드 최대 3분, 공식자료 명확 시 최대 5분, RICH는 PASS)
- 코드·스키마·migration 변경 없음. 삭제·수정 API 사용 없음.

## 2. Chart 실제 저장 완료 후보 (재조회 검증済)

| # | 이름 / 소속 | 필드 | Evidence(신규 URL) | 점수(preview) |
|---|---|---|---|---|
| 11 | 김성진 / 피케이리더스영어학원 | — (이전분) | naeil.com 인터뷰 1 | 17→19 |
| 12 | 권수진 / 뮤엠영어고덕점 | — (이전분) | prompie 소개문 1 (B급) | 17→31 · SCORE_REVIEW |
| 16 | 권아영 / 오케이영어교습소 | — (이전분) | youthassembly.kr 인터뷰 1 | 17→19 |
| 20 | 민정빈 / JBM영어학원 | — (이전분) | youthassembly.kr 인터뷰 1 | 17→19 |
| 21 | 이윤정 / 윤정영어학원 | — (이전분) | prompie 소개문 1 (B급) | 17→31 · SCORE_REVIEW |
| 27 | 김지민 / 김지민영어학원 | — (이전분) | sisunnews 인터뷰 1 | 17→19 |
| 29 | 전종삼 / JS뉴욕어학원 | — (이전분) | heraldcorp 인터뷰 1 | 17→19 |
| 30 | 이수연 / 더퍼스트펭귄 | — (이전분) | mkfn.co.kr 인터뷰 1 | 17→19 |
| 33 | 이승현 / CS영어학원 | — (이전분) | kpetnews 인터뷰 1 | 17→19 |
| 34 | 황수미 / 영어잡는영잡 | — (이전분) | changupnews 인터뷰 1 | 17→19 |
| 37 | 김혜경 / 뮤엠영어학원 | — (이전분) | kmedia-news 칼럼 1 | 17→19 |
| 38 | 류제시 / 제시영어학원 | — (이전분) | primeherald 인터뷰 1 | 17→19 |
| 39 | 이지혜 / 마스터즈영어 | — (이전분) | 세종시뉴스 인터뷰 1 | 17→19 |
| 41 | 김시훈 / 시은영어학원 | curriculum 1 | albamon + 공식블로그 2 | 17→43 |
| 45 | 박정민 / 압구정에듀홀릭학원 | 철학·커리큘럼·교수법·최근활동 4 + 강점 3 | 공식홈 + 내신LIVE 2 | 17→63 |
| 46 | 김주경 / 김주경 영어교습소 | 커리큘럼·교수법 2 | 당근 공식프로필 1 (B급) | 17→39 |
| 62 | 신원경 / 윤선생IGSE 옥천 | 커리큘럼·교수법 2 (+최근활동 기존) | yoons.com 매거진 1 | 미산정(RESEARCH_PENDING) |
| 91 | 신희진 / 과천보듬교육 | 커리큘럼 1 (+최근활동 기존) | 공식홈 1 | 미산정(RESEARCH_PENDING) |
| 93 | 백준호 / 백준호영어전문 | 커리큘럼·교수법 2 | 0 (URL 중복) | 미산정(RESEARCH_PENDING) |
| 96 | 홍세미 / HLS어학원 | 커리큘럼 1 (+최근활동 기존) | albamon 1 | 미산정(RESEARCH_PENDING) |
| 100 | 강수민 / 링키영어 대원포레나 | 철학·커리큘럼·교수법 3 | changupnews 인터뷰 1 | 미산정(RESEARCH_PENDING) |
| 101 | 박은주 / MBC&Winclass | 커리큘럼·교수법 2 | 0 (URL 중복) | 미산정(RESEARCH_PENDING) |
| 102 | 이선혜 / 이지키즈잉글리쉬 | 철학·커리큘럼·교수법 3 | 0 (URL 중복) | 미산정(RESEARCH_PENDING) |
| 108 | 서경희 / 윤선생 우리집앞 | 철학·커리큘럼·교수법 3 | 0 (URL 중복) | 미산정(RESEARCH_PENDING) |
| 109 | 최정윤 / KJC 삼계 | 철학·커리큘럼 2 | 0 (URL 중복) | 미산정(RESEARCH_PENDING) |

- 카드 필드 보강 수: 이번 run 21 (누적 세션 30+)
- Evidence 저장 수(신규 URL): 이번 run 8
- UNSAVED VALID DATA: 0 (12건 보강 후보 전수 재조회 확인)
- #45 필수 확인 항목: 대표/원장·공식홈·경력·철학·커리큘럼·교수법·최근활동·Evidence 2건 — 전부 잔류 확인.

## 3. PROFILE 상태 요약

- RICH: 기존 완성 카드 다수 PASS (변경 없음: #49~#61, #63~#76, #80·#81, #85~#90, #92, #95, #97·#98, #103~#107)
- PARTIAL: #41, #45, #46, #62, #93, #96, #101, #109
- SPARSE: #43, #44, #77, #78, #79, #83, #84 외
- UNRESOLVED: #22~#26, #28, #31, #32, #35, #36, #40, #42, #47, #48 외

## 4. 운영 반영 검증 (2026-09-18)

- PRODUCTION DB SAVE: PASS (운영 API 재조회로 전수 확인)
- LIVE CHART DISPLAY: PASS
  - #45 박정민: 4개 필드 + 강점 3 + Evidence 2 실페이지 표시 확인
  - #100 강수민: 철학·커리큘럼·교수법 표시 확인
  - #46 김주경: 커리큘럼·교수법·근거 표시 확인
- DEPLOY REQUIRED: NO (enrichment 저장 즉시 라이브 반영, 공개 점수는 기존 스냅샷 흐름 유지)

## 5. 큐

- NEW_CANDIDATE_QUEUE: 임혜진/키위북웜스, 김홍조/HJ영어학원, 지니 킴/지니영어학원
- CORRECTION: #6 (오등록 OFFLINE_ACTIVITY 1건 삭제 대기 — 지원 삭제 경로 없음)
- SCORE REVIEW: #7, #12, #21 (Evidence 분류 재검토 대상)
- DUPLICATE: #80/#81 토마스·중앙K영어 중복 의심
- TECH REVIEW: RESEARCH_PENDING 후보의 preview 422 (사유 분석·수정 보류)

## 6. 다음 시작점

1. NEW_CANDIDATE_QUEUE 등록·심층조사 (임혜진 → 김홍조 → 지니 킴)
2. PROFILE_SPARSE/UNRESOLVED 2차 보강
3. review queue 일괄 정리 (전국 조사 완료 후)
