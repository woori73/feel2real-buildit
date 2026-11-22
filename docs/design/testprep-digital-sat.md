# TESTPREP Digital SAT 연습 시험 설계안

> 작성 2026-11-15 · 대상 경로 `/testprep/sat`, `/testprep/sat/run` · 기존 TESTPREP(TOEFL) 테이블·API 재사용
>
> 참고: 응시자 PC에서 실행 중인 College Board Bluebook(테스트 미리보기)의 화면 구성과 도구를 직접 조작해 확인했다.
> 사용자 PC의 Practice Test Player(Bluebook을 본떠 Rust/egui로 만든 앱, `Craft Anything/bluebook-setup-0-9-730/app`)의 작동 규칙(MECHANICS.md)도 참고했다.
> **Bluebook의 문항·이미지·로고·문구는 복제하지 않는다.** 화면 배치와 도구의 동작 방식만 참고하고, 문항은 F2R이 직접 작성한다.
> 점수는 F2R 내부 추정치이며 College Board 공식 SAT 점수가 아니다.

## 1. 목표

- 학생이 Feel2Real 안에서 실제 디지털 SAT와 같은 화면·도구로 시간 제한 연습 시험을 본다.
- 결과는 영역별 200~800, 합계 400~1600 추정 점수와 문항별 해설로 확인한다.
- 오답은 기존 TESTPREP "내 학습"의 오답 다시 풀기로 이어진다.
- 평소 화면에는 타이머와 문제만 보인다. **해설** 버튼을 누르면 모듈 시간이 멈추고 ① 한글 해석 ② 영어 어휘 ③ 풀이 설명 ④ 풀이 전략이 나온다(§7-1).

## 2. Bluebook 분석 요약 (참고한 화면 요소)

| 구역 | 요소 |
|---|---|
| 위쪽 바 | 섹션·모듈 이름, Directions(펼침 안내), 가운데 남은 시간(Hide/Show), 오른쪽 도구 |
| 도구(읽기·쓰기) | Highlights & Notes(형광펜 3색·밑줄·메모), More(Help·Shortcuts·Line Reader·Exit) |
| 도구(수학) | Calculator(그래프·공학용, 왼쪽 도킹), Reference(공식표, 오른쪽 도킹) |
| 본문 | 읽기·쓰기: 왼쪽 지문/오른쪽 문제 2단, 가운데 막대로 폭 조절 · 수학: 한 단, 주관식은 왼쪽 입력 규칙/오른쪽 입력칸 |
| 문항 머리 | 문항 번호, Mark for Review, 선택지 지우기(ABC) 켜기·끄기 |
| 선택지 | A~D 선택, 오른쪽 지우기 버튼(줄 긋기)과 Undo |
| 아래쪽 바 | 응시자 이름, "Question n of N" 이동판(현재·미응답·검토 표시, 검토 페이지 이동), Back/Next |
| 모듈 끝 | Check Your Work(모듈 전체 문항 상태), Next로 다음 모듈 이동 |
| 단축키 | Ctrl+Alt+V 검토 표시, Ctrl+Shift+1~4 선택, Ctrl+Alt+1~4 지우기, Ctrl+Alt+O 지우기 모드, Ctrl+L 라인 리더, Ctrl+H 형광펜, Ctrl+Alt+C 계산기, Ctrl+Alt+R 공식표, Ctrl+Alt+T 타이머, Ctrl+Alt+B/X 이전·다음, Ctrl+Alt+G 이동판, F1 단축키 안내 |

## 3. 범위

### 1차(이번 PR)
- 연습 세트 1개: 읽기·쓰기 1모듈 10문항(12분) + 수학 1모듈 8문항(13분). 전 문항 F2R 자체 작성, 18문항 모두 해설란 자료 포함.
- 진행: 모듈 안내 화면(Begin을 눌러야 시간 시작) → 문항 → Check Your Work → 섹션 사이 쉬는 시간(최대 10분, 일찍 재개 가능) → 다음 모듈 안내 → … → 제출.
- 남은 5분부터는 타이머를 숨길 수 없다. 시험 중에는 화면이 꺼지지 않게 한다(Screen Wake Lock, 지원 브라우저).
- 위 2장의 화면·도구 전부(Help 화면은 단축키 안내로 대체, 음성 읽기·쉬는 시간 없음).
- 모듈별 시간 제한, 시간이 끝나면 다음 모듈로 자동 이동, 앞 모듈로 돌아가기 불가(실제 시험과 같음).
- 답안 자동 저장(기존 API), 이어 풀기, 제출 후 추정 점수·해설, 오답 다시 풀기.

### 추가 세트(2026-11-15, V1114)
- 사용자 PC의 Practice Test Player에 있던 시험 3개(63문항)를 옮겼다. 그중 "Make my own test" 템플릿은 영어 세트와 같은 문항이라 빼고, 서로 다른 42문항을 세트 2개로 넣었다.
  - `TP-SAT-MINI-02` Digital SAT Practice — Mini Set 02: 영어 21문항(읽기·쓰기 10 + 수학 11)
  - `TP-SAT-KO-01` 디지털 SAT 연습 — 한국어 미니 세트: 한국어 21문항. 해설란 첫 칸은 '영어 원문'(`translationTitle`)으로 SAT 영어 표현과 연결한다.
- Player의 2모듈 적응형 구성은 한 모듈로 이어 붙였다. 문항·선택지·정답은 원본 그대로이고, 해설란 자료(해석·어휘·풀이·전략)는 F2R이 새로 썼다.

### 연습 세트 01(2026-11-22, V1129)
- `TP-SAT-PRAC-01` Digital SAT Practice — Set 01: 읽기·쓰기 27문항(32분) + 수학 22문항(35분). 실제 시험의 모듈 1개 길이다. 모듈 시간은 화면이 문항 수로 계산한다(`sat_run.js` `PER_QUESTION_SEC`).
- 공개 SAT 기출 9~11회 모듈 1의 영역·유형 비율(읽기·쓰기 7/7/7/6, 수학 Algebra 8·Advanced Math 7·Problem-Solving and Data Analysis 4·Geometry 3), 지문 길이, 선택지 길이, 난이도 흐름을 재서 맞췄다. 문항·지문·수치는 F2R이 새로 썼고, 문학 지문 2개(5·9번)만 저작권이 끝난 소설의 원문 발췌다.
- 제작 뒤 독립 검수 3회(정답 재풀이 / 기출 대조 / 해설·형식)를 거쳐 고쳤다. 풀이 전략(`strategy`)에는 정답이 드러나지 않게 했다.
- 화면이 지원하지 않아 뺀 것: 그래프·도형 문항, 밑줄 표시("밑줄 친 문장의 역할"은 "마지막 문장의 역할"로), 2모듈·적응형.

### 이후 단계(이번 PR 아님)
- 적응형 2단계 모듈(1모듈 정답률로 2모듈 난이도 선택)과 실제 길이 세트(27+27 / 22+22문항).
- 그래프·도표 이미지 문항, 문항 은행 관리자 화면.
- 공식 Desmos 계산기 연동(상업 이용 API 키 필요 — 별도 결정).

## 4. 경로와 권한

| 경로 | 역할 |
|---|---|
| `GET /testprep/sat` | SAT 소개·세트 목록·시작 버튼. 기존 `/testprep/**` 규칙대로 로그인 필요(비회원은 `/testprep`로 보내 로그인 안내) |
| `GET /testprep/sat/run?attempt={id}` | 시험 화면(전체 화면형, TESTPREP 상단 메뉴 없음) |
| `GET /testprep/review?attempt={id}` | 기존 복습 화면 — SAT이면 추정 점수와 지문 서식으로 표시 |
| `/api/testprep/**` | 기존 API 그대로(시험 목록 `kind=SAT`, 응시 시작·답안 저장·제출·복습) |

새 API·새 테이블은 만들지 않는다.

## 5. 데이터

- `V1112__testprep_question_ko_help.sql`: `tp_questions.ko_help_json` MEDIUMTEXT NULL 추가(해설란 JSON). 기존 행과 무관한 nullable 컬럼이라 1단계.
- `V1113__testprep_sat_mini.sql`: SAT 세트 행 추가(스키마 변경 없음).

| 열 | SAT 값 |
|---|---|
| `tp_exams.kind` | `SAT` |
| `tp_exams.time_limit_sec` | 7200(서버 마감). 모듈별 시간·쉬는 시간·해설 정지는 화면이 관리하고, 서버 마감을 넘겨 제출하면 overtime 표시만 남는다 |
| `tp_questions.ko_help_json` | `{"translation","vocab":[[영어,뜻]],"solution","strategy"}` — 전략에는 정답을 쓰지 않는다 |
| `tp_questions.section` | `RW`(읽기·쓰기), `MATH`(수학) |
| `tp_questions.task_type` | `SAT_RW_MC`, `SAT_MATH_MC`, `SAT_MATH_SPR`(주관식) |
| `tp_questions.body_text` | 지문(읽기·쓰기). 간단한 서식 표기 사용(아래) |
| `tp_questions.answer_key` | 객관식 `A`~`D`, 주관식은 허용 답을 `|`로 나열(예: `21/2|10.5`) |

### 본문 서식 표기(화면이 HTML로 바꿈, 원문은 항상 이스케이프)
`*기울임*`, `**굵게**`, `^{위첨자}`, `_{아래첨자}`, `______`(빈칸), 빈 줄 = 문단, `|`로 시작하는 줄 = 표(첫 줄 머리), `• ` = 목록, `\frac{a}{b}` = 분수, `\sqrt{x}` = 제곱근.

**그림(2026-11-22 추가)**: 한 줄 전체가 `[[img:/testprep/img/<파일>]]`이면 그 자리에 그림을 넣는다(`prompt`·`body_text`, 선택지에는 안 된다). 예: `[[img:/testprep/img/sat_p01_m1_q07.svg]]`.
파일은 `src/main/resources/static/testprep/img/`에 둔다. 경로는 `/testprep/img/`로 시작하고 영문자·숫자·`_`·`-`·`.`·`/`만, `..` 없이, 확장자 `svg`·`webp`·`png`만 허용한다. 그 밖의 경로는 그리지 않고 그 줄을 지운다(`<img src>`로만 넣는다 — `sat_rich.js`의 `SATRich.image`). 줄 가운데에 쓴 `[[img:…]]`는 글자로 보인다.
표(`SATRich.table`)와 그림은 TOEIC·iBT 화면도 함께 쓴다(`testprep.js`의 `TP.richBody`, [testprep-toeic.md](testprep-toeic.md) 4장).

## 6. 채점

- 객관식: 기존과 같이 글자 비교.
- 주관식(`SAT_MATH_SPR`): 입력값과 허용 답을 공백 제거 후 문자열 비교 → 일치하지 않으면 분수·소수를 수로 바꿔 비교(오차 1e-9) → 그래도 다르면 "칸을 다 채운 소수(양수 5자·음수 6자)"가 정답을 그 자리에서 자르거나 반올림한 값인지 본다(2/3 → .6666/.6667/0.666/0.667 정답, 0.67 오답). 입력 규칙은 양수 5자·음수 6자, 숫자·`.`·`/`·`-`만.
- 영역 점수(추정): `200 + round(정답률 × 60) × 10` → 200~800. 합계 = 두 영역 합(400~1600).
  - 문항 수가 적은 미니 세트라 실제 점수와 차이가 크다는 안내를 결과 화면에 함께 표시한다.
- **오답만 다시 풀기(부분 응시, `tp_attempts.question_ids_json`이 있는 응시)는 추정 점수를 내지 않는다**(2026-11-22, TOEIC과 같은 처리). 영역의 `scaled`·`scaleMax`와 합계가 모두 `null`이고 정답 수·정답률만 남는다(틀린 몇 문항을 다시 맞혀 800·1600이 나와 세트 점수처럼 남는 것을 막는다). `TestPrepService.satSectionScaled`.
  - 화면은 점수 자리에 정답 수를 보여 준다: 결과 화면 `3 / 3 correct`(`testprep.js`의 `TP.satScoreHtml`, `sat_run.js`가 부른다), 복습 화면 합계 칸 `3 / 3 correct`, 내 점수 표 `3 / 3 correct`(이력 응답의 `correctCount`·`autoCount`).
  - 이 변경 전에 제출한 부분 응시는 저장된 점수(`section_scores_json`)가 그대로 남아 예전처럼 점수로 보인다(기록을 고치는 마이그레이션은 하지 않았다).
- TOEFL용 밴드 열(`overall_band`, DECIMAL(2,1))에는 SAT 점수를 넣지 않는다. 영역 점수는 `section_scores_json`의 `scaled` 값으로 저장하고 합계는 읽을 때 계산한다.
- 안내 문구: "F2R internal score estimate — not an official College Board SAT score."

## 7. 시험 화면 구성

```
┌───────────────────────────────────────────────────────────────┐
│ Section 1, Module 1: Reading and Writing      12:00     도구  │
│ Directions ▾                                  [Hide]          │
├───────────────────────────────────────────────────────────────┤
│                   F2R PRACTICE TEST                           │
│  지문(형광펜·메모)            ┃  1 □ Mark for Review    [ABC]  │
│                               ┃  질문                          │
│                               ┃  (A) …                  ⊘     │
│                               ┃  (B) …                  ⊘     │
├───────────────────────────────────────────────────────────────┤
│ 응시자 이름            [Question 1 of 10 ▴]       Back  Next  │
└───────────────────────────────────────────────────────────────┘
```

- 상태(검토 표시, 지운 선택지, 형광펜·메모, 현재 모듈과 모듈 시작 시각)는 브라우저 `localStorage`에 응시별로 저장한다. 답안만 서버에 저장한다.
- 모듈 순서: RW → MATH. 모듈 끝 "Check Your Work"에서 Next → 다음 모듈(되돌아가기 불가) → 마지막 모듈 Next = 제출.
- 남은 5분에 알림, 시간 0이면 자동 이동/제출. 서버 마감 시각을 넘으면 서버가 overtime으로 기록(기존 동작).
- Exit: 저장 후 SAT 목록으로 나감. 같은 응시는 이어 풀 수 있다(모듈 시간은 계속 흐름).

### 7-1. 해설란(시간 멈춤)
- 문항마다 문제 바로 위의 주황색 **한글 해설 · 풀이 전략 보기** 버튼(Ctrl+Alt+E). 오른쪽에 패널이 열리고 탭(해석·어휘·풀이·전략)으로 이동한다. 공식표와는 번갈아 열린다.
- 위쪽 도구 줄에는 실제로 동작하는 도구만 둔다(계산기·공식표·More). 형광펜·메모는 글자를 드래그하면 바로 도구가 나오므로 따로 버튼을 두지 않는다(2026-11-15 수정).
- 아래쪽 바 왼쪽의 응시자 이름 칸은 지우고 **Home** 버튼을 둔다. 누르면 답안을 저장하고 TestPrep 홈(`/testprep`)으로 간다. 같은 응시는 SAT 목록·내 점수에서 이어 풀 수 있다.
- 여는 순간 모듈 시간이 멈추고 타이머 옆에 "해설 보는 중 · 시간 멈춤"이 뜬다. 닫으면 이어서 흐른다. 해설을 연 채 나갔다 돌아와도 그 사이 시간은 멈춘 것으로 친다.
- 해설을 본 문항은 기록해 결과 화면에 "해설을 본 문항 n개"로 알린다(점수는 그대로 계산).
- 문항을 옮기면 패널 내용도 그 문항으로 바뀐다. 검토 화면·쉬는 시간으로 가면 자동으로 닫힌다.

### 글꼴
- 지문·선택지: Noto Serif(18px/17px), 화면 글자: Roboto, 한글: Noto Sans KR(Google Fonts). Practice Test Player보다 글자가 또렷하게 보이도록 크기·굵기를 올렸다.

### 계산기
- Desmos는 운영 사용에 API 키 계약이 필요해 1차는 F2R 자체 계산기로 대체한다.
- 공학용: `+ − × ÷ ^ ( )`, `sin cos tan asin acos atan sqrt abs ln log exp`, `π e`, 직전 답 `ans`, 도/라디안 전환, 암묵적 곱(`2x`, `3(…)`).
- 그래프: 식 4개까지 `y = …`(x에 대한 식), 확대·축소·원점 복귀.
- 식 해석은 직접 만든 파서로 처리하고 `eval`은 쓰지 않는다.

### 공식표
원·직사각형·삼각형·피타고라스·특수직각삼각형·직육면체·원기둥·구·원뿔·각뿔 공식과 "원의 호 360°, 2π 라디안, 삼각형 내각 합 180°" 안내 — F2R이 SVG로 새로 그린다.

## 8. 파일

| 파일 | 내용 |
|---|---|
| `domain/testprep/controller/TestPrepPageController.java` | `/testprep/sat`, `/testprep/sat/run` 추가 |
| `domain/testprep/service/TestPrepService.java` | SAT 채점(주관식 동치·추정 점수), 종류별 안내 문구, 영역 순서 |
| `domain/testprep/dto/TestPrepDtos.java` | `SectionScore.scaled`, `Scorecard/ReviewView/AttemptSummary.totalScaled`, `RunQuestion/ReviewItem.koHelpJson` 추가 |
| `db/migration/V1112__testprep_question_ko_help.sql` | `tp_questions.ko_help_json` 추가 |
| `db/migration/V1113__testprep_sat_mini.sql` | SAT 미니 세트 1개(해설란 자료 포함) |
| `domain/testprep/entity/TpQuestion.java` | `koHelpJson` |
| `templates/testprep/sat.html`, `sat_run.html` | 소개·목록, 시험 화면 |
| `static/js/sat_rich.js` | 본문 서식 표기 → 안전한 HTML |
| `static/js/sat_calc.js` | 계산기 파서·그래프 |
| `static/js/sat_run.js`, `static/css/sat_run.css` | 시험 화면 |
| `static/js/testprep_review.js`, `testprep_scores.js`, `testprep_learning.js` | SAT 점수 표시·이어 풀기/다시 풀기 경로 |

## 9. 검증

- 단위: 주관식 동치 채점, 추정 점수 환산, 서식 표기 이스케이프, 계산기 파서. 부분 응시는 점수 없음(`TestPrepScoringTest`), 부분 응시의 결과·복습·내 점수 표시(`src/test/js/testprep_partial_score.test.js`).
- 통합(MySQL): SAT 세트 응시 → 답안 저장 → 제출 → 점수·복습. 오답만 다시 풀기 → 점수 없음·정답 수(`satRetry_noEstimate`).
- 화면: 로컬 부팅 후 두 모듈 끝까지 응시, 도구별 동작 확인.

## 10. 운영 반영

- `main` 병합 = 운영 자동 배포이므로 병합은 사용자 승인 후 진행한다.
- 배포 전 운영 `flyway_schema_history` 최대 버전이 1111인지 확인한다(아니면 V1112·V1113 번호를 올린다).
