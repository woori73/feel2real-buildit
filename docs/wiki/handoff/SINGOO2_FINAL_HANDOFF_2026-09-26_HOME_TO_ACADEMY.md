# 신구중2 · FINAL/직보 HOME→ACADEMY 핸드오프 (2026-09-26)

> 세션: Agent 6 (HOME) → 학원 PC 새 세션 인계 · 작성 시각 12:45 이전
> 이 문서가 유일한 handoff 계약서다. 학원 세션은 브랜치 fetch 후 이 문서 전체를 가장 먼저 읽는다.

---

## A. 시험 일정 (TARGET 고정)

- 대상: **신구중학교 2학년 · 영어 · 2026학년도 2학기 중간고사**
- 시험일: **2026-09-30 (수)**
- 마지막 수업: **2026-09-29 (화)** — FINAL TEST 40 실전 확인용 (틀린 문항은 수업 중 바로 해설 후 종료)
- 시험범위: 5과 The City as a Canvas + 6과 The Greatest Love for All (실측 근거: 주간 모의고사 9회 헤더, 학교 프린트 4종, Digital Twin id=2)
- 형식: **2학기 중간 서답형 없음** (박원장 Insight id=10 실측) → 전 문항 객관식

### 프로그램 명칭 규약 (중요)
- 전체 프로그램 분류명: **직보**
- 직보 내부 학생 흐름: ① FINAL 복습집 → ② FINAL TEST 40 → ③ 시험 직전 최종 점검
- **"시험 직전 최종 점검"은 전체 직보의 이름이 아니다.** 시험 당일(수) 학생이 보는 마지막 자료(6p)만 그렇게 부른다. 학생 표시 제목은 반드시 "시험 직전 최종 점검"으로.

## B. Source inventory (전부 실측 — 추측 아님)

| 원천 | 실측 내용 |
|------|-----------|
| 실제 기출 (1학기말) | 발문 DNA: 영영 없음찾기 / 있는대로 복수정답 / 개수 판단 / 짝짓기 / 바꿔쓰기 / 오역찾기 / 심정 / 순서 배열. Q1 "Are you sure?" 선지, Q9 어법 개수(builded), Q17 관계사 등 재활용 |
| 5·6과 학교 프린트 | sg6_print_11/12/112/113 (Word List 영영 원문, 어법1 관계사·어법2 so~that·지각동사·used to, 지문 A~C) |
| 기존 모의고사 9회 | w3(40) w4(70) w10(40) w13(70) w18(40) w21(70) w24(오답모의 27문항) w25(70) w29(70) — coverage 실측: used to 201 · 수동태 103 · 부사적용법 82 · enough to 48 · so~that 44 등 (work-inspect/sg6_coverage_report.txt) |
| 실제 수업오답 | 6과 필기 빨간 교정 3건 (①studied hard so that ②difficult enough to finish ③too loud to sleep) + w24 오답모의 27번 was died/buried. 학생 오답 DB 저장분 = 0 (가짜 오답지 제작 안 함) |
| 박원장 Insight | id=10 (서답형 없음+유형DNA+전환주의), id=13 (historic/historical 문항 직접 요청 → TEST Q35 반영), id=14 (기존 모의 결함 Q27/Q35/Q47 — id=13 산출물에서 해소 확인됨, OPEN 아님) |

## C. FINAL BLUEPRINT 요약

- 20개 리스크 포인트, A=12 · B=5 · C=3 (A=기출/프린트/실오답/Insight 중 실측 근거 필수)
- A-risk 전체: 목적격 관계사 · used to 3종 · so~that↔too~to 전환 · 지각동사 · 수동태(was died/p.p./수일치) · 영영풀이 · historic/historical · 바꿔쓰기 · 4형식 전치사 · continue/to-ing · 서답형 없음 전략 · favor
- TEST40 커버리지 매핑표는 TEST40 HTML P6에 내장 (A-risk 20/20)

## D. 현재 완성 파일 — 정확한 경로

GitHub 보존본 (이 브랜치, 이 디렉터리):
- `docs/wiki/handoff/singoo2-final-20260926/sg6_FINAL_BLUEPRINT.md`
- `docs/wiki/handoff/singoo2-final-20260926/sg6_FINAL_BOOK_v1.html` + `.pdf`
- `docs/wiki/handoff/singoo2-final-20260926/sg6_FINAL_TEST_40.html` + `.pdf`
- `docs/wiki/handoff/singoo2-final-20260926/sg6_JIKBO_full.html` + `.pdf`

HOME 로컬 원본 (미커밋 작업증거, 브랜치에 없음): `work-inspect/` 동명 파일 + 렌더 QA 스크립트·모의고사 원자료.
⚠ `work-inspect/`의 sg6_w*/ap_* 원자료는 학생 실제 답안·이름 포함 가능 → **어떤 브랜치에도 커밋 금지**.

## E. 페이지 수

- FINAL BOOK: **8p** (P1 ONE MAP / P2 VOCAB / P3-5 GRAMMAR / P6 5과 지문 / P7 6과 지문 / P8 TRAP)
- FINAL TEST 40: **8p** (P1-5 문제지 Q1-40 / P6 교사용 정답표+배점+분포+A-risk / P7-8 교사용 해설 40행)
- 시험 직전 최종 점검: **6p** (P1 ONE-PAPER / P2 TRAP 8+루틴 / P3 VOCAB·영영 / P4 SENTENCE / P5-6 LAST CHECK 10문항+정답)

## F. QA 결과 (2026-09-26 HOME 실측)

- 렌더: 전 파일 전 페이지 `scrollHeight−clientHeight = 0` (A4 210×297mm 고정, 맑은고딕, 하단 침범 0)
- PDF: /Count = 8 / 8 / 6 (printBackground, preferCSSPageSize)
- TEST40 기계검증: 40문항 data-answer 추출 — 분포 **①8·②8·③8·④8·⑤8**, 동일답 3연속 0건, 12345/54321 0건, 정답표↔속성 불일치 0건, 배점 2×25+3×10+4×5 = **100점**
- 최종 답렬: `5,1,2,3,4,1,3,5,2,4,3,5,1,4,2,2,4,3,1,5,1,5,3,2,4,5,2,4,5,1,3,1,3,3,4,1,4,2,5,2`
- LAST CHECK 10: 답 ③①⑤②④②⑤①④③ — 분포 2·2·2·2·2, 총 25점, 3연속·순열 패턴 없음
- CJK 감사: TEST40·JIKBO·BOOK 전부 의도적 한자 표기(名·形·必) 외 중국어 혼입 0건
- 복수정답 모호성 점검 완료: TEST40 Q7/Q16/Q24, 직보 Q3/Q10 선택지 재설계로 단일 정답 확정
- §9 준수: 지문·선지 전부 학교 프린트/Word List/기출 원문 기반 — 원문 밖 문장 생성 삽입 없음

## G. 알려진 미완료 작업

1. **(핵심) FINAL TEST 40 학생용/교사용 분리** — 현재 sg6_FINAL_TEST_40.html은 문제(P1-5)+정답표(P6)+해설(P7-8) 통합본. **이 상태로 학생 배포 금지.** 학생용에는 정답·해설·분포·A-risk·교사용 메모 노출 0이어야 함.
2. 학원 최종 산출물 4종 파일명 정리 (§8 참조) + 4종 PDF 재검증.
3. production 등록: **아직 안 함** (§9 GATE).
4. FINAL BOOK P8 TRAP / 직보 P2 TRAP의 문구 최종 검토는 박원장 실내용 검토 항목.

## H. 학원 FIRST TASK (순서 그대로)

1. `git fetch origin && git checkout docs/sg2-final-handoff-20260926`
2. 이 MD 전문 읽기
3. FINAL BOOK 실제 화면 검토 (8p)
4. FINAL TEST를 **학생용**(P1-5만) / **교사용 해설**(P6-8)로 분리 — 분리 후 학생용에 정답 문자열 0건 grep 확인
5. 시험 직전 최종 점검 학생 표시 제목 = "시험 직전 최종 점검" 확인 (sg6_JIKBO_full.html은 작업파일명 유지 가능)
6. 4종 browser QA (매 페이지 overflow 0)
7. 4종 print/PDF QA (/Count 확인)
8. 박원장 실제 내용 검토
여기까지. **아직 production POST 하지 않는다.**

## I. production 등록 상태

- `/api/final-deliverables` POST **0건** — 미등록.
- 등록 채널: 서명된 브라우저 세션(운영 위키 로그인)에서만. `?role=ADMIN`은 표시용이고 인증이 아님.
- GATE: 박원장 내용 검토 통과 후 **별도 승인** 시에만 final(kind=final)/직보(kind=jikbo) 등록. 기존 시험지 overwrite 금지.

## J. 변경 없음 확인

- DB: 없음 (migration 0)
- 코드: 없음 (src 변경 0)
- 기존 wiki 자료(기출·프린트·Insight·모의고사): 변경 없음
- merge: 없음 / deploy: 없음
- 이 브랜치는 handoff 보존 전용 — 커밋은 산출물 7종 + 이 문서뿐

## §8 최종 목표 파일명 (학원 생성분)

1. `신구중2_2026_2학기중간_FINAL_복습집.html` (+PDF)
2. `신구중2_2026_2학기중간_FINAL_TEST_40.html` (+PDF) ← 학생용, 정답 노출 0
3. `신구중2_2026_2학기중간_FINAL_TEST_40_해설.html` (+PDF) ← 교사용
4. `신구중2_2026_2학기중간_시험직전_최종점검.html` (+PDF)
