# STEP 2 배치 4 — 신규 17명 달성, STOP 지점 보고 (2026-09-19 16:50~16:55)

`enrichment` + `reevaluate?dryRun=false` 1건(#94). **중간 publication rebuild 없음 — 17명 전부 `published:false` 확인.**

## 1. #94 장한 (ITC영어 하남미사점, 경기 하남시) — 승격 67점

- 해소한 게이트: 커리큘럼 요약 + 비광고 INFORMATIVE 근거
- 근거: `blog.naver.com/itchenry/224189434540`("2026. 2. 21. 7:30" — 12단계 커리큘럼, 4~5개월 단위 단계, 단계 종료마다 학부모 앞 공개수업), `224325128203`("2026. 6. 24. 0:19" — 3월 신학기 지점 설명회 학부모 40명 참석, 하루 600회 이상 발화 중심 수업)
- **동일인 검증**: 같은 블로그의 자기소개 글 `223493479997`("2024. 6. 27. 19:35")에서 필자 "ITC헨리"가 "하남미사 지점 장한 원장", "제 영어 이름은 Henry"라고 밝혔다. 2026 글의 운영 진술을 장한 개인 활동으로 인용한 근거가 이것이다.

## 2. STOP 조건 달성 — 신규 EVALUATABLE 17명 (live `scores/stale` = 17건)

| candidateId | 이름 | 학원 | 지역 | score | evidence | 경로 | published |
|---|---|---|---|---|---|---|---|
| 100 | 강수민 | 링키영어 대원포레나점 | 경남 창원시 | 61 | 2 | reevaluate | false |
| 110 | 조유진 | 리딩온어학원 | 경남 진주시 | 58 | 3 | research-drafts | false |
| 111 | 이상민 | 솔민학원 | 경남 함안군 | 49 | 2 | research-drafts | false |
| 112 | 윤혜은 | 윤혜은영어학원 | 경남 양산시 | 61 | 2 | research-drafts | false |
| 113 | 남경채 | 이지스어학원 | 경남 양산시 | 61 | 2 | research-drafts | false |
| 114 | 백승일 | 조슈아영어 통영학원 | 경남 통영시 | 44 | 2 | research-drafts | false |
| 115 | 박송미 | 리드앤톡 밀양삼문센터 | 경남 밀양시 | 63 | 3 | research-drafts | false |
| 116 | 김서애 | 제이+ 영어교습소 | 인천 부평구 | 63 | 3 | research-drafts | false |
| 117 | 김지환 | 에이닷영어학원 양산지점 | 경남 양산시 | 56 | 2 | research-drafts | false |
| 82 | 정희수 | 정희수 영어수학학원 | 경북 경산시 | 75 | 4 | enrichment+reevaluate | false |
| 91 | 신희진 | 과천보듬교육학원 | 경기 과천시 | 65 | 4 | enrichment+reevaluate | false |
| 96 | 홍세미 | HLS어학원 | 경기 성남시 중원구 | 65 | 4 | enrichment+reevaluate | false |
| 66 | 문나경 | 기지시GNB영어수학학원 | 충남 당진시 | 79 | 5 | enrichment+reevaluate | false |
| 99 | 손성하 | 샤인영어교습소 | 경남 창원시 | 75 | 4 | enrichment+reevaluate | false |
| 80 | 토마스 | 중앙K영어 | 경북 경주시 | 94 | 5 | enrichment+reevaluate | false |
| 84 | 박명숙 | 벨영어교습소 | 경북 상주시 | 70 | 4 | enrichment+reevaluate | false |
| 94 | 장한 | ITC영어 하남미사점 | 경기 하남시 | 67 | 4 | enrichment+reevaluate | false |

- 점수 범위 44~94(합 1106, 평균 65.1). 최고 #80 토마스 94.
- 기존 공개 83 + 17 = **100명 → TOP 100 구성 가능**(100위 밖 예상 0명).

## 3. 잔여 RESEARCH_PENDING (강제 승격하지 않았다)

- 구조적으로 공식 API 로 승격 불가 13명: 51, 52, 53, 55, 78, 79, 90, 93, 101, 102, 106, 108, 109 (confirmation boolean 컬럼이 게이트에 직접 쓰이고 enrichment 로 변경 불가 — handoff §7.3)
- 보강 여지 남은 4명: **59**(김요한, ITC 청주 — 비광고 INFORMATIVE 새 URL 필요), **77**(채은숙, 김앤채 고령 — 철학+커리큘럼+Informative 3개), **83**(성진경, 이엠 포항 — 3개), **62**(신원경, 윤선생IGSE 옥천 — 근거가 2025년·브랜드 본사 매체뿐이라 현 기준에서 승격 불가 판정)
- 권역 조사 결과 0건: 남해군(인스타 전용), 사천시(실명 근거 없음 — 영어훈련소 PARTIAL)

## 4. 다음 단계 (사용자 승인 대기 — 아직 실행 안 함)

1. `POST /api/admin/chart/tools/review-batches` (위 17명만; 신규·변경 후보 한정, 기존 83명 재처리 없음)
2. `decision APPROVED` → `finalize`(score snapshot) → `POST /publications/rebuild`
3. 검증: public count 100 / RANKED 1~100 연속 / candidateId ≠ rank 확인 / `feel2real.co.kr/chart` UI 100위 표시
4. 참고: 100명 정확히 일치 시 DISCOVERED 0. 여유 1명을 더 승격하면(59/83 등) 100위 밖 1명 = Goal B 실제 사례 검증 가능.
5. `regions/*/complete` 는 아직 미호출(gyeongnam-namhae, gyeongnam-sacheon IN_PROGRESS 로 방치 — 자동 게시 방지).
