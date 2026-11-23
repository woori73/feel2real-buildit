# 학생이 문제 푸는 페이지 (`/learn`)

> 2026-11-22 대표 지시: 큰 틀은 두 페이지 — ① **학생이 문제 푸는 페이지**(이 문서) ② Claude와 함께 문제 제작 페이지(`feat/f2r-question-factory`).
> 기준 문서: `feat/f2r-lesson-prep-worksheets`의 `docs/lesson-prep/SITE_PLAN.md`.
> 처음 올린 `/learn`은 기존 화면 링크 모음이었다. 이것을 **과목·시험 탭을 고르면 곧 푸는 화면**으로 다시 구성했다.

## 1. 원칙

- **풀이 엔진을 새로 만들지 않는다.** 응시·채점·해설은 기존 `tp_exams`/`tp_questions`/`tp_attempts`와 `/api/testprep/**`, 기존 풀이 화면을 그대로 쓴다. `/learn`은 시험을 고르고 응시를 만들어 풀이 화면으로 보내는 일만 한다.
- **문항을 지어내지 않는다.** 게시된 자료가 없는 과목·파트는 「준비 중」으로 보인다.
- **분량 숫자는 글로 적지 않는다.** 화면의 회차·문항 수는 요청 때마다 게시된 자료에서 센다(`LearnHubCatalog`). 이 문서에 적힌 수는 2026-11-22 로컬 DB(마이그레이션 V1136까지)에서 센 값이다.
- 스키마 변경·마이그레이션 없음.

## 2. 화면

- 위: 흰 메뉴(문제 풀기 · 내 점수 · 홈), 로그인 이름 또는 로그인 단추.
- 탭: **내신 · 수능 · iBT · SAT · TOEIC · 국어 · 수학 · 사회 · 과학**. 주소는 `/learn?tab=naesin|csat|ibt|sat|toeic|korean|math|social|science`(모르는 값은 내신). 탭은 링크라서 스크립트 없이도 열린다. 게시된 시험이 없는 탭에는 「준비 중」 표시가 붙는다.
- 탭 안: 묶음마다 표 한 장(제목·범위 / 분량 / 단추). 휴대폰 폭에서는 한 줄씩 쌓인다.
- **「풀기」**: 로그인 상태면 `POST /api/testprep/attempts`로 응시를 만들고 곧바로 풀이 화면으로 간다. 비회원이면 로그인 안내 창이 뜨고, 네이버 로그인 뒤 보던 탭(`/learn?tab=…`)으로 돌아온다.
- 로그인 규칙은 바꾸지 않았다. 탭 머리의 노란 표시가 그 탭의 기존 규칙이다.

| 탭 | 무엇이 풀리나 | 풀이 화면 | 로그인(기존 규칙) | 2026-11-22 분량 |
|---|---|---|---|---|
| 내신 | 학교별 영어 예상문제. 학년·학교 거르기는 이 탭 안에만 있다 | `/naesintest/run` (디지털 모의고사 그대로) | 목록 누구나 · 풀이 로그인. 1회(`-R01`)는 회원 누구나, 2회부터 재원생·관리자 | 3개 학교·학년 35회차(압구정중2 16 · 신구중2 12 · 압구정중1 7) |
| 수능 | 예비고1·고2 디지털 영어 모의고사 1~7부(회차를 고르면 그 회차 풀이 화면), 2027 수능 영어 해설, 모의고사 위키 | `/naesintest/pre-high/…` | 목록 누구나 · 풀이 로그인. 해설은 누구나 | 2부 22회 · 3부 10단원(단원마다 2부)+마무리 10회 · 4부 50회 · 6부 1회 · 7부 40회. 1부·5부(기출)는 서버 바깥 폴더(`app.pre-high-english.dir`) 자료라 로컬에서는 줄이 나오지 않는다. 운영 사이트의 예비고1 목록 화면에서 읽은 값은 1부 35회 · 5부 36회(2026-11-22) |
| iBT | iBT 모의고사, 어휘 퀴즈 | `/testprep/run` | 로그인. 첫 세트는 회원 누구나, 다른 세트는 재원생·관리자 | 모의고사 9세트 · 어휘 퀴즈 3세트 |
| SAT | 디지털 SAT 세트 | `/testprep/sat/run` | 로그인. 첫 세트는 회원 누구나, 다른 세트는 재원생·관리자 | 4세트 |
| TOEIC | 아래 3장 | `/testprep/run` | 로그인. 첫 세트는 회원 누구나, 다른 세트는 재원생·관리자 | 3세트 400문항 |
| 국어·수학·사회·과학 | 디지털 모의고사(내신과 같은 틀). 지금은 빈 목록 | `/naesintest/run` | 목록 누구나 · 풀이 로그인. **회원 누구나 전 회차** | 0 — 「준비 중 — 제작 공장에서 게시하면 여기에 올라옵니다.」 |

- 내신 탭: 로그인 회원에게는 풀던 회차가 「이어 풀기」로, 일반 회원에게는 2회부터 「재원생 전용」으로 보인다(`/api/naesintest/access`, 서버도 403으로 막는다).
- iBT·SAT는 풀이 화면·채점·문항 계약을 건드리지 않았다. 고친 것은 목록에서 바로 시작하는 진입뿐이다. 기존 목록 화면(`/testprep/practice`, `/testprep/sat`, `/testprep/toeic`)도 그대로 있다.

## 3. TOEIC — 잘 틀리는 부분을 섹션별로

대표 지시: iBT와 비슷하게 풀되 잘 틀리는 부분을 섹션별로. 대표 결정(2026-11-22): **Part 5·6과 독해(Part 7) 마지막 부분을 많이 틀린다** → 이 둘을 맨 위 강조 묶음으로 둔다.
저장소에서 찾은 기준 자료는 [design/testprep-toeic.md](../design/testprep-toeic.md)와 V1131~V1133 머리 주석이다(감독이 준 기출 후기 정리를 반영해 Part 5·6 138문항 중 137문항이 후기 포인트를 반영, 원본은 저장소 밖 `naesin-workspace/testprep/toeic`). 그 기준에 따라 모든 문항 해설 맨 앞에 유형 꼬리표가 붙어 있다. 새 열을 만들지 않고 이 값으로 묶는다(`ToeicFocus`).

- 파트 = `tp_questions.task_type`(`TOEIC_P2`~`TOEIC_P7`).
- 유형 = 해설 맨 앞 꼬리표에서 괄호 안 세부 설명과 `PartN-` 머리를 뗀 이름. 예) `[문법-품사(부사 자리)] 정답 (A)…` → `문법-품사`, `[Part6-문장삽입] …` → `문장삽입`.

탭 안의 묶음 순서:

1. **Part 5·6 어휘·어법 집중**(강조) — 세트마다 「Part 5·6 · 46문항」「Part 5 · 30문항」「Part 6 · 16문항」.
2. **Part 7 끝부분 · 이중·삼중 지문**(강조) — 실제 번호 176~200번, 세트마다 25문항(이중 지문 2묶음 + 삼중 지문 3묶음). 줄에 유형별 문항 수(연계·세부 사항 등).
3. **Part 5 유형별로 골라 풀기**(강조) — 어법 유형(문항 많은 순) 다음에 어휘 유형. 줄마다 세트별 단추(그 세트에 든 그 유형 문항 수). 2026-11-22 세 세트 합계: 품사 15 · 전치사 9 · 접속사 6 · 대명사 5 · 분사 4 · 수일치 4 · 태 4 · 관계사 3 · 도치 3 · 시제 2 · 준동사 2 · 비교 1 · 제안절 1 / 동사 어휘 12 · 형용사 어휘 10 · 부사 어휘 5 · 명사 어휘 4.
4. **Part 6 유형별로 골라 풀기**(강조) — 문맥 어법 17 · 문장삽입 12 · 문맥 어휘 12 · 연결어 6 · 대명사 1.
5. Part 1 사진 묘사(V1135로 6문항이 들어와 1회분 세트에 나온다. 문항이 없는 파트는 「준비 중」으로 보인다), Part 2·3·4(듣기, 1회분 세트에만 있음: 25·39·30문항), Part 7(세트마다 54문항, 줄에 유형별 문항 수).
6. 실전 세트 전체 풀기 — 실제 번호·실제 길이, 영역별 추정 점수.
7. 로그인 회원에게는 맨 위에 **내가 틀린 Part 5·6 문항**(세트별 「오답 N문항 다시 풀기」, 기존 오답 다시 풀기 그대로)이 나온다.

파트·유형만 골라 풀기(이번에 더한 것, TOEIC 세트 전용):

- `POST /api/testprep/attempts`에 `taskTypes`(파트 배열), `focusTag`(유형 이름), `fromNo`·`toNo`(실제 문항 번호 구간, 예: 176~200)를 선택으로 받는다. 여러 개를 주면 모두 맞는 문항만.
- 부분 응시(`tp_attempts.question_ids_json`)로 기록되므로 기존 규칙대로 **추정 점수 없이 정답 수·정답률·해설**만 나온다(몇 문항으로 495점이 나오는 것을 막는다).
- 시간(대표 결정: 실제 TOEIC 시간을 문항 수로 나눠 정한다): 실제 시험은 Reading 75분·100문항, Listening 약 45분·100문항이다.
  - **읽기(Part 5·6·7): 문항당 45초**(75분 ÷ 100문항). 고른 문항 수 × 45초를 분 단위로 올림, 최소 1분. 예) Part 5 30문항 → 23분, Part 5·6 46문항 → 35분, Part 7 끝부분 25문항 → 19분, 품사 5문항 → 4분.
  - **듣기(Part 2·3·4)**: 실제 시험대로면 문항당 27초(45분 ÷ 100문항)지만, 이 화면은 대본을 문항마다 브라우저 음성으로 다시 읽어 주어 27초로는 모자란다. 그래서 듣기는 27초와 그 세트의 문항당 시간(1회분 세트 = 120분 ÷ 200문항 = 36초) 가운데 긴 쪽을 쓴다. 녹음 음원이 들어오면 27초로 맞춘다.
  - 실제 시험은 파트별 시간을 따로 정하지 않는다(Reading 75분을 수험자가 나눠 쓴다). 그래서 파트별로 다른 값을 두지 않고 영역 시간 ÷ 문항 수 한 가지를 쓴다.
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
| `exam_code` | 위 머리 + 자유 식별(학교·학기 등) + `-R` + 두 자리 회차. 40자 이내, 유일. **타과목은 로그인한 회원 누구나 전 회차를 푼다**(대표 결정 2026-11-22, `NaesinAccessService.isSubjectExam`). 영어 내신의 「1회만」 규칙은 적용하지 않는다 |
| `kind` | `NAESIN` |
| `title` | 목록에 그대로 보인다. 예) `압구정중 2학년 국어 2학기 기말 1회` |
| `subtitle` | 목록 둘째 줄. 시험 범위를 **여러 쪽수 구간**으로 적는다. 예) `문학 12~45쪽, 72~83쪽 · 25문항 · 100점` |
| `time_limit_sec` | **7200 고정**. 풀이 화면(`naesin_run.js`)이 서버 마감을 7200초로 보고 화면 시간을 문항당 30초로 계산하므로, 다른 값이면 시작하자마자 자동 제출된다 |
| `status` | `PUBLISHED`일 때만 나온다. `DRAFT`·`ARCHIVED`는 나오지 않는다(게시 취소 = `ARCHIVED`) |

`tp_questions`(내신 문항과 같은 계약):

| 열 | 값 |
|---|---|
| `section` | 과목 표시 한 가지(예: `KO`, `MATH`, `SOC`, `SCI`). 한 시험 안에서는 같은 값 |
| `task_type` | `NAESIN_MC5`(①~⑤ 가운데 하나) 또는 `NAESIN_MC5_MULTI`(모두 고르기) |
| `prompt`, `body_text` | 발문, 지문. 글로 넣으면 글 그대로(줄바꿈 유지) 보이고, 그림은 `[[img:/naesin/{폴더}/rNN/….webp]]` 표기(webp만, 통째 또는 글 사이). **타과목 그림 폴더 이름은 `kor-` / `math-` / `soc-` / `sci-`로 시작**해야 한다 — 이 머리가 아니면 2회부터의 그림이 재원생에게만 보인다(`NaesinImageAccessFilter`) |
| `choices` | 선택지 5개. 「①」…「⑤」만 넣으면 그림 문항으로 보고 선택지 글을 따로 안 보인다. 글을 넣으면 풀이·리뷰 화면에 번호와 함께 보인다 |
| `choices_json` | 선택지 5개 |
| `answer_key` | `A`~`E`. 모두 고르기는 `B,D` |
| `auto_score` | `1` |
| `points` | 배점(합계가 만점) |
| `explanation` | 첫 줄 `정답 ③ · 유형 · 배점`, 이어서 근거 |
| `order_no` | 문항 번호 |

- 시험지 머리의 과목은 시험 코드 머리(KOR/MATH/SOC/SCI, 없으면 영어), 학년은 제목의 「N학년」에서 읽는다.
- 한계: 풀이 화면은 수식 표기(LaTeX 등)를 그리지 않는다. 수식·도표가 든 문항은 그림으로 넣는다.
- 같은 `kind`라서 재원생 앱 배정 목록(`/dashboard` 디지털 모의고사 배정)과 정답·해설 조회에도 함께 나온다. 영어 내신 화면(`/naesintest`)에는 학교 코드 머리가 달라 나오지 않는다.
- 풀이 화면의 「나가기」·결과 화면의 「다른 회차」는 그 과목 탭(`/learn?tab=korean` 등)으로 돌아온다(`naesin_common.js`).
- SITE_PLAN의 시범 문항 9개는 「검수 전 시안」이라 넣지 않았다.

### 제작 공장과의 연결 확인 (2026-11-22)

제작 공장 브랜치(`feat/f2r-question-factory`, V1138·V1139)의 게시 계약(`FactoryExamContract`)은 이 장과 같은 값을 쓴다: `kind='NAESIN'`, 같은 시험 코드 머리 네 개, `NAESIN_MC5`/`NAESIN_MC5_MULTI`, 서버 시간 7200초, 게시 뒤 안내 주소 `/learn?tab=korean` 등. 두 브랜치를 임시로 합쳐 따로 만든 로컬 DB에서 돌려 보았다(합친 것은 올리지 않았다).

- 제작 공장에서 국어 묶음을 게시 승인 → `/learn` 국어 탭 목록에 그 시험이 나오고 다른 탭에는 나오지 않음
- 일반 회원이 그 시험을 시작할 수 있음(2회 코드도 열림)
- 게시를 내리면(`ARCHIVED`) 국어 탭이 다시 「준비 중」

두 브랜치를 실제로 합칠 때 `docs/API.md` · `docs/FLOWS.md` · `docs/INDEX.md` 세 문서가 겹친다(코드는 겹치지 않음). 제작 공장 쪽은 「이미 사이트에 있는 그림만」 받으므로, 타과목 문항에 그림을 쓰려면 그림 폴더 머리(`kor-` 등) 규칙에 맞는 그림을 먼저 올려야 한다.

## 5. 파일

- `domain/learnhub/LearnHubCatalog`(탭·묶음·줄·단추), `LearnHubPageController`(`GET /learn`)
- `domain/testprep/service/ToeicFocus`(파트·유형 규칙), `TestPrepService.start`(파트·유형만 골라 풀기), `TpQuestionRepository`(문항 수 한 번에 세기, 유형 묶음용 가벼운 조회)
- `templates/public/learn.html`, `static/css/learn_hub.css`, `static/js/learn_hub.js`
- 모양은 `feat/f2r-lesson-prep-worksheets`의 `worksheets.css`·`_prep_shell.html`(흰 위 메뉴, 여백, 표·칩·단추)을 본떴다. 그 파일은 이 브랜치에 없어 고치지 않았고 필요한 모양은 `learn_hub.css`에 뒀다.
- 홈(`public/landing.html`)의 `/learn` 링크 두 줄 문구를 「문제 풀기」로 바꿨다.
- 2026-11-22 대표 지시 「홈페이지에 문제풀이 페이지 소개하고 누르면 연결되게」: 홈 「학습 콘텐츠」(`#learning-content`) 맨 위에 소개 카드(`#learn-hub`)를 두고, 탭 이름 9개는 `/learn?tab=<탭>`으로, 「문제 풀러 가기」는 `/learn`으로 잇는다. 위쪽 바로가기 줄(`.hl-chips`)에도 「문제 풀기」를 넣었다. 홈의 `/learn` 링크는 상단 메뉴·바로가기 줄·전체 메뉴·카드 단추 4곳(`LearnHubTemplateContractTest`). 카드의 이용 범위 문구는 §7과 같게 고친다.
- 2026-11-23 대표 지시 「문제 풀기가 중요한 파트이니 상단에, 불필요한 부분은 삭제」: 소개 카드를 「학습 콘텐츠」에서 빼서 AI 코디(`#coordinator`)·공지 바로 아래 「문제 풀기」 구역(`#learn`)으로 옮겼다(구역 순서는 `LandingEdunovaTemplateContractTest`). 삭제 범위는 대표가 고른 「배너 캐러셀의 일부 장」만: 위쪽 배너 6장 중 모의고사 위키·영어 복습 게임·AI AGENT·전자책 4장과 그 타일을 지우고 디지털 기말고사·맞춤문제 2장만 남겼다. 각 서비스의 본문 구역(위키 링크·복습 게임·AI 드라마·전자책)은 그대로다. 지운 장만 쓰던 그림 5개(`home-card-wiki`·`home-tile-wiki`·`ai-agent-hero`·`home-tile-ebook`·`home-card-game`)는 예전 검증 기록(`docs/verification/home-*`)이 가리키고 있어 파일은 남겼다.
- 캡처: [captures/](captures/)

## 6. 검증

- Java: `LearnHubCatalogTest`(탭 순서, 학교별 묶음, 회차 수, TOEIC 파트·유형 묶음, 타과목 준비 중·게시 계약, 모든 링크가 실제 등록된 GET 주소인지) · `LearnHubPageControllerTest` · `LearnHubTemplateContractTest` · `ToeicFocusTest` · `TestPrepFocusStartTest`
- MySQL 통합: `TestPrepFlowMySqlIntegrationTest`(Part 5·6만 풀기 46문항·35분·추정 점수 없음, 유형만 풀기, TOEIC 아닌 세트는 400) · `TestPrepPagesMySqlTest`(`/learn` 공개·게시된 시험에서 센 값)
- JS: `src/test/js/learn_hub.test.js`, `naesin_common.test.js`(타과목 되돌아가기)
- 기존 홈 계약: `LandingEdunovaTemplateContractTest`, `src/test/js/home_*.test.js`, `PublicAnalyticsLayoutContractTest`, `SitemapControllerTest`

## 7. 대표 결정 (2026-11-22) 과 반영

| # | 결정 | 반영 |
|---|---|---|
| 1 | 타과목은 모두에게 | 국어·수학·사회·과학은 로그인한 회원 누구나 전 회차(시작·문항 그림 모두). 영어 내신 규칙은 그대로 |
| 2 | TOEIC 시간을 찾아 문항당 나누어 정하기 | 읽기 문항당 45초(75분 ÷ 100문항). 듣기는 3장의 까닭으로 27초를 그대로 쓰지 못했다 |
| 3 | TOEIC Part 5·6과 독해 마지막 부분을 많이 틀림 | 맨 위 강조 묶음 두 개: Part 5·6 집중, Part 7 끝부분(176~200번 이중·삼중 지문) |
| 4 | 비회원은 로그인해서 회원이 되면 1회 사용. iBT·SAT·TOEIC도 회원은 첫 세트만 | 비회원은 목록만 보고, 풀기를 누르면 로그인 안내. 영어 내신은 로그인 회원에게 1회. iBT·어휘 퀴즈·SAT·TOEIC은 종류마다 **첫 세트만** 일반 회원에게 열리고, 다른 세트는 재원생·관리자만 시작한다(`TestPrepService.FIRST_SET_KINDS`, 서버가 403으로 막고 `/learn`은 「재원생 전용」으로 표시) |

첫 세트 = 그 종류의 게시된 세트 가운데 먼저 올라온 것(`exam_id`가 가장 작은 것). 2026-11-22 기준: iBT `TP-PRACTICE-2026-MINI`(Mini Set 18문항), 어휘 퀴즈 `TP-VOCAB-EASY`, SAT `TP-SAT-MINI-01`(Mini Set 01 18문항), TOEIC `TP-TOEIC-FULL-01`(기출 유사 1회, 200문항). 재원생 = 내신과 같은 기준(`NaesinAccessService.hasFullAccess`).

- 일반 회원은 TOEIC 첫 세트 안에서는 파트·유형·Part 7 끝부분 골라 풀기를 모두 쓸 수 있다. 독해 추가 세트 1·2는 재원생 전용이다.
- 이미 시작한 응시의 이어 풀기와 지난 결과·해설 보기는 막지 않는다(새로 시작하는 것만 막는다). 오답 다시 풀기도 잠긴 세트의 것은 시작되지 않는다.
- 기존 목록 화면(`/testprep/practice`·`/testprep/vocab`·`/testprep/sat`·`/testprep/toeic`)도 일반 회원에게는 첫 세트만 Start이고 나머지는 「재원생 전용」 표시로 보인다(`testprep.js`의 `TP.startButtonHtml`, 이용 범위는 `/api/naesintest/access`). 범위를 못 불러오면 표시 없이 두고 시작할 때 서버가 막는다. 캡처: `captures/locked-testprep-*.jpg`, `captures/learn-pc-*-member.jpg`.
- 첫 세트를 다른 세트로 정하고 싶으면(예: iBT를 Mini Set이 아닌 실제 길이 세트로) 종류별로 지정하는 값이 필요하다 — 대표 결정.
