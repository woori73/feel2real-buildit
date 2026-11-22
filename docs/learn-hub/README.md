# 학생이 문제 푸는 페이지 (`/learn`)

> 2026-11-22 대표 지시: 큰 틀은 두 페이지 — ① **학생이 문제 푸는 페이지**(이 문서) ② Claude와 함께 문제 제작 페이지(`feat/f2r-question-factory`).
> 기준 문서: `feat/f2r-lesson-prep-worksheets`의 `docs/lesson-prep/SITE_PLAN.md`.
> 처음 올린 `/learn`은 기존 화면 링크 모음이었다. 이것을 **과목·시험 탭을 고르면 곧 푸는 화면**으로 다시 구성했다.

## 1. 원칙

- **풀이 엔진을 새로 만들지 않는다.** 응시·채점·해설은 기존 `tp_exams`/`tp_questions`/`tp_attempts`와 `/api/testprep/**`, 기존 풀이 화면을 그대로 쓴다. `/learn`은 시험을 고르고 응시를 만들어 풀이 화면으로 보내는 일만 한다.
- **문항을 지어내지 않는다.** 게시된 자료가 없는 과목·파트는 「준비 중」으로 보인다.
- **분량 숫자는 글로 적지 않는다.** 화면의 회차·문항 수는 요청 때마다 게시된 자료에서 센다(`LearnHubCatalog`). 이 문서에 적힌 수는 2026-11-22 로컬 DB(마이그레이션 V1133까지)에서 센 값이다.
- 스키마 변경·마이그레이션 없음.

## 2. 화면

- 위: 흰 메뉴(문제 풀기 · 내 점수 · 홈), 로그인 이름 또는 로그인 단추.
- 탭: **내신 · 수능 · iBT · SAT · TOEIC · 국어 · 수학 · 사회 · 과학**. 주소는 `/learn?tab=naesin|csat|ibt|sat|toeic|korean|math|social|science`(모르는 값은 내신). 탭은 링크라서 스크립트 없이도 열린다. 게시된 시험이 없는 탭에는 「준비 중」 표시가 붙는다.
- 탭 안: 묶음마다 표 한 장(제목·범위 / 분량 / 단추). 휴대폰 폭에서는 한 줄씩 쌓인다.
- **「풀기」**: 로그인 상태면 `POST /api/testprep/attempts`로 응시를 만들고 곧바로 풀이 화면으로 간다. 비회원이면 로그인 안내 창이 뜨고, 네이버 로그인 뒤 보던 탭(`/learn?tab=…`)으로 돌아온다.
- 로그인 규칙은 바꾸지 않았다. 탭 머리의 노란 표시가 그 탭의 기존 규칙이다.

| 탭 | 무엇이 풀리나 | 풀이 화면 | 로그인(기존 규칙) | 2026-11-22 분량 |
|---|---|---|---|---|
| 내신 | 학교별 영어 예상문제. 학년·학교 거르기는 이 탭 안에만 있다 | `/naesintest/run` (디지털 모의고사 그대로) | 목록 누구나 · 풀이 로그인. 1회(`-R01`)는 회원 누구나, 2회부터 재원생·관리자 | 3개 학교·학년 34회차(압구정중2 16 · 신구중2 11 · 압구정중1 7), 회차마다 40문항(종합 3회차는 70문항) |
| 수능 | 예비고1·고2 디지털 영어 모의고사 1~7부(회차를 고르면 그 회차 풀이 화면), 2027 수능 영어 해설, 모의고사 위키 | `/naesintest/pre-high/…` | 목록 누구나 · 풀이 로그인. 해설은 누구나 | 2부 22회 · 3부 10단원(단원마다 2부)+마무리 10회 · 4부 50회 · 6부 1회 · 7부 40회. 1부·5부(기출)는 서버 바깥 폴더 자료라 로컬에서는 줄이 나오지 않는다 |
| iBT | iBT 모의고사, 어휘 퀴즈 | `/testprep/run` | 로그인 | 모의고사 9세트 · 어휘 퀴즈 3세트 |
| SAT | 디지털 SAT 세트 | `/testprep/sat/run` | 로그인 | 4세트 |
| TOEIC | 아래 3장 | `/testprep/run` | 로그인 | 3세트 394문항 |
| 국어·수학·사회·과학 | 디지털 모의고사(내신과 같은 틀). 지금은 빈 목록 | `/naesintest/run` | 내신과 같음 | 0 — 「준비 중 — 제작 공장에서 게시하면 여기에 올라옵니다.」 |

- 내신 탭: 로그인 회원에게는 풀던 회차가 「이어 풀기」로, 일반 회원에게는 2회부터 「재원생 전용」으로 보인다(`/api/naesintest/access`, 서버도 403으로 막는다).
- iBT·SAT는 풀이 화면·채점·문항 계약을 건드리지 않았다. 고친 것은 목록에서 바로 시작하는 진입뿐이다. 기존 목록 화면(`/testprep/practice`, `/testprep/sat`, `/testprep/toeic`)도 그대로 있다.

## 3. TOEIC — 잘 틀리는 부분을 섹션별로

대표 지시: iBT와 비슷하게 풀되 잘 틀리는 부분을 섹션별로, 지금은 **Part 5·6 어휘·어법 강조**.
저장소에서 찾은 기준 자료는 [design/testprep-toeic.md](../design/testprep-toeic.md)와 V1131~V1133 머리 주석이다(감독이 준 기출 후기 정리를 반영해 Part 5·6 138문항 중 137문항이 후기 포인트를 반영, 원본은 저장소 밖 `naesin-workspace/testprep/toeic`). 그 기준에 따라 모든 문항 해설 맨 앞에 유형 꼬리표가 붙어 있다. 새 열을 만들지 않고 이 값으로 묶는다(`ToeicFocus`).

- 파트 = `tp_questions.task_type`(`TOEIC_P2`~`TOEIC_P7`).
- 유형 = 해설 맨 앞 꼬리표에서 괄호 안 세부 설명과 `PartN-` 머리를 뗀 이름. 예) `[문법-품사(부사 자리)] 정답 (A)…` → `문법-품사`, `[Part6-문장삽입] …` → `문장삽입`.

탭 안의 묶음 순서:

1. **Part 5·6 어휘·어법 집중**(강조) — 세트마다 「Part 5·6 · 46문항」「Part 5 · 30문항」「Part 6 · 16문항」.
2. **Part 5 유형별로 골라 풀기**(강조) — 어법 유형(문항 많은 순) 다음에 어휘 유형. 줄마다 세트별 단추(그 세트에 든 그 유형 문항 수). 2026-11-22 세 세트 합계: 품사 15 · 전치사 9 · 접속사 6 · 대명사 5 · 분사 4 · 수일치 4 · 태 4 · 관계사 3 · 도치 3 · 시제 2 · 준동사 2 · 비교 1 · 제안절 1 / 동사 어휘 12 · 형용사 어휘 10 · 부사 어휘 5 · 명사 어휘 4.
3. **Part 6 유형별로 골라 풀기**(강조) — 문맥 어법 17 · 문장삽입 12 · 문맥 어휘 12 · 연결어 6 · 대명사 1.
4. Part 1 사진 묘사 — 문항이 없어 「준비 중」. Part 2·3·4(듣기, 1회분 세트에만 있음: 25·39·30문항), Part 7(세트마다 54문항, 줄에 유형별 문항 수).
5. 실전 세트 전체 풀기 — 실제 번호·실제 길이, 영역별 추정 점수.
6. 로그인 회원에게는 맨 위에 **내가 틀린 Part 5·6 문항**(세트별 「오답 N문항 다시 풀기」, 기존 오답 다시 풀기 그대로)이 나온다.

파트·유형만 골라 풀기(이번에 더한 것, TOEIC 세트 전용):

- `POST /api/testprep/attempts`에 `taskTypes`(파트 배열)와 `focusTag`(유형 이름)를 선택으로 받는다. 둘 다 주면 둘 다 맞는 문항만.
- 부분 응시(`tp_attempts.question_ids_json`)로 기록되므로 기존 규칙대로 **추정 점수 없이 정답 수·정답률·해설**만 나온다(몇 문항으로 495점이 나오는 것을 막는다).
- 시간 = 세트 시간 × (고른 문항 수 ÷ 세트 문항 수)를 분 단위로 올림, 최소 1분. 예) 100문항 75분 세트의 Part 5 30문항 → 23분.
- TOEIC이 아닌 세트에 보내면 400. iBT·SAT·내신의 응시 계약은 그대로다.

## 4. 타과목 게시 계약 (제작 공장 → 학생 풀이 페이지)

국어·수학·사회·과학은 **내신과 같은 디지털 모의고사 틀**로 푼다. 과목 구분은 새 열·새 `kind`를 만들지 않고 **시험 코드 머리**로 한다. 풀이·채점·이용 범위·재원생 앱 배정이 모두 `kind='NAESIN'`을 기준으로 이미 움직이기 때문에, `kind`를 늘리면 그 분기를 전부 고쳐야 한다. 코드 머리 규칙은 기존 내신이 학교를 가르는 방식(`TP-NAESIN-APM2-` 등)과 같다.

제작 쪽에서 「게시」할 때 아래 값으로 넣으면 그 탭에 나온다(`LearnHubCatalog.SUBJECTS`).

| 탭 | `tp_exams.exam_code` 머리 | 보기 |
|---|---|---|
| 국어 | `TP-NAESIN-KOR-` | `TP-NAESIN-KOR-APM2-2602-R01` |
| 수학 | `TP-NAESIN-MATH-` | `TP-NAESIN-MATH-APM2-2602-R01` |
| 사회(한국사 포함) | `TP-NAESIN-SOC-` | `TP-NAESIN-SOC-APM2-2602-R01` |
| 과학 | `TP-NAESIN-SCI-` | `TP-NAESIN-SCI-APM2-2602-R01` |

`tp_exams` 한 줄:

| 열 | 값 |
|---|---|
| `exam_code` | 위 머리 + 자유 식별(학교·학기 등) + `-R` + 두 자리 회차. 40자 이내, 유일. **끝이 `-R01`인 시험만 일반 로그인 회원이 풀 수 있고** 그 밖은 재원생·관리자만 푼다(기존 내신 규칙) |
| `kind` | `NAESIN` |
| `title` | 목록에 그대로 보인다. 예) `압구정중 2학년 국어 2학기 기말 1회` |
| `subtitle` | 목록 둘째 줄. 시험 범위를 **여러 쪽수 구간**으로 적는다. 예) `문학 12~45쪽, 72~83쪽 · 25문항 · 100점` |
| `time_limit_sec` | 서버 마감(넉넉히). 화면 시간은 내신과 같이 문항당 30초 |
| `status` | `PUBLISHED`일 때만 나온다. `DRAFT`·`ARCHIVED`는 나오지 않는다(게시 취소 = `ARCHIVED`) |

`tp_questions`(내신 문항과 같은 계약):

| 열 | 값 |
|---|---|
| `section` | 과목 표시 한 가지(예: `KO`, `MATH`, `SOC`, `SCI`). 한 시험 안에서는 같은 값 |
| `task_type` | `NAESIN_MC5`(①~⑤ 가운데 하나) 또는 `NAESIN_MC5_MULTI`(모두 고르기) |
| `prompt`, `body_text` | 발문, 지문. 그림은 `[[img:/naesin/…​.webp]]` 표기(정해진 폴더의 webp만) |
| `choices_json` | 선택지 5개 |
| `answer_key` | `A`~`E`. 모두 고르기는 `B,D` |
| `auto_score` | `1` |
| `points` | 배점(합계가 만점) |
| `explanation` | 첫 줄 `정답 ③ · 유형 · 배점`, 이어서 근거 |
| `order_no` | 문항 번호 |

- 한계: 풀이 화면은 수식 표기(LaTeX 등)를 그리지 않는다. 수식·도표가 든 문항은 그림으로 넣는다.
- 같은 `kind`라서 재원생 앱 배정 목록(`/dashboard` 디지털 모의고사 배정)과 정답·해설 조회에도 함께 나온다. 영어 내신 화면(`/naesintest`)에는 학교 코드 머리가 달라 나오지 않는다.
- 풀이 화면의 「나가기」·결과 화면의 「다른 회차」는 그 과목 탭(`/learn?tab=korean` 등)으로 돌아온다(`naesin_common.js`).
- SITE_PLAN의 시범 문항 9개는 「검수 전 시안」이라 넣지 않았다.

## 5. 파일

- `domain/learnhub/LearnHubCatalog`(탭·묶음·줄·단추), `LearnHubPageController`(`GET /learn`)
- `domain/testprep/service/ToeicFocus`(파트·유형 규칙), `TestPrepService.start`(파트·유형만 골라 풀기), `TpQuestionRepository`(문항 수 한 번에 세기, 유형 묶음용 가벼운 조회)
- `templates/public/learn.html`, `static/css/learn_hub.css`, `static/js/learn_hub.js`
- 모양은 `feat/f2r-lesson-prep-worksheets`의 `worksheets.css`·`_prep_shell.html`(흰 위 메뉴, 여백, 표·칩·단추)을 본떴다. 그 파일은 이 브랜치에 없어 고치지 않았고 필요한 모양은 `learn_hub.css`에 뒀다.
- 홈(`public/landing.html`)의 `/learn` 링크 두 줄 문구를 「문제 풀기」로 바꿨다.
- 캡처: [captures/](captures/)

## 6. 검증

- Java: `LearnHubCatalogTest`(탭 순서, 학교별 묶음, 회차 수, TOEIC 파트·유형 묶음, 타과목 준비 중·게시 계약, 모든 링크가 실제 등록된 GET 주소인지) · `LearnHubPageControllerTest` · `LearnHubTemplateContractTest` · `ToeicFocusTest` · `TestPrepFocusStartTest`
- MySQL 통합: `TestPrepFlowMySqlIntegrationTest`(Part 5·6만 풀기 46문항·35분·추정 점수 없음, 유형만 풀기, TOEIC 아닌 세트는 400) · `TestPrepPagesMySqlTest`(`/learn` 공개·게시된 시험에서 센 값)
- JS: `src/test/js/learn_hub.test.js`, `naesin_common.test.js`(타과목 되돌아가기)
- 기존 홈 계약: `LandingEdunovaTemplateContractTest`, `src/test/js/home_*.test.js`, `PublicAnalyticsLayoutContractTest`, `SitemapControllerTest`

## 7. 대표가 정할 것

1. 타과목 이용 범위: 지금 계약은 내신과 같다(1회는 회원 누구나, 2회부터 재원생). 타과목을 회원 모두에게 열지.
2. TOEIC 파트만 풀 때의 시간(지금: 세트 시간을 문항 수 비율로) — 파트별 권장 시간으로 따로 정할지.
3. 「잘 틀리는 부분」의 기준: 지금은 문항 유형 꼬리표와 학생 자신의 오답이다. 응시 기록이 쌓이면 전체 학생 오답률로 순서를 정할지.
4. `/learn`을 비회원에게 어디까지 열지(지금: 목록은 누구나, 풀이는 기존 규칙).
