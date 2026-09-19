# ROUND4 70 STRUCTURE ANALYSIS — 6회 PRIMARY FORMAT REFERENCE

- 파일명 = 신구중2_2026_2학기_중간고사_4회_70문항_문제지_8PAGE_FINAL_v15_BROWSER_VERIFIED.html
- 정확한 경로 = work-output/round4-70/ (repo local, Downloads 사본과 동일본)
- 총 문항 = 70 (data-qnum 1~70 연속, qprompt 70)
- 실제 인쇄 페이지 수 = 8 (class=page 8개)
- 공통지문 Group 수 = 명시적 [n-m] 표기 없음. 지문 공유는 발문(윗글/다음 대화/다음 글)으로 연결. 대화 Group 2개(길안내 Q13~17, 추천 Q43~48) + 윗글 참조 산재.
- 2단 구조 = A4 2단. @page 2개, @media print 1개, page-break 5개, column 3개 언급.
- 주요 table/보기 구조 = table 2개, cond-box/view-box 11개, underline 28개. 조합표 1개(Q55 (a)~(c)).
- HTML/CSS 판독 = 완료(발문 전수·구조 요소 계수). qblock 70개(정규식 50개는 중첩 div로 과소집계, data-qnum 70개가 정본).
- BROWSER VERIFIED 여부 = 파일명상 VERIFIED. 본 작업에서 재렌더 검증은 미실시(6회 제작 시 Chrome Print QA로 재확인).

## 구간 구조 (4회 실제 흐름)
- Q1~12: 빈칸+일치+밑줄+관계대명사 혼합 오프닝. 짧은 단독·윗글 참조 교대.
- Q13~18: 길안내 대화 Group 5문항 연속.
- Q19~26: 관계대명사 어법 집중 8문항(짝짓기·생략·공통빈칸·틀린문장).
- Q27~42: 빈칸+일치+보기진술+영영+시간순서+Schofield 소재 혼합 중반.
- Q43~48: 추천 대화 Group 6문항 연속.
- Q49~70: 빈칸+어법+영영+보기진술 후반. Q55 조합표, Q60 정의+예문.

## 6회에서 그대로 계승할 구조
- 70문항 전량·8페이지 분량감·A4 2단·qblock 비분리·table/보기 box DOM·data-qnum 연속성·빠른 정답+문항별 해설 구성·대화 Group 연속 배치·어법 집중 구간 배치.

## 6회에서 계승하지 않고 새로 설계할 내용
- 문제 내용 전부(중복검사 대상). `[n-m]` 명시 Group 표기(4회 무표기 → 6회는 5회식 명시 표기). 단순 빈칸 22개의 밀도(6회는 결합형으로). 대화 소재(길안내·추천 재사용 금지). Schofield 소재 재사용 금지. Q55 단일 조합표를 4요소·진위결합형으로 교체.
- 오늘 표준 추가 적용: column 독립 간격, 하단 여백 문항간 분배, ⑤/보기/표 하단 안전, 지문-첫문항 근접.
