# BUILD IT Chart Handoff — 2026-09-18

## A. 현재 운영 상태

- 전체 candidate = 109
- 현재 EVALUATABLE = 83
- RESEARCH_PENDING = 26
- publication/ranking = 83
- 현재 홈페이지는 아직 기존 snapshot 표시
- 현재 score=17 = 43명
- 현재 avg = 42.93
- min=17 / max=95
- **홈페이지 점수는 아직 수정되지 않았음**

**절대로 "홈페이지 반영 완료" 라고 기록하지 않는다.**

## B. 오전 research

기존 자료:

branch: chart/research-handoff-20260918
commit: 7ce4c77
enrichment commit: cc944771

관련 파일:
- docs/chart/research/CHART_RESEARCH_HANDOFF_2026-09-18.md
- docs/chart/research/2026-09-11-chart-expansion-research.json
- docs/chart/research/2026-09-11-chart-expansion-research.md

FIRST PASS: #1~#109 COMPLETE

오전 enrichment:
- profile fields 21
- new evidence URLs 8
- UNSAVED 0

## C. 83명 재계산 결과

83명 전수:

live == recalculated: 65
changed: 18
변경 전원 상승.

정확한 값:

#45 박정민 17 → 63
#41 김시훈 17 → 43
#46 김주경 17 → 39

#6 문준영 17 → 31
#7 이광노 17 → 31
#12 권수진 17 → 31
#21 이윤정 17 → 31

#11 김성진 17 → 19
#16 권아영 17 → 19
#20 민정빈 17 → 19
#27 김지민 17 → 19
#29 전종삼 17 → 19
#30 이수연 17 → 19
#33 이승현 17 → 19
#34 황수미 17 → 19
#37 김혜경 17 → 19
#38 류제시 17 → 19
#39 이지혜 17 → 19

score=17: 43 → 예상 25
avg: 42.93 → 예상 45.00
min/max: 17 / 95 유지

오전 preview 검증: 6/6 일치

**중요: 이 값들은 검증된 recalculated 결과이지만 아직 production snapshot에 기록되지 않았다.**

## D. 83명만 보이는 원인

109명 전체 중:
- EVALUATABLE = 83
- RESEARCH_PENDING = 26

ranking pool은 eligibility gate 때문에 83명만 포함.
83이라는 hardcoded limit 문제가 아니다.

26명 IDs:
51,52,53,55,59,62,66,77,78,79,80,82,83,84,
90,91,93,94,96,99,100,101,102,106,108,109

## E. pending re-evaluation

오전 enrichment된 pending 9명:
- 62 신원경 / 윤선생IGSE 옥천
- 91 신희진 / 과천보듬교육
- 93 백준호 / 백준호영어전문
- 96 홍세미 / HLS어학원
- 100 강수민 / 링키영어 대원포레나
- 101 박은주 / MBC&Winclass
- 102 이선혜 / 이지키즈잉글리쉬
- 108 서경희 / 윤선생 우리집앞
- 109 최정윤 / KJC 삼계

현재 예상:
- #100 강수민 → EVALUATABLE 예상
- 나머지 8명 → RESEARCH_PENDING 유지 예상

**단: #100도 production 저장 플래그까지 이용한 공식 dryRun PASS를 확인하기 전에는 승격 금지.**
**직접 SQL UPDATE 절대 금지.**

## F. PR #318

PR: https://github.com/woori73/feel2real-buildit/pull/318
branch: fix/chart-recalculate-eligibility-20260918
commit: ecf0d033866c39438c094232e64352cc6188cf14

현재 상태: OPEN, NOT MERGED, NOT DEPLOYED

구현:
- RESEARCH_PENDING re-evaluation
- 최초 assess 로직 재사용
- dryRun 기본 true
- PASS일 때만 EVALUATABLE
- stale snapshot 조회
- ROLE_ADMIN
- research agent allowlist 밖
- schema 변경 없음

endpoint:
- POST /api/admin/chart/tools/candidates/{id}/reevaluate
- GET /api/admin/chart/tools/scores/stale

## G. 테스트

Chart unit tests: 106 PASS, 0 FAIL

신규:
- ChartScoreRecalculationTest
- ChartEligibilityReevaluationTest
- ChartRankingBulkTest
- ChartStaleSnapshotTest

./gradlew build -x test: SUCCESS

MySQL E2E: 로컬 MySQL 부재로 미실행, CI에서 확인 필요

## H. 가장 중요한 현재 문제

사용자가 실제 홈페이지를 확인했으며 점수가 아직 변경되지 않았다.
이것은 현재 정상이다.

이유: recalculated 결과만 만들었고 production snapshot/publication을 아직 갱신하지 않았기 때문이다.

따라서 집 PC에서 이어갈 첫 번째 목표는:
**"검증 완료된 18명의 점수를 공식 production snapshot → publication → homepage까지 반영"**

## I. 집 PC 작업 순서

집 PC에서는 먼저:
1. repository pull
2. 이 HANDOFF 파일 읽기
3. PR #318 상태 확인
4. production 인증/접근 방법 확인

그 다음 우선순위:

### STEP 1
83명 production preview 재확인.
EXPECTED 18/18 일치해야 함.

### STEP 2
기존 공식 경로:
submitReview → chart_score_snapshots
으로 83명 snapshot 생성.
**직접 score UPDATE 금지.**

### STEP 3
snapshot 검증.
EXPECTED:
- score=17 = 25
- avg ≈ 45.00
- min=17
- max=95
- 18명 점수 18/18 일치

불일치하면 revertReviewBatch 후 STOP.

### STEP 4
rebuildNationwidePublication

### STEP 5
production rankings API 검증.
- 83명
- rank 1~83 연속
대표:
- #45=63
- #41=43
- #46=39
- #12=31
- #11=19

### STEP 6
실제 홈페이지 검증.
DATABASE SNAPSHOT = PUBLICATION API = ACTUAL HOMEPAGE
세 단계가 같아야 완료.

API만 바뀌고 홈페이지가 안 바뀌면 cache/CDN/frontend 문제 조사.

### STEP 7
83명 정상화가 끝난 다음에만 #100 처리.
PR #318 기능을 이용해 #100 dryRun.
PASS하면 공식 re-evaluation.
그 후 #100 공식 snapshot 생성.
publication rebuild.
최종 ranking 84 예상.
PASS하지 않으면 83 유지.

## J. 절대 금지

- 109명 강제 공개
- 26명 일괄 EVALUATABLE
- score 직접 UPDATE
- random score
- rank hardcoding
- eligibility 직접 SQL UPDATE
- evidence 삭제
- educator 재생성
- production DB 초기화

## K. 산출물 위치

- docs/chart/research/2026-09-18/01_chart_recalc_83.csv
- docs/chart/research/2026-09-18/02_chart_pending_dryrun.md
- docs/chart/research/2026-09-18/03_CHART_PRODUCTION_HANDOFF.md (본 문서)

## L. 참고 자료

오전 research handoff:
- docs/chart/research/CHART_RESEARCH_HANDOFF_2026-09-18.md

오전 recalculation 상세:
- docs/chart/research/2026-09-18/01_chart_recalc_83.csv
- docs/chart/research/2026-09-18/02_chart_pending_dryrun.md

코드 변경:
- fix/chart-recalculate-eligibility-20260918 branch (PR #318)

## M. 다음 작업자 체크리스트

- [ ] repository pull 및 branch 확인
- [ ] PR #318 상태 확인
- [ ] production API 인증/접근 방법 확인
- [ ] 83명 preview 재확인 (EXPECTED 18/18)
- [ ] submitReview → snapshot 생성
- [ ] snapshot 검증 (25명 score=17, avg≈45.00)
- [ ] rebuildNationwidePublication
- [ ] rankings API 검증 (83명, rank 연속, 대표 5명 점수)
- [ ] 홈페이지 실시간 검증 (DATABASE = API = HOMEPAGE)
- [ ] #100 별도 처리 (83명 완료 후)

---

**PRODUCTION DB CHANGED: NO**
**MERGED: NO**
**DEPLOYED: NO**

NEXT ACTION AT HOME: "83명 production preview 재확인 → submitReview → snapshot → rebuild → 홈페이지 검증"