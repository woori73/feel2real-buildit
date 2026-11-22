# TESTPREP TOEIC 연습 설계

> 작성 2026-11-22 · 경로 `/testprep/toeic`(목록) + 기존 `/testprep/run`(시험) + 기존 `/testprep/review`(복습) · 기존 TESTPREP 테이블·API 재사용
>
> **ETS 공식 문항·지문과 시험 후기 속 복원 문항은 쓰지 않는다.** 파트 구성·문항 형식·출제 포인트·상황만 참고하고 문장은 F2R이 새로 쓴다.
> 점수는 F2R 내부 추정치이며 ETS 공식 TOEIC 점수가 아니다.

## 1. 목표

- 학생이 Feel2Real 안에서 TOEIC 기출 유사 세트를 실제 번호 체계(7~200번)·실제 길이로 시간을 재고 푼다.
- 결과는 영역별 5~495 추정 점수와 정답 수, 문항별 한국어 해설(정답 근거 + 오답 이유)로 확인한다.
- 오답은 기존 "내 학습"의 오답 다시 풀기로 이어진다.

## 2. 재사용한 것과 새로 만든 것

| 구분 | 내용 |
|---|---|
| 그대로 재사용 | `tp_exams`·`tp_questions`·`tp_attempts`·`tp_attempt_answers`, `/api/testprep/**` 전부, 시험 화면 `/testprep/run`(`run.html` + `testprep_run.js`), 복습 `/testprep/review`, 내 점수·내 학습, 목록 스크립트 `testprep_catalog.js`, 듣기의 브라우저 음성(TTS) |
| 새로 만든 것 | 목록 화면 `/testprep/toeic`(`templates/testprep/toeic.html`, SAT 목록 화면을 본뜸), 상단 메뉴·홈의 TOEIC 진입 |
| 분기만 더한 것 | 채점(`TestPrepService`: 종류 `TOEIC`, 추정 점수, 안내 문구), 화면 도우미(`testprep.js`: Part 탭·문항 머리말·점수 표기), 시험·복습·내 점수 화면의 TOEIC 표시 |

새 표·새 API·새 시험 화면은 없다. 시험 화면은 SAT 전용 화면(`sat_run`, 모듈 시간·계산기·형광펜)보다 iBT 화면이 TOEIC에 가깝다
(세트 전체 타이머, 문항 하나씩, 듣기 TTS 버튼이 이미 있음). 그래서 iBT 화면에 아래 네 가지만 더했다.

1. 위쪽 탭을 영역(section) 대신 **Part별**로 묶는다(`TP.groupOf`). 100~194문항을 파트 단위로 오갈 수 있다.
2. 문항 머리말에 **실제 문항 번호**를 쓴다: `Part 6 · Text Completion · No. 131`(`TP.questionLabel`, `order_no` 사용).
3. Part 6·7은 실제 시험지처럼 **지문을 먼저, 질문을 아래**에 둔다.
4. 제출 뒤 밴드 대신 **추정 점수·정답 수** 표를 보여 준다(`TP.toeicScorecardHtml`, 영역 순서는 듣기 → 읽기).

듣기 음성에서 바꾼 것(`testprep.js`의 `TP.stopSpeech`·`TP.speechText`, 부르는 곳은 `testprep_run.js`):

- **문항 이동·탭 이동·제출 때 읽던 음성을 멈춘다.** 이 화면을 함께 쓰는 iBT·어휘 퀴즈의 듣기 문항에도 똑같이 적용된다(기존에는 다음 문항으로 가도 앞 음성이 끝까지 나왔다). 기존 화면의 동작이 달라지는 곳은 이것 하나다.
- **Part 2는 읽는 글만** 응답 줄 머리 `A.`를 `Response A.`로 바꾼다(`A.`가 관사 a처럼 들리는 것을 막는다). `body_text`와 SQL 생성기는 그대로다.

내 학습의 오답 목록은 TOEIC 문항에만 실제 번호를 붙인다(`No. 131 · …`, `TP.wrongItemLabel`, 응답의 `wrongItems[].orderNo`).

## 3. 경로와 권한

| 경로 | 역할 |
|---|---|
| `GET /testprep/toeic` | TOEIC 소개·세트 목록·시작 버튼. 기존 `/testprep/**` 규칙대로 로그인 필요(비회원은 `/testprep?login=required&returnUrl=…`) |
| `GET /testprep/run?attempt={id}` | 기존 시험 화면. 응시의 `kind`가 `TOEIC`이면 위 2장의 표시로 바뀐다 |
| `GET /testprep/review?attempt={id}` | 기존 복습 화면. TOEIC이면 추정 점수, 실제 문항 번호, 지문·대본, 한국어 해설 |
| `/api/testprep/**` | 그대로(목록 `kind=TOEIC`, 응시 시작·답안 저장·제출·복습) |

사용자가 만드는 객체는 응시 기록뿐이고 기존 정책(본인·관리자만 조회, 수정·삭제 기능 없음) 그대로다.

## 4. 데이터

스키마 변경 없음. 세트별 마이그레이션 한 파일. 번호는 V1131~V1133이다 — V1129·V1130은 PR #1170의 SAT 연습 세트 01·iBT 세트 09가 쓴다(그 뒤 번호).

| 파일 | 시험 코드 | 구성 | 서버 마감 |
|---|---|---|---|
| `V1131__testprep_toeic_full01.sql` | `TP-TOEIC-FULL-01` | 기출 유사 1회분: 듣기 Part 2 25 + Part 3 39 + Part 4 30 = 94, 읽기 Part 5 30 + Part 6 16 + Part 7 54 = 100 → 194문항 | 7200초 |
| `V1132__testprep_toeic_rc01.sql` | `TP-TOEIC-RC-01` | 독해 추가 세트 1: Part 5 30 + Part 6 16 + Part 7 54 = 100문항 | 4500초 |
| `V1133__testprep_toeic_rc02.sql` | `TP-TOEIC-RC-02` | 독해 추가 세트 2: 같은 구성 100문항 | 4500초 |

| 열 | TOEIC 값 |
|---|---|
| `tp_exams.kind` | `TOEIC` |
| `tp_questions.section` | `LISTENING`, `READING` (점수 영역) |
| `tp_questions.task_type` | `TOEIC_P2` ~ `TOEIC_P7` (Part) |
| `tp_questions.order_no` | 실제 TOEIC 문항 번호(Part 2 = 7~31, Part 3 = 32~70, Part 4 = 71~100, Part 5 = 101~130, Part 6 = 131~146, Part 7 = 147~200) |
| `prompt` | Part 5 = 빈칸 문장, Part 6 = `Choose the best answer for blank (131).`, Part 3·4·7 = 질문, Part 2 = 공통 지시문 |
| `body_text` | Part 6·7 = `Questions 131-134 refer to …` + 지문(이중·삼중 지문은 `[E-mail]` 같은 머리말과 구분선으로 이어 붙임). Part 3·4 = 대본, Part 2 = 질문 + 응답 3개(화면에는 숨기고 읽어 준다). 같은 지문을 묶음의 문항마다 되풀이해 넣는다 |
| `audio_mode` | 듣기 `TTS`, 읽기 `NONE` |
| `choices_json` | 4개(Part 2는 빈 문자열 3개 — 화면에 A·B·C 글자만 나온다) |
| `explanation` | `[유형] 정답 (B). 근거 … 오답: (A) … (C) … (D) …` — 한국어. 끝의 `포인트: …`는 줄바꿈 뒤 한 줄이고, 복습 화면은 해설의 줄바꿈을 줄로 보여 준다(`white-space: pre-line`, iBT·SAT 해설에도 같이 적용) |
| `ko_help_json`, `points` | `NULL` |

### 듣기를 넣은 방식과 한계

- 기존 iBT 듣기와 같은 방식이다: `audio_mode='TTS'`이면 시험 화면이 대본을 숨기고 **Play audio** 버튼으로 브라우저 음성(Web Speech)이 읽는다. 음원 파일·새 재생 기능은 만들지 않았다.
- **Part 1(사진 묘사 6문항)과 도표를 보고 푸는 문항은 없다.** 그래서 1회분 듣기는 94문항이다.
- 실제 시험과 다른 점: 화자가 같은 목소리에 높이만 다름(아래), 속도·발음이 브라우저마다 다름, 다시 듣기 가능, Part 3·4는 같은 대본을 문항마다 다시 재생. 듣기 점수는 참고용이다. 목록 화면(`/testprep/toeic`)에도 "표·그래프 문항과 Part 1(사진) 없음, 한 목소리 음성이라 듣기 점수는 참고용"이라고 적었다.

**화자 차례로 나눠 읽기(2026-11-22 변경, `testprep.js`).** 이 화면을 함께 쓰는 iBT 듣기에도 똑같이 적용된다. `body_text`·SQL은 바꾸지 않고 읽는 방식만 바꿨다.

- `TP.speechTurns(text)` → `[{speaker, text}]`: 대본을 " — "(줄표) 또는 줄바꿈에서 조각으로 나누고, 조각 맨 앞의 꼬리표(`Woman:`, `Man 1:`, `Lab worker:` — 대문자로 시작하는 영문자·숫자 1~3낱말 + `:` + 공백)를 화자로 본다. 꼬리표는 읽는 글에서 뺀다.
  - 꼬리표가 없는 조각은 앞 차례에 이어 붙인다. 그래서 문장 속 줄표, 시각 `9:30`, 문장 가운데의 콜론, Part 2의 응답 줄은 차례를 나누지 않는다. 화자가 없는 글(Part 4 담화, 강의, Part 2)은 한 차례다. Part 3 머리의 `Questions 32 through 34 refer to …` 줄은 화자 없는 차례다.
  - 낱말이 모두 대문자로 시작하지 않는 꼬리표(`Lab worker:`)는 꼬리표가 둘 이상인 대화에서만 화자로 본다(안내 방송의 `Attention cast members: …`를 화자로 보아 읽지 않는 일을 막는다).
- `TP.speechChunks(text)`: 200자를 넘는 차례는 문장 단위로 나눠 200자 이하 덩어리로 묶는다(긴 발화가 중간에 끊기는 문제 예방). `Ms.`·`Dr.` 같은 줄임말 뒤에서는 끊지 않는다. 한 문장이 200자를 넘으면 그 문장은 그대로 한 덩어리다.
- `TP.speechQueue(q)` → `[{text, pitch}]`: 차례·덩어리마다 발화 하나. 화자 순서대로 높이(pitch) 1.0 · 0.8 · 1.2(넷째 화자부터 되풀이), 화자 없는 글은 1.0. 시험 화면(`testprep_run.js`의 `speak`)이 이 목록대로 `SpeechSynthesisUtterance`를 따로 만들어 순서대로 큐에 넣는다.
- 멈춤은 그대로 `TP.stopSpeech()`(`speechSynthesis.cancel()` — 큐에 남은 발화까지 모두 지운다).
- 한계: 목소리(voice)는 화자마다 바꾸지 않는다(브라우저 기본 영어 음성 하나에 높이만 다르게). 높이 차이가 얼마나 들리는지, 발화 사이 쉼 길이는 브라우저·음성마다 다르다. 화자가 한 명뿐인 글이 `Note: …`처럼 대문자 낱말 + 콜론으로 시작하면 그 낱말을 화자로 보아 읽지 않는다(지금 세트에는 없다).
- 확인: 지금 올라 있는 듣기 544문항(iBT·TOEIC)에 돌려 보면 화자로 잡히는 것은 실제 꼬리표뿐이고(`Woman`·`Man`·`Man 1`·`Man 2`·`Student`·`Professor`·`Librarian`·`Clerk`·`Lab worker` 등 19종), 읽는 글에 꼬리표가 남거나 낱말이 빠지는 문항은 없다. **실제 소리(높이 차이, 끊김)는 브라우저에서 들어 확인하지 못했다.**

## 5. 채점

- 객관식 글자 비교(기존).
- 영역 추정 점수: `5 + round(정답 비율 × 98) × 5` → 5~495(5점 단위). `TestPrepService.toeicScaledFor`.
  ETS 환산표를 쓰지 않고 정답 비율을 점수 범위에 그대로 편 값이라 실제 환산 점수와 다르다(실제 환산은 회차 난이도에 따라 달라진다).
- 합계 = 영역 점수 합. 1회분 만점 990, 독해 세트 만점 495(`totalMax`).
- **오답만 다시 풀기(부분 응시, `tp_attempts.question_ids_json`이 있는 응시)는 추정 점수를 내지 않는다.** 영역의 `scaled`·`scaleMax`와 합계가 모두 `null`이고 정답 수·정답률만 남는다(틀린 3문항을 다시 맞혀 495가 나오는 것을 막는다). `TestPrepService.toeicSectionScaled`. 결과 화면은 점수 자리에 정답 수(`3 / 3`)를 보여 준다. SAT의 부분 응시도 같은 방식으로 점수를 내지 않는다(2026-11-22, [testprep-digital-sat.md](testprep-digital-sat.md) 6장).
- 점수표의 영역 순서는 듣기 → 읽기(`TestPrepService.sectionRank(kind, section)`). iBT·SAT·내신의 순서는 그대로다.
- TOEFL 밴드 열(`overall_band`)에는 넣지 않는다. 영역 점수는 `section_scores_json`의 `scaled`·`scaleMax`.
- 안내 문구: `F2R internal score estimate — not an official ETS TOEIC score.`

## 6. 문항 제작

- 원본: 저장소 밖 `naesin-workspace/testprep/toeic/sets/<세트>/pN.json`. 규격 `AUTHORING_SPEC.md`, 검사 `tools/validate.py`,
  SQL 생성 `tools/build_sql.py`, SQL 검사 `tools/check_sql.py`, 정답 배정 `keys.json`(정답 글자를 미리 고르게 배정).
- 기준 자료: ETS 공식 샘플(파트 구성·형식만), 감독이 준 기출 후기 정리(최근 3회 문법 출제 쟁점 T01~T12, 11-13·11-21 교사용 어법 A·상황 어휘 B·Part 6·7 C 단원, 어법·어휘 출제조사·복습·오답복습, Part 7 상황 어휘 최신 경향).
- 후기 반영 근거: 문항별(Part 5·6)·지문 묶음별(Part 7) 표를 `naesin-workspace/testprep/toeic/후기_반영_근거표.md`에 둔다. Part 5·6 138문항 중 137문항이 후기 포인트를 반영한다.
- 세트 개정 v2(검수 3곳 반영): 후기 확인문항과 틀이 닮은 문항·세 세트 사이 되풀이(문항, 정답 낱말, 지문 소재, 고유명사)를 새로 썼고, 정답 글자 3연속·정답 길이 단서·듣기 대본 길이(100~110낱말)를 고쳤다. 모든 문항 해설(`explanation`) 끝에 `포인트: …` 한 줄(Part 5·6은 문법·어휘 포인트 이름, Part 7은 유형과 근거 문서, 듣기는 유형)이 들어간다. 후기 자료의 내부 코드는 해설에 쓰지 않고 근거표에만 둔다. 수정 기록·기계 점검 결과: `naesin-workspace/testprep/toeic/수정기록_toeic_v2.md`, 점검 스크립트 `fix/check_all.py`.
- 독립 검증: 정답·해설을 뺀 문제지를 다른 세션 4개가 다시 풀었다(394문항, 불일치 0, 복수 정답 의심 0).
- 정답 분포: 읽기 세트마다 A 26 · B 26 · C 24 · D 24, 듣기 A 27 · B 26 · C 25 · D 16(Part 2는 선택지 3개).

## 7. 검증

- 단위(Java): `TestPrepScoringTest` — TOEIC 추정 점수 범위, 합계·만점, 안내 문구, 부분 응시는 점수 없음, 영역 순서(듣기 → 읽기).
- 단위(JS): `src/test/js/testprep_toeic.test.js` — Part 탭 묶음, 문항 머리말, 점수 표기, 응시 화면 주소, 결과 화면(전체·부분 응시), Part 2 읽는 글, 오답 목록 번호, 응시 화면을 가짜 화면에서 실행해 문항 이동·제출 때 음성이 멈추는지, Play audio가 꼬리표 없이 차례별 발화를 넣는지. `src/test/js/testprep_speech.test.js` — 화자 차례 나누기(화자 2명·3명, `Man 1:`, 화자 없음, 시각 `9:30`, 줄표·줄바꿈 구분), 문장 덩어리, 발화 목록(높이). `src/test/js/testprep_partial_score.test.js` — 복습 화면 해설 줄바꿈(TOEIC `포인트:` 줄). CI(`build-check.yml`)의 JS 테스트 목록에 들어 있다.
- 통합(MySQL): `TestPrepFlowMySqlIntegrationTest`(세트 3개 정답 제출 → 495/990, 빈 제출 → 5, 오답만 다시 풀기 → 점수 없음), `TestPrepPagesMySqlTest`(`/testprep/toeic` 권한·렌더).
- SQL: `tools/check_sql.py`(행 수, JSON 열, 작은따옴표 이스케이프, exam_code·버전 중복, 원본과 값 일치).

## 8. 이후 단계(이번 범위 아님)

- 녹음 음원(화자 2~3명)과 Part 1 사진 문항, 도표 문항.
- 문항 번호판(한 번에 건너뛰기)·좌우 2단 지문 화면.
- 회차별 환산표에 가까운 점수 추정.
- Part 7 지문을 묶음에 한 번만 그리기: 지금은 같은 지문을 문항마다 통째로 다시 그린다(복습 화면에서는 Part 7 54문항에 지문이 54번 나온다).
- Part 3·4 대본을 묶음에 한 번만 재생하기: 지금은 같은 대본을 문항마다 다시 재생한다(실제 시험은 대본 한 번에 3문항).
- 화자별로 다른 목소리(voice) 고르기, 브라우저별 소리 확인(높이 차이·끊김).
