# 신사중3 Boiling Frog Q1~Q40 최종 QA 보고서 (2026-09-18)

> 대상: `신사중3_Boiling_Frog_40문항_문제지_DRAFT.html`(통합 문제지),
> `신사중3_Boiling_Frog_40문항_정답해설_DRAFT.html`(통합 정답해설)
> 기준: 배치 HTML 4종(Q01_Q10 / Q11_Q20 / Q21_Q30 / Q31_Q40) + 배치 정답키(ANSWER_KEY_Q01_Q10 / Q11_Q20 / Q21_Q30 / Q31_Q40) +
> `SOURCE_ANALYSIS_BOILING_FROG.md`(S1~S31). 실제 학교 원본·기출 이미지 재판독은 범위 밖이며 S번호는 분석 기록 전사문 기준.

## 1. 최종 정답표 (배치 검수 확정 기준)

| Q1~10 | ② ② ③ ① ① ② ①·④ ③·④ ⑤ ②·③ |
| Q11~20 | ⑤ ④ ④ ② ④ ⑤ ② ① ② ①·⑤ |
| Q21~30 | ② ③·⑤ ① ⑤ ③ ①·⑤ ① ③ ③ 서답(Q30) |
| Q31~40 | ③ ⑤ ② ③ ③·⑤ ①·⑤ 서답(Q37) ③ ② 서답(Q40) |

- 서답 모범답안 Q30: Net zero means reducing greenhouse gas emissions so that the amount added is balanced by the amount removed. (15단어)
- 서답 모범답안 Q37: A 1°C rise may not sound significant because an average can hide much larger changes in particular places. (17단어)
- 서답 모범답안 Q40: Like the frog in the story, we can recognize the problem and change our actions before it becomes too serious. (19단어)

## 2. 자동 검증 결과 (PASS)

1. 문제지 문항 번호: Q1~Q40 40개 존재, 누락 0, 중복 0(단, [34~35] 공통지문 헤더 블록은 번호 공유 설계로 제외 후 판정).
2. 정답해설 블록: Q1~Q40 40개 존재, 누락 0, 중복 0.
3. 문항 구성: 전 문항 지문(passage/cond-box/poster) 또는 서답 답란 존재, 선택형 선택지 완비(Q10만 6선지, 복수정답형 설계).
4. HTML 종료 태그: 문제지·정답해설 모두 `</body></html>` 정상.
5. 브라우저 렌더링: 문제지 PASS(40문항/closing), 정답해설 PASS(40블록/closing).
6. 배치-통합 동기화: 40개 qblock 전수 비교 IDENTICAL(공백 정규화 기준).

## 3. 배치 수정 → 통합본 동기화 내역 (11건)

| # | 문항 | 동기화 내용 |
|---|------|-------------|
| 1 | Q2 | 발문을 요청-제안 화행형으로 교체 |
| 2 | Q3 | 발문을 출제용 재구성 개념 설명형으로 교체 |
| 3 | Q4 | (A)에 of warming above pre-industrial levels 복원, (D)에 keeping~instead of letting 확장 |
| 4 | Q6 | 발명에 가정적 조건('만약 ~한다면') 명시 → Since/If 모호성 해소 |
| 5 | Q10 | 지문에 S21 후반부·S22 문맥 추가, 정답 선지 ③을 원문 근거형 기준점 서술로 교정 |
| 6 | Q12 | 선택지 ②를 consequences(자기 참조 불성립 오답)로 교체 |
| 7 | Q13~15 | 공통지문 정답 노출 제거(( ㉠ )·( ㉡ ) 빈칸화), Q14 현재분사구문형·Q15 영영풀이형으로 재설계 |
| 8 | Q18 | (A)에 S2~S5 개구리 이야기 전체 복원 |
| 9 | Q19 | 선택지 ⑤를 독립 거짓 선지(새로 만든 문구)로 교체 |
| 10 | Q20 | 발문을 "다음 개념 설명을 읽고"로 정리 |
| 11 | Q35 | 변형 지문 명시 + ⓒ they(소유격 오류) + ⓔ is expected(수일치 오류)로 교정, 정답 ③·⑤ 정합 |

- 정답해설 동기화: Q10 오답 서술(⑤악화·⑥임박한 위험 회피 불일치), Q12 오답 서술(② 자기 참조 불성립) 갱신.
- Q35~Q40 해설 6블록 신규 추가(Q34 이후 이어쓰기, Q1~Q34 untouched).

## 4. 수동 QA (발문-밑줄·보기·유일성)

- 발문-밑줄: Q7·Q26·Q35(ⓐ~ⓔ) 지문 일치, Q10(밑줄 3개) 일치, Q14(( ㉠ ))·Q15(( ㉡ )) 일치.
- 발문-<보기>: Q3·Q28(<보기> ㄱ~ㄹ) 일치, Q32(주어진 문장)·Q38(<보기> 상관비교급) 일치.
- 정답 유일성: 배치 정답키에서 전 문항 독립 판정 완료(위 정답표). 통합 정답해설 ans-grid 4행과 일치.
- 문제지-정답해설 번호 일치: 1~40 양쪽 일치.

## 5. 미해결 (차기 조판 단계, 본 QA 범위 밖)

- 문제 번호 옆 점수 표기·"(정답 2개)" 노출은 내신 시험지 제작원칙 §1 위반 → 조판 단계에서 일괄 제거 필요(문항 정합성과 무관).
- Q12 ② 자기 참조 오답의 시험장 명료성, Q19 포스터 ③의 net zero 개인 연결 수용성은 난이도 관점 잔여 리스크(정답 유일성 영향 없음).
- Q31 ②↔③ 논쟁성(방출 vs 열가둠): ③ 우위 명확, 이중 근거 결함 아님으로 판정 유지.

## 6. 산출물

- 통합 문제지: `신사중3_Boiling_Frog_40문항_문제지_DRAFT.html` (Q1~Q40 + Batch 구분선 + closing)
- 통합 정답해설: `신사중3_Boiling_Frog_40문항_정답해설_DRAFT.html` (정답표 + Q1~Q40 해설 40블록 + closing)
- 배치 문제지 4종 + 배치 정답키 4종(ANSWER_KEY_Q01_Q10 / Q11_Q20 / Q21_Q30 / Q31_Q40): 수정 없음(이미 확정 상태)
- 본 보고서: `QA_REPORT.md`
- 상태: 커밋·푸시 미실행(지시에 따라 STOP 전 보고)
