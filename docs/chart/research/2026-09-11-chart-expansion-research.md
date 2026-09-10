# Chart 확장 조사 — 지역별 결과 (2026-09-11)

> 상세 payload: `2026-09-11-chart-expansion-research.json` (동일 디렉터리).
> 읽기 전용 조사. DB 미반영. production write / commit / push / PR / merge / deploy 없음.

## 권역 요약

| 권역 | 대상 지역 수 | EVALUATABLE | PENDING/리드 | 후보 0명 지역 수 |
|---|---|---|---|---|
| 전북 | 14 | 0 | 3 (전주 1·군산 2) | 12 |
| 전남 | 22 | 0 | 0 | 22 |
| 경기 (분당 제외) | 32 | 1 (용인) | 3 (수원 2·용인 1) | 30 |
| 제주 | 2 | 0 | 0 | 2 |
| 강원 | 18 | 0 | 1 (원주) | 17 |
| 합계 | 88 | 1 | 7 | 83 |

## EVALUATABLE (등록-준비, DB 미반영)

- **이수정** — `gyeonggi-yongin` / 닥터수밀크잉글리쉬어학원 원장. 실명·소속·지역·2026-07 근거 3건·철학 2문장·커리큘럼(MILK/LIME) 확인. 중복 아님.
  실행: ADMIN 세션에서 `save_research_drafts → preview_scores → submit_batch → complete`.

## PENDING / 리드 (보류 — 등록하지 않음)

| 후보 | 지역 | 사유 |
|---|---|---|
| 윤성중 (YEA매직) | jeonbuk-jeonju | 최근연도 근거 없음 (최신 2024-07) |
| Amy (리딩맥) | jeonbuk-gunsan | 한글 실명 미확보 |
| 장용호 (시드니) | gyeonggi-suwon | 최근 근거 없음 (블로그 2018 정지) |
| 최현숙 (시드니) | gyeonggi-suwon | 최근 근거 없음 (공동원장, 1인 여부 판단 필요) |
| 써니쌤 (에이블) | gyeonggi-yongin | 실명 불완전 + 개인 직결 근거 없음 |
| 원장 성명 미확인 (전문가집단) | gangwon-wonju | 실명 없음 (원장 직강·커리큘럼은 우수) |
| 작성자 미확인 (이레) | jeonbuk-gunsan | 실명 없음 (제일고 내신 자료 2024만) |
| 위영선 | 범위 외 (대구) | 주소 대구 지범로 192 — 4개 권역 대상 아님 |

## 후보 0명 지역 (83곳, 억지 채움 없음)

- 전북 (12): jeonbuk-iksan, jeonbuk-jeongeup, jeonbuk-namwon, jeonbuk-gimje, jeonbuk-wanju, jeonbuk-jinan, jeonbuk-muju, jeonbuk-jangsu, jeonbuk-imsil, jeonbuk-sunchang, jeonbuk-gochang, jeonbuk-buan
- 전남 (22): jeonnam-mokpo, jeonnam-yeosu, jeonnam-suncheon, jeonnam-naju, jeonnam-gwangyang, jeonnam-damyang, jeonnam-gokseong, jeonnam-gurye, jeonnam-goheung, jeonnam-boseong, jeonnam-hwasun, jeonnam-jangheung, jeonnam-gangjin, jeonnam-haenam, jeonnam-yeongam, jeonnam-muan, jeonnam-hampyeong, jeonnam-yeonggwang, jeonnam-jangseong, jeonnam-wando, jeonnam-jindo, jeonnam-sinan
- 경기 (30): gyeonggi-suwon 이외 31곳 중 PENDING 2곳(수원 2명은 동일 지역) 제외 — gyeonggi-suwon은 PENDING 보유이므로 0명 명단에서 제외. 0명: gyeonggi-goyang, gyeonggi-yongin 제외(등록-준비 1·PENDING 1 보유), gyeonggi-bucheon, gyeonggi-ansan, gyeonggi-anyang, gyeonggi-namyangju, gyeonggi-hwaseong, gyeonggi-pyeongtaek, gyeonggi-uijeongbu, gyeonggi-siheung, gyeonggi-paju, gyeonggi-gimpo, gyeonggi-gwangmyeong, gyeonggi-gwangju, gyeonggi-gunpo, gyeonggi-osan, gyeonggi-icheon, gyeonggi-yangju, gyeonggi-anseong, gyeonggi-guri, gyeonggi-pocheon, gyeonggi-uiwang, gyeonggi-hanam, gyeonggi-yeoju, gyeonggi-dongducheon, gyeonggi-gwacheon, gyeonggi-gapyeong, gyeonggi-yangpyeong, gyeonggi-yeoncheon, gyeonggi-seongnam-sujeong, gyeonggi-seongnam-jungwon
- 제주 (2): jeju-jeju, jeju-seogwipo
- 강원 (17): gangwon-wonju 제외(리드 1 보유). 0명: gangwon-chuncheon, gangwon-gangneung, gangwon-donghae, gangwon-taebaek, gangwon-sokcho, gangwon-samcheok, gangwon-hongcheon, gangwon-hoengseong, gangwon-yeongwol, gangwon-pyeongchang, gangwon-jeongseon, gangwon-cheorwon, gangwon-hwacheon, gangwon-yanggu, gangwon-inje, gangwon-goseong, gangwon-yangyang

참고: `gyeonggi-seongnam-bundang`은 RESEARCHED이므로 조사 대상이 아님 (제외 유지).

## 조사 한계 (정확히 보고)

1. 네이버 블로그 직접 fetch는 JS 셸만 반환 — 본문은 검색 발췌 + RSS 미러(오늘학교) 경유 확인. payload에 출처 한계 표기.
2. 일반 웹검색은 SEO 전화영어·프랜차이즈 본사 위주 — 개인 식별 원장 노출이 적어 0명 지역이 다수. 강제 채움 금지 원칙에 따라 0명으로 확정.
3. `activity_dates` 빈 배열·`name` null은 미확보 의미 (추측 기입 없음).
4. 실제 DB 반영 0건 — 실행 중인 앱/ADMIN 세션/DB 접속이 이 환경에 없음 (docker 부재, 3308/8080 리스너 없음, .envrc DB 설정 없음, prod RDS 접근수단 없음).
