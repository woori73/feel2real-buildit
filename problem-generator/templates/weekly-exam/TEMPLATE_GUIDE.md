# BUILD IT Weekly Exam Template Guide

## Canonical templates
- `BUILD_IT_WEEKLY_EXAM_40_TEMPLATE.html`: 40문항 / A4 6페이지 / 2단. 기준본은 신구중2 2026 2학기 중간고사 5회 40문항.
- `BUILD_IT_WEEKLY_EXAM_70_TEMPLATE.html`: 70문항 / A4 8페이지 / 2단. 기준본은 압구정중2 2026 2학기 중간고사 4회 70문항.

## OpenCode / AI 필수 원칙
새 문제지를 만들 때 기존 회차 HTML의 CSS를 임의 복제하거나 처음부터 레이아웃을 만들지 않는다. 문항 수에 맞는 위 canonical template을 먼저 복사한 뒤 placeholder와 문항 content만 채운다.

## 레이아웃 불변 조건
1. 40문항은 정확히 6페이지, 70문항은 정확히 8페이지 A4 portrait 2단이다.
2. 첫 페이지 EduHolic 로고/제목/학교/학년/범위/이름·요일·점수 영역을 유지한다. `{{EDUHOLIC_LOGO_SRC}}`에는 승인된 EduHolic 로고 asset 또는 기존 승인본의 embedded data URI를 사용한다.
3. `.qblock`은 통째로 이동하며 페이지/단 경계에서 자르지 않는다.
4. 공통지문과 해당 문항 group은 반드시 연속 배치한다. 지문만 이전 페이지에 남기거나 31·36·38처럼 번호가 떨어진 상태로 한 지문에 묶지 않는다.
5. 문항 번호는 1부터 끝까지 순차적이어야 한다. 재배치로 번호가 바뀌면 정답·해설 번호도 동시에 동기화한다.
6. 보기 선택지는 ①~⑤, hanging indent, 지문 밑줄, `<보기>` box, 빈칸 표현을 유지한다.
7. 빈칸 2개는 (a)(b)로 통일하고 발문에서 어느 빈칸을 묻는지 명시한다.
8. 좌/우 단은 독립적으로 균형을 잡는다. 문항을 먼저 배치한 뒤 단별 `--qgap`을 조절한다. 하단에 큰 공백을 남기면서 다른 페이지를 빽빽하게 만들지 않는다.
9. overflow를 `overflow:hidden`으로 숨겨 해결하지 않는다. 잘림이 있으면 문항/group 자체를 이동하고 전체 pagination을 다시 균형 조정한다.
10. 화면 렌더만 보지 말고 Chrome print preview/PDF 기준으로 페이지 수와 하단 안전영역을 검증한다.

## FINAL QA
- DOM `.qblock` 실제 문항 수 = 40 또는 70
- `data-qnum` 중복/누락 없음, 1..N 순서
- 공통지문 group 연속
- 발문/지문/보기/선택지/마지막 문항 잘림 0
- 40문항 print 6페이지 / 70문항 print 8페이지
- 각 페이지·각 단 하단 여백이 과도하지 않고 전체 밀도가 균형적
- 정답·해설이 최종 문제 번호/선택지와 일치

## 제작 workflow
`내신 Wiki context/실제 기출/학교 학습지 재대조 -> Blueprint -> 정답 위치 사전 설계 -> matching canonical template 복사 -> 10문항 단위 제작 -> 독립 재풀이/정답 유일성 -> 중복검사 -> pagination 재편집 -> print QA -> 정답해설 sync -> FINAL`

## 금지
- template 자체의 문제 내용을 새 시험에 복사
- 학교 실제 기출/학습지 확인 없이 범용 문제 생성
- 마지막에 정답번호를 맞추려고 선택지만 기계적으로 섞기
- 공통지문과 문항 분리
- CSS 축소만으로 페이지 수 맞추기
- 잘린 내용을 hidden 처리
