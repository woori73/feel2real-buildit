# 학교별 내신 Wiki 반복 제작 허브 1차 설계

## 조사 결과

- 화면: `problem-generator/wiki-src/src/components/NaesinWikiV1.tsx`가 학교·학년·연도·학기·시험 Context를 선택하고 자료, Digital Twin, 문제 DB, FINAL/직보 Workspace를 연결한다.
- 학교 계층: `fetchSchoolTree()`의 SchoolNode → grade → exam 구조를 사용한다. Digital Twin은 동일 시험을 우선하고 없으면 `전체` Twin을 사용한다.
- 자료 source of truth: FastAPI/SQLite의 `exam_material_sets`. `SCHOOL_PRINT`, `PAST_EXAM`, `AI_WORK_LOG`, `STUDENT_WRONG_NOTE`가 원본 attachment와 HTML/JSON/본문 metadata를 같은 행에 연결한다.
- 직접 View: 학교 프린트 HTML, 기출·AI 작업기록의 HTML/Markdown 본문, Digital Twin JSON, FINAL Workspace의 Context JSON은 다운로드 없이 볼 수 있다. HWP/HWPX/PDF/이미지만 저장된 legacy 원본은 브라우저 직접 본문 View가 없으므로 다운로드/OCR이 필요하다.
- AI Context: `GET /api/workspaces/{final|jikbo}/context`가 사람 화면과 동일한 DB 자료를 조립한다. MCP adapter도 이 함수를 호출하므로 Human View와 AI Context View가 같은 source of truth를 사용한다.
- WRONG: 학교별 Wiki에는 학생 연결형 `student_wrong_notes` CRUD와 `exam_material_sets/STUDENT_WRONG_NOTE` bulk 자료 흐름이 이미 있다. 별도 공개 모의고사 `/wiki`의 CSAT WRONG assessment는 Question Library/모의고사 문항에 결합돼 있어 학교별 Wiki로 복사하면 coupling이 커진다.
- 상태: Question Wiki AI summary에는 `DRAFT/APPROVED`, Academy에는 `DRAFT/PUBLISHED`가 있으나 주간 내신 시험 산출물 전용 REVIEW/승인 계약은 없다.
- Agent 등록: MCP에는 FINAL/직보 Context 조회 및 final deliverable 생성 adapter가 있으나, 문제지 HTML + 정답해설 HTML + 검수 MD + context version을 하나의 Weekly Exam REVIEW 세트로 등록하는 contract는 아직 없다.

## 이번 PR

- 기존 학교/시험 선택 아래에 `주간 실전 모의고사`와 `오답 복습` 진입 카드를 추가한다.
- 압구정중2·신구중2는 40/70, 신구중3·신사중3은 40A/40B 운영정책을 UI policy로 표현한다.
- 압구정중2는 학습자료가 진행 중임을 `Context PARTIAL`로 표시한다.
- 기존 FINAL Workspace의 `AI Context 보기`와 학생 오답 저장 화면으로 연결한다.
- AI 제작 완료와 공개 사이의 `REVIEW → APPROVED → FINAL/PUBLISHED` 승인 Gate를 UI에 명시한다.
- DB migration과 신규 외부 API는 만들지 않는다.

## 2차 PR 제안

1. `weekly_exam_sets`와 artifact 관계를 설계한다. school/grade/year/semester/exam/round/setType/questionCount/status/contextVersion/producedAt를 보존한다.
2. Living Context에 monotonic version과 source fingerprint를 부여하고 Weekly Exam이 제작 당시 version을 참조하게 한다.
3. 인증된 관리자/TUTOR 내부 contract로 세 산출물을 원자적으로 `REVIEW` 등록한다. Agent 우회 endpoint나 자동 공개 권한은 만들지 않는다.
4. 승인/수정요청/반려 audit와 `APPROVED → FINAL → PUBLISHED` 전이 권한을 구현한다.
5. 학생 오답은 School Context와 분리된 구조화 Student Error Context로 확장하고, 공개 모의고사 CSAT WRONG와는 adapter 경계만 공유한다.

## 위험과 경계

- 이번 UI의 세트 정책은 현재 운영 대상 4개 학교·학년만 표현하는 임시 presentation policy다. 영구 metadata가 생기면 API 응답으로 교체한다.
- `exam_material_sets`에는 Weekly 산출물 상태·회차·Context version이 없으므로 기존 행을 억지로 재해석하지 않는다.
- MCP는 Bearer 인증, 일반 화면/API는 로그인과 workspace 격리를 유지한다. Agent가 관리자 승인 없이 PUBLISHED로 전이할 수 있는 endpoint를 만들지 않는다.
