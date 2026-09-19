# STEP 2 배치 2 — 신규 등록 1명 + HOLD 승격 4명 (2026-09-19 ~06:10 KST)

작성: BuildIt Chart 작업 세션(집 PC). 경로: `research-drafts` 1건, `enrichment` + `reevaluate` 4건.
**중간 publication rebuild 없음. review-batches / decision / finalize / publications/rebuild 미실행.**

## 1. 승격·등록 결과

| id | 이름 | 학원 | 지역 | 경로 | preview | evidence 수 |
|---|---|---|---|---|---|---|
| 117 | 김지환 | 에이닷영어학원 양산지점 | 경남 양산시 물금읍 | `research-drafts` → EVALUATABLE | 56 | 2 URL |
| 82 | 정희수 | 정희수 영어수학학원 | 경북 경산시 | `enrichment`(요약3+strengths+evidence3) → `reevaluate false` | 75 | 4 |
| 91 | 신희진 | 과천보듬교육학원 | 경기 과천시 | `enrichment`(philosophy 1필드) → `reevaluate false` | 65 | 4 |
| 96 | 홍세미 | HLS어학원 | 경기 성남시 중원구 | `enrichment`(philosophy) → `reevaluate false` | 65 | 4 |
| 66 | 문나경 | 기지시GNB영어수학학원 | 충남 당진시 | `enrichment`(evidence 2건) → `reevaluate false` | 79 | 5 |

합계: 기존 공개 83 + 배치1 8명(#100,#110~#116) + 배치2 5명 = **96명 공개 가능**, 신규 누적 13명.
17명 정지 조건까지 **4명 남음**.

## 2. 검증한 원 출처 (날짜는 화면 표시 그대로)

- #117: `m.blog.naver.com/adot_yangsan/224270702693`("2026. 4. 30. 18:00", "에이닷 영어학원 양산지점 원장 김지환입니다", 시험=분석 도구/피드백=성장의 시작, 2주 플랜), `224388814781`("2026. 8. 24. 19:19" — `rss.blog.naver.com/adot_yangsan.xml` pubDate `Mon, 24 Aug 2026 19:19:46 +0900` 일치; 4단계 절차·매 수업 1:1 지문/문장/어법 확인, 주소 경상남도 양산시 물금읍 범어로 72 701호)
- #82: `dbfla74/224301523849`("2026. 5. 31. 10:50", 재능보다 습관·하루 10~20개 단어를 쉐도잉·오답 노트 학습법으로 서술), `dbfla74/224264153734`("2026. 4. 24. 21:51", 학교별 기출 반영·고난도 별도 분석·구문 세부 분석·등급별 개별 Teaching), `dbfla74/224169238289`("2026. 2. 2. 21:26", 초등 파닉스→원서, 중등 문법+지역 기출, 고등 EBS·빈칸 추론, 수학 개념→유형→심화, 원장 직강·레벨 테스트). ※ `dbfla74/224331154286`("2026. 6. 30. 0:03")은 본문이 유튜브 링크 한 줄이라 근거로 쓰지 않음.
- #91: `bodeum_education/224407624398`("2026. 9. 10. 22:20", "영어를 감으로 풀지 않고 원리로 읽는 것", 품사→5형식→실전→서술형 순 커리큘럼, 자체 교재 반복, 수업 후 클리닉 과제→오답→질문→해결)
- #96: `hyl0715/224397777081`("2026. 9. 1. 21:55", 개근 Reward, "출석이 습관이 되고 습관이 실력이 되고", "영어는 오래 배우는 과목이 아니라 제대로 배우면 빨리 끝나는 과목", 주소 경기도 성남시 중원구 금빛로 49 4층)
- #66: `dwstar726/224356449733`("2026. 7. 24. 12:29", "GNB영어수학 원장 문나경입니다", 등원·수업 시간표·결석 보강·교육비 산정, 8/3~8/17 클래스별 단어 챌린지+장학금), `dwstar726/224131403873`("2026. 1. 2. 11:50", "기지시에서 16년째 아이들을 지도", 꾸준함이 성적을 올리는 힘)

## 3. STRUCTURAL FINDING 2 — evidence 는 URL 당 1행, 문구 정정 경로 없음

- `chart_candidate_activities` 는 동일 candidate + 동일 `source_url` 재삽입이 INSERT IGNORE 로 무시된다(#117 에 4행을 넣었지만 `evidenceCount` 는 2). → **"URL 근거 없음" 계열 게이트(OFFLINE_ACTIVITY 부재)는 반드시 새 URL 로만 해소 가능.** 같은 URL 에 kind 를 바꿔 추가하는 방법은 작동하지 않는다.
- `ChartCandidateEnrichmentService` 는 요약 4컬럼·strengths·improvement·evidence INSERT 만 가능. **이미 들어간 evidence 의 `content_summary` 를 수정·삭제하는 공식 API 는 없다.**

### 3.1 알려진 데이터 결함 2건 (오타, 정정 경로 없음)

운영자 작성 요약 문구에 비한자어 문자가 섞였다. URL·날짜·evidenceKind·판정 근거는 모두 정확하고 점수·eligibility 에 영향 없다. 단 공개 상세(`GET /api/chart/educators/{id}`)의 evidence 요약에 노출된다.

- candidateId 82, `m.blog.naver.com/dbfla74/224169238289` 행: 저장 문구 "…정희수영수학원 명의 [cyrillic word] 맺었다" — 한국어 대신 Cyrillic 단어가 혼입됨.
- candidateId 66, `blog.naver.com/dwstar726/224356449733` 행: 저장 문구 "…원장 문나경[CJK 2자]의 여름방학 안내…" — "작성" 자리에 CJK 글자가 혼입됨.

조치: 이후 모든 쓰기 페이로드는 POST 전에 비한국어 문자(CJK·kana·키릴) 정규식 검사로 차단한다(#2장 이후 적용). 정정이 필요하면 evidence 요약 수정 endpoint 를 별도 작업으로 추가해야 한다(이번 범위 밖).

## 4. HOLD 보강 잔여 (12명 중 남은 7명)

dryRun holdReasons 기준 실패 게이트 (2026-09-19 16:27 확인):

| id | 이름 | 학원 | 남은 게이트 | 해소 요건 |
|---|---|---|---|---|
| 59 | 김요한 | ITC영어 청주테크노폴리스 | 비광고 교육 근거 | 새 INFORMATIVE URL |
| 62 | 신원경 | 윤선생IGSE 옥천 | 교육철학 | 개인 교육철학 공개 근거(브랜드 슬로건 불가) |
| 77 | 채은숙 | 김앤채 영어교습소(고령) | 철학+커리큘럼+비광고 근거 | 3개 모두 |
| 80 | 토마스 | 중앙K 영어(경주) | OFFLINE_ACTIVITY URL | 새 대면 활동 URL |
| 83 | 성진경 | 이엠학원(포항) | 철학+커리큘럼+비광고 근거 | 3개 모두 |
| 84 | 박명숙 | 벨영어교습소(상주) | 철학+커리큘럼 | 본문 확인 후 요약 2종 |
| 94 | 장한 | ITC영어 하남미사 | 커리큘럼+비광고 근거 | 새 INFORMATIVE URL + 요약 |
| 99 | 손성하 | 샤인영어교습소(창원) | OFFLINE_ACTIVITY URL | 새 대면 활동 URL |

13·84·62·99·80 은 조사 의뢰 완료(배경 리서치 에이전트). 승격 성공 시 96+4 = 100명 선까지 여유 확보.

## 5. 운영 메모 — 16:25Z Deploy 로 일시 502

배치 2 마지막 쓰기(#66 `reevaluate?dryRun=false`, 16:27) 성공 직후 `feel2real.co.kr` 전체가 502. 원인은 타 세션의 수동 **Deploy to EC2 (manual)** run #35454901266(2026-09-19T16:25:21Z 시작, in_progress) — 이 작업의 쓰기가 원인 아니다. 배포 완료까지 API 읽기·쓰기 모두 대기. #66 승격은 배포 이전 커밋된 상태이므로 손실 없음(사후 `GET /api/chart/educators/66` 으로 EVALUATABLE 재확인 필요).

쓰기 가드(이후 모든 페이로드에 적용): POST 직전 `JSON.stringify(payload)` 에서 다음 매치 발견 시 예외로 중단한다.
`/[\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\u0400-\u04ff]/`(일본 가나·CJK 확장·CJK 통용·키릴) — 한국어 음절 `\uac00-\ud7af` 는 허용 범위로 별도 검사하지 않는다.
