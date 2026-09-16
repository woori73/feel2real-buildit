# FINAL QA — 신구중2 6회 70문항 (문제지 + 정답해설)

## 1. Q1 복수답항 검증
- 발문: "밑줄 친 ... 바꿔 쓸 수 있는 것을 모두 고르면? (정답 2개)" → 복수정답 요구 YES
- Blueprint Q1: TRANSFORM(단일→복수+화용), "paraphrase 2개 동시 선택" → 복수 의도 YES
- 정답 ②(How do I get…)/④(show me the way) 모두 길 안내 동일 화행 → 모두 정답 YES
- 판정: Q1 intentional multi-answer: YES. LOCK 유지, 수정 없음.

## 2. 무결성 (프로그램 실측)
- Questions: 70/70 (first=1, last=70, 순서 정상)
- Duplicate qnum: 0 / Missing: 0
- 선택지 누락: 0 (전 문항 choice-item 5+ 또는 choice-table 보유 확인)
- paper-answer sync: 70/70 (BATCH 정답키와 불일치 0)
- Content changed: NO (누적 LOCK본 대비 FINAL 전 문항 텍스트 대조, 누락 0)
- Answer distribution (실측, Q1 복수 전개 총 71답항): ①×13 ②×16 ③×12 ④×17 ⑤×13
- 최대 동일정답 연속: 2 (유일 사례 Q1④–Q2④. 단답 기준 연속 중복 0)

## 3. FORMAT MASTER 적용
- 기준: 4회 70문항 8PAGE FINAL v15 BROWSER_VERIFIED (8pp·70문항·run-header·N/8 푸터 실측 확인)
- 적용: A4 고정 페이지, exam-header(이름/요일/점수), run-header(p2~), N/11 푸터, 2단, qblock avoid-break, choice-table, print CSS — 동일 구조
- 레이아웃 한정 조정 (내용 불변): Q41 보기 8.5pt, Q43 보기 8pt+행간 축소, choice-table 8.5pt, 컬럼 간격 7px

## 4. 페이지수 판정: 11 pages (8페이지 목표 대비 보고)
- 실측 블록 높이 + 최적 packing(DP 전수 탐색) 결과:
  - 8 pages: 약 960px 초과 (불가)
  - 9 pages: 약 963px 초과 (불가)
  - 10 pages: 약 12px 초과 (불가)
  - 11 pages: 초과 0 (최소 여유 40px)
- 원인: Q41/Q43 메가 Word List + 표 12개 + 장문 지문의 물리량. CONTENT LOCK(문장 삭제·축약·재배열 금지) 하에서는 8페이지 수납 불가.
- 원칙("무조건 8페이지보다 잘림 0 + 가독성 + 균형 우선")에 따라 11페이지 확정.
- 공통지문 Group: [Q1~Q4+대화 2종] p1, [Reading+Q5~Q8] p2 — 분리·orphan 없음.

## 5. BROWSER VISUAL QA: PASS
- Chromium 실측, print 미디어 + fonts.ready 대기 후 전 페이지 DOM 전수 확인
- p1 EXAMHEAD + 이름/요일/점수, p2~p11 RUNHEAD, 푸터 1/11~11/11
- Q1~Q70 전부 존재·순서 정상, 지문/발문/선택지/보기/밑줄/빈칸/표/기호 정상
- p1·p7 스냅샷 육안 확인 (헤더·메가 문항·표 정상)

## 6. PRINT QA: PASS
- Chrome Print-to-PDF 실측: 11 pages (전 페이지 문자량 1800~3200, 빈 페이지 없음)
- Cutoff: 0 / Overflow: 0 (전 컬럼 scroll≤client 실측) / Missing choices: 0 / Orphan passage/group: 0 / Abnormal blank: 0 (p7 Q42 단일 컬럼은 메가 분절 구조상 허용, 보고)

## 7. CONTENT HOLD: 없음 (FAIL/HOLD 0)
- 편집 중 문항 오류(복수정답·Source·문법·정답·지문 누락) 신규 발견 없음
- Q44·Q49는 승인 BATCH 근거(Blueprint 설계 정답) 그대로 유지

## 8. FINAL 산출물 (LOCK batch 파일 미수정)
- 신구중2_2026_2학기_중간고사_대비_6회_70문항_문제지_FINAL.html (11pp, 70문항)
- 신구중2_2026_2학기_중간고사_대비_6회_70문항_정답해설_FINAL.html (빠른 정답표 + 70 해설)
- FINAL_QA.md (본 파일)

## 9. 상태
- CONTEXT STATUS: PARTIAL (1~3회 전지 미열람)
- R1~R3 limitation: 유지 (FINAL 전 Wiki 열람 조건 잔류)
- 기존 LOCK 파일 보존: Q01-Q70_paper.html, Q41/Q51/Q61 BATCH + paper 전부 불변
- commit/push/PR/merge/deploy: 미실시 (사용자 최종 확인 대기)
- Wiki 업로드: 미실시
