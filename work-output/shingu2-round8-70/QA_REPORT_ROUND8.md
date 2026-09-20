# 신구중2 8회 70문항 — 출제 감수 / QA 보고서

- 대상: `신구중2_8회_70문항_문제지_DRAFT.html`, `신구중2_8회_70문항_정답해설_DRAFT.html`
- 생성 파이프라인: `maker/questions.mjs` + `maker/explanations.mjs` → `render*.mjs draft` → `maker/measure.mjs`(헤드리스 Chrome 실측) → `render*.mjs final <heights.json>`
- 검증 스크립트: `maker/validate.mjs` (내용·정답 축), `maker/qa_pdf.py` (인쇄 PDF 레이아웃 축 — 전 페이지 기하 검수)

## 1. 레이아웃 QA (실제 Chromium 렌더, 96dpi = 1mm ≈ 3.7795px)

| 항목 | 결과 |
|---|---|
| 페이지 수 | 10 / 10 (문제지·정답해설 모두 A4 10페이지 = 20컬럼, 목표 달성) |
| 컬럼 수 | 20 (maximin-slack DP로 최대 하단 여백을 최소화한 순차 분할) |
| 문항 블록 | 70 / 70, 중복 0, 누락 0, 번호 1~70 연속 |
| 하단 침범(breach) | 0 (본문 최하단 281.3mm ≤ 컬럼 하한 283mm ≤ 페이지 하한 287mm) |
| 잘림(clipping) | 0 · 빈 컬럼 0 |
| TOP ALIGN 위반(topOff) | 0 |
| 컬럼 간 간격 | min 14px / 문항 내부 간격 14~90px (패킹 계산과 표시 간격 동일 값) |
| 본문 폰트 | 12.6667px(9.5pt) — 축소 이력 없음, 11px 미만 단위 0 |
| 인쇄 규칙 | `@page A4 portrait margin:0`, `.page` 210×297mm, padding 12/11/10/11mm 유지 |
| 한글 줄끊김 | 절단 0 (`선행사`·`가능`·`형태`·`오답 포인트` 등 전수 확인) |
| 거터 위반 | 0 (좌 11~101.5mm / 우 108.5~199mm 경계 내) |

측정 정합성: 초안은 측정 컬럼 폭(343px)과 인쇄 컬럼 폭이 달라 Q39에서 19px 오차가 발생 → `render.mjs`의 `#measureCol`을 빈 보조 컬럼과 짝지어 인쇄 폭과 일치시킨 뒤 재실측, breach 0 확인.
인앱 브라우저가 `viewport=0x0 / navigate timeout`으로 unusable 상태가 된 뒤에는 `measure.mjs`(draft HTML에 프로브 주입 → `chrome --headless --dump-dom`)로 전환했고, 기존 인앱 실측값과 70개 단위 전부 0.0px 차이로 일치함을 확인했다.

## 2. 내용 QA

- 전 문항 5지선 객관식(선택지 350개), 서답형 0, `<img>` 0 — 기출 Q22 보기 이미지는 판독 불가로 추정·복원 출제하지 않음.
- 정답 분포 ①14 ②14 ③14 ④14 ⑤14, 동일 답 4연속 이상 없음.
- Lesson 배분: L5 = Q1~Q30, L6 = Q31~Q70.
- 발문의 밑줄·인용문 전수 대조: `<u>`·인용 영어 구절은 전부 해당 문항 또는 같은 그룹 지문에 실재 (Q7→Q4·Q5, Q13→Q11, Q40·Q42→Q39). remaining flags는 발문 강조용 우리말 인용(“모두 옳게”, “옳지 않은 것만을 모두” 등)이라 문제 없음.
- 공통지문: 지문과 첫 문항이 같은 컬럼에서 시작(블록 단위 패킹). 그룹 후속 문항은 "윗글"으로 참조하며, 세 투어 지문에는 A·B·C 라벨을 붙여 Q12 통합 문항의 참조를 명확화.
- 문법 출제 범위: 학교 인쇄본 5개 항목(목적격 관계대명사 / used to·be used to·There used to be / so~that / too…to·enough to / 지각동사+목적어+p.p.)으로 한정. 함정 A=Q28·29·30·61, B=Q49~52, C=Q46·47·48·58, D=Q53·54·55·61·67.
- Blueprint 예상정답과 실제 정답표 70/70 일치 (Q46 ⑤, Q69 ③ 포함).

## 3. 수정 이력(감수 지적 → 조치)

| 지적 | 조치 |
|---|---|
| Q42가 인용한 "Unbelievable!"가 Q38 지문에 있던 문제 | `[39~42]` 지문으로 옮겨 밑줄 처리, Q38은 자체 지문에서 `this important event` 선행내용이 성립하도록 재작성 |
| Q41이 연도(1916·1919·1920·1958·1970)를 함께 제시해 지문 없이 풀리는 문제 | 지문 근거 4단계 서사 배열 문항으로 교체, 정답 ① 유지 |
| Q40 발문이 우리말 번역 인용('건물 안에 숨어서') | 원문 인용 `was hiding in a building`으로 교체 |
| Q61 (a) 원문장이 비문법적 4형식, (e) 인칭 불일치 | `Give me a clue.` / `She heard…`로 정리 |
| Q23·Q27·Q29·Q35의 다의·판독 애매 문항 | 선택지·보기 재작성(전치사 of/for 중의 제거, 500m 거리 표현 정리 등) |
| 11페이지 초과 | 발문·선택지·표 셀 텍스트만 의미 보존 하에 정리(총 높이 16,327→15,401px) + 최대하 여백을 최소화하는 DP 패킹 → 10페이지. `font-size`·여백 축소는 사용하지 않음 |
| 해설 1페이지 우측 컬럼 잘림 | 가용 높이를 컬럼이 아니라 **페이지** 단위로 계산하도록 수정(전폭 헤더는 두 컬럼 모두에서 차감) |
| 한글 음절 절단(`선행/사`, `가/능`, `형/태`, `오답 포인/트`) | `word-break:keep-all` + 표 `colgroup`(열너비 지정) + 해설 라벨 열 15→18mm |
| Q21 표 행이 Q60 행과 중복 | Q21 A·C행을 다른 예문으로 교체(정답 ④ 유지) |

## 4. 알려진 편차 · 한계

- **컬럼 하단 여백 편차**: 문제지 최대 288px(76mm), 해설 최대 307px(81mm). 20컬럼 고정·순차 패킹에서 문항 높이 단위(183~329px) 때문에 남는 여백이며, DP로 최대값을 최소화한 결과다. 침범·잘림은 0.
- **해설 항목 구성**: 페이지 목표(10)를 맞추기 위해 해설에서 `발문` 행을 제거하고 필수 4항목(핵심 근거 / 오답 포인트 / 규칙 / 지문 근거)만 유지했다.
- **시각 검수 방식**: 인앱 브라우저의 스크린샷 API는 `viewport=0x0 / visible=false`로 여전히 사용 불가 → 실제 Chromium 인쇄 출력(`chrome --headless --print-to-pdf`, MediaBox 595×842pt = A4)을 PyMuPDF로 래스터화해 10페이지 컨택트 시트와 원해상도 페이지를 육안 확인하고, 전 20페이지는 `qa_pdf.py`로 기하 검수(PASS). pixel 스크린샷이 별도로 필요하면 브라우저 복구 후 재실행.
- Wiki(운영 데이터)는 읽기 전용으로만 사용; 등록·수정·승인 동작 없음. **본 문서의 두 HTML은 아직 DRAFT** — 운영 Wiki 등록/FINAL 확정은 별도 승인 필요.

## 5. 학원 PC 세션 (2026-09-20) — EduHolic 대형 로고 헤더 이식 + 재QA

- 복구 지점: `origin/docs/shingu2-round8-70-20260919` HEAD `37a85523` → 전용 worktree `feel2real-buildit-shingu2r8` (브랜치 `docs/shingu2-round8-70-academy-20260920`). 다른 worktree 미접촉.
- 헤더 Source of Truth: **신구중3 4회 문제지 등록본**의 EduHolic 대형 로고 헤더. 실제 로고(data-URI JPEG 4,199B)·구조(`logo-cell rowspan=2` + `title-cell colspan=5` + `school-cell` + 학생정보 행)·CSS를 `docs/exam-generation/templates/shingu3/round4/신구중3_4회_40문항_문제지_TEMPLATE_FINAL.html`(GitHub canonical)에서 그대로 이식. 로고 신규 생성 없음. `render.mjs`에 LOGO_URI 로드 + 헤더 마크업/CSS 교체, 텍스트만 Round8 정보(8회 70문항 / 신구중학교 2학년 · 영어 · 70문항 (5·6과)).
- 본문 불변 검증: 이식 후 qblock 70개 전문이 기존 DRAFT와 완전 일치(발문·선택지·공통지문·순서), 2~10페이지 run-header 유지, 정답해설 헤더는 범위 밖이라 미변경.
- 재실측·재생성: `render.mjs draft` → `measure.mjs paper`(헤드리스 Chrome) → headerH 121.25px·단위합 15,400.82px로 패킹 입력 불변 → `render.mjs final` cols 20 / pages 10.
- 인쇄 재QA: 두 문서 `--print-to-pdf` 10p씩, `qa_pdf.py` 전 20페이지 RESULT: PASS (breach/clip/colOver/gutter/markerFrag/brokenWord 0, 번호 1~70 연속·무결). 문제지 1쪽 PDF에서 로고 이미지 실재 확인(rect ≈ 33.1×17.7mm, 헤더 좌상단), 1·10쪽·해설 1쪽 래스터 육안 확인.
- 산출물: `신구중2_8회_70문항_문제지_DRAFT.html`(헤더 반영), `신구중2_8회_70문항_정답해설_DRAFT.html`(무변경), 신규 `신구중2_8회_70문항_출제검수.md`(BLUEPRINT·HTML·QA·Wiki 자료원 실대조 검수 기록).
- 한계: 학원 PC 내장 브라우저도 조작 타임아웃(unusable) → 브라우저 육안 확인은 PDF 래스터로 대체. 신구중3 4회 Wiki 등록본 바이트 직접 열람은 ADMIN 세션 필요 → GitHub canonical TEMPLATE_FINAL을 원전으로 사용(출제검수 §8-6 기록).
