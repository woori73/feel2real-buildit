# 제작 공장 — Claude와 함께 문제 제작 (`/dashboard/factory`)

> 2026-11-22 대표 지시. 기준: [SITE_PLAN.md](SITE_PLAN.md) §3 「제작 공정(공장식)」. 브랜치 `feat/f2r-question-factory`(PR #1173 위에 쌓음).
> 스키마: 현황판 자체는 변경 없음. 학생 페이지 게시 연결에 **V1139** `coord_factory_exam_packages` 한 표를 더했다(§5).

## 1. 무엇을 만들었나

| 구분 | 내용 |
|---|---|
| **그대로 쓴 것** | 제작 작업 대기열 `CoordPcTaskService`(`coord_jobs` kind=PC_TASK, 기록 `coord_job_events`), MCP 도구 `f2r_pc_task_request/list/claim/brief/review/finish/cancel`, 제작 지시서(brief)·briefHash, 화면 틀 `fragments/_prep_shell.html` + `worksheets.css` |
| **고친 것** | 검수 필요 수 2곳 → **3곳**(`REQUIRED_REVIEWS`). 요청에 「공장 정보」(과목·시험 구분, 쪽수 구간, 자료 위치, 문항 표시)를 받도록 `request` 확장. 수능·공인시험은 학교 없이 맡길 수 있게. 지시서·작업 지시문에 「새로 쓰기」 원칙과 공정 6단계 |
| **새로 더한 것** | MCP 도구 `f2r_pc_task_stage`(제작 세션의 단계 보고), 화면 `/dashboard/factory`, API `/api/admin/coord/factory/**`(`CoordFactoryService`·`CoordFactoryController`), 공정 판정 `FactoryStage`, 요청 검증 `FactoryPlan`, 왼쪽 메뉴 「제작 공장」 |
| **만들지 않은 것** | 새 대기열, Claude API 호출(전 환경 OFF 그대로), 파일·그림 올리기 |
| **게시 연결 ①** | 시험 묶음 제출·읽기 MCP(`f2r_pc_task_exam_submit`·`f2r_pc_task_exam_read`), 게시 승인 때 `tp_exams`·`tp_questions` 탑재, 내림 = ARCHIVED (`FactoryExamPublisher`·`FactoryExamContract`, 표 `coord_factory_exam_packages` V1139) — §5 |
| **게시 연결 ②** | `FactoryExamUploader` — 게시 승인된 내신(영어) 5지선다 글 문항을 화면에서 바로 학생 풀이 페이지에 올림(§5, 대표 결정 2026-11-22) |

Claude는 서버가 부르지 않는다. 화면은 **요청을 올리고 · 진행과 검수를 보여 주고 · Claude 세션에 붙여 넣을 문장을 건넨다.** 세션이 MCP로 대기열 작업을 처리한다.

## 2. 공정 6단계와 판정

```
① 기출 분석 → ② 시험범위 자료(공인시험은 기출 업데이트) → ③ 변형·유사 문제 제작
→ ④ 검수: 만든 세션이 아닌 세션 최소 3곳 → ⑤ 검수 후 수정 → ⑥ 게시 = 학생 풀이 페이지에 올림
```

단계는 따로 저장하지 않는다. `coord_jobs.status`와 `coord_job_events`만 읽어 낸다(`FactoryStage.of`).

| 작업 상태 | 기록 | 단계 · 화면 글 |
|---|---|---|
| `queued` | — | ① 「대기 중 — 아직 맡은 세션이 없습니다」 |
| `running` | 기록 없음(가져가기만) | ① 「기출 분석 중」 |
| `running` | 마지막이 STAGE `SCOPE` | ② 「시험범위 자료 확인 중」 |
| `running` | 마지막이 STAGE `DRAFT` | ③ 「문항 제작 중」 |
| `running` | 마지막이 STAGE `REVIEW` 또는 REVIEW `PASS` | ④ 「검수 n/3」 |
| `running` | 마지막이 REVIEW `FIX` | ⑤ 「검수 의견 반영 중」 — 제작 세션이 고치고 STAGE `REVIEW`를 다시 남기면 ④ 0/3 |
| `running` | 마지막 FIX 뒤 서로 다른 세션 PASS 3곳 | ⑥ 「검수 통과 — 원장 확인·저장 대기」 |
| `completed` | PUBLISH 기록 없음(또는 마지막이 `WITHDRAWN`) | ⑥ 「게시 대기 — 원장 승인 필요」 |
| `completed` | 마지막 PUBLISH가 `APPROVED` | ⑥ 「게시 승인됨 — 탑재 대기」 |
| `completed` | 마지막 PUBLISH가 `PUBLISHED · /주소` | ⑥ 「게시됨 — 학생 풀이 페이지에 올라감」 |
| `cancelled`·`failed` | — | 공정 밖(「취소·실패도 보기」를 켜야 보임) |

- ①②③의 구분은 제작 세션이 `f2r_pc_task_stage`로 남겨야 보인다. 남기지 않으면 ①에 머물다가 첫 검수가 들어올 때 ④로 넘어간다(지시문 0번에 보고하라고 적어 둠).
- 검수 통과 수는 기존 규칙 그대로: **마지막 FIX 이후** 서로 다른 검수 세션의 PASS만 센다. 화면에서 FIX 앞의 통과는 흐리게 보인다.
- 검수 없는 종류(`OTHER`, MCP로만 맡길 수 있음)는 ④에서 「제작 마무리」로 보이고 게시할 수 없다.

## 3. 화면

| 영역 | 내용 |
|---|---|
| 위·왼쪽 메뉴 | 학습지 화면과 같은 틀. 왼쪽 메뉴에 「제작 공장」 추가(학습지 화면들에서도 보임), 업무 대시보드 「자료·문제 제작」 묶음에 링크 |
| 원칙 안내 | 「기출·시험범위 자료에서는 평가 요소·사고 과정·오개념만 읽는다. 지문·보기·선지·해설은 새로 쓰고, 다른 회사의 지문·문항·그림은 싣지 않는다.」 — 지시서 6번과 같은 글(`FactoryPlan.ORIGINALITY`) |
| 공정 띠 | 여섯 칸, 칸마다 지금 그 단계에 있는 작업 수. 누르면 그 단계만 본다. 좁은 화면에서는 3칸 × 2줄 |
| 작업 표 | 구분 · 자료(제목, 범위 구간, 문항 표시) · 지금 단계(여섯 칸 진행 띠 + 글) · 검수 n/3 · 맡은 세션 · 맡긴 날 · 자세히. 구분 고르기, 검색, 취소·실패 보기 |
| 자세히 | 요청 내용(구분·시험·범위·자료 위치·지시), **Claude 세션에 붙여 넣을 문장**(복사), 검수 의견 목록, 제작 세션 보고, 올린 자료(결과 요약·저장 위치·검수한 세션), 게시(승인·게시됨 표시·내림), 제작 지시서 보기, 요청 취소 |
| 제작 요청 | 과목·시험 구분 9종(내신 영어·수능·iBT·SAT·TOEIC·국어·수학·사회·과학), 학교·학년(학교 시험 구분만), 연도·학기·시험, **시험 범위 쪽수 구간 여러 줄**(예 `12~45` / `72~83` / `92~115`, 겹침·거꾸로는 바로 안내), 기출·시험범위 자료 위치, 자료 종류·문항 수·맡길 곳, 문항 표시 3종, 원장님 지시 |

붙여 넣을 문장: 대기 중이면 `F2R 대기 작업 처리해줘(worker=CLOUD, jobId=…)` 한 줄, 검수 단계면 남은 검수 수만큼 `F2R 검수 jobId=… reviewer=REVIEW-n …`(세션마다 다른 이름). 검수는 **새 세션**에서 붙여 넣는다.

자료 올리기: 파일은 학습지 자료실 「내신위키 자료」 탭(`/dashboard/worksheets/library?tab=wiki`)이나 기존 내신위키 올리기(`/problem-generator`)에서 내신위키에 올린다. 학교 시험 구분이면 제작 요청 칸의 「내신위키에서 고르기」가 같은 학교·학년·연도·학기·시험의 자료(기출·기출 분석 → 「기출 자료」, 학교 프린트·완성본 → 「시험범위 자료」)를 「내신위키 #번호 제목(종류)」로 채운다. 직접 적어도 된다.

타과목(국어·수학·사회·과학)은 단원 트리·유형 체계를 따로 두지 않았다 — 기출과 시험범위 자료 위치, 쪽수 구간을 받아 그에 맞춰 만들게 한다(SITE_PLAN §2).

캡처: [captures/](captures/) `factory-pc-*.jpg`(1280px), `factory-mobile-*.jpg`(375px) — 1 현황판 · 2 자세히(검수 단계) · 3 자세히(게시) · 4 제작 요청. 로컬 DB에 넣은 가짜 작업으로 찍었다.

### 업무 대시보드·통합 대시보드에서 들어가기 (2026-11-23)

- 업무 대시보드(`/dashboard`): 위 메뉴 「문제 제작」·「학습지」, 학부모 카카오톡 바로 아래 「문제 제작 · 수업 준비」 카드 3장(제작 공장 · 학습지 · 학생 문제 풀기 `/learn`).
- 통합 대시보드(`/admin/hub`): 이번 달 요약 아래 같은 카드 3장 + 「자주 가는 곳」 타일 3개(제작 공장 · 학습지 · 학생 문제 풀기).
- 관리자 위 메뉴: 「전체 메뉴 › 콘텐츠·제작」에 같은 3개(위 메뉴 첫 줄은 6개로 두는 기존 규칙을 지켰다).
- 제작 공장 카드의 숫자(제작·검수 중 / 게시 승인 대기 / 게시됨)는 `factory_summary.js`가 `/api/admin/coord/factory`에서 읽는다. 원장(ADMIN)이 아니면 숫자 대신 「원장 계정에서 합니다」 안내만 보인다. 게시 승인 대기가 있으면 노란 알림이 뜬다. 조각: `fragments/_factory_summary.html`.
- 캡처: `captures/home-dashboard-pc.jpg`·`home-dashboard-mobile.jpg`·`home-hub-tiles-pc.jpg`.

## 4. 권한 · CRUD 범위 · 삭제 정책

**권한**: 기존 대기열이 `ADMIN` + 본인(같은 계정) 작업만 다루므로 그대로 따랐다 — 대기열 작업은 원장 계정으로 연결한 Claude 세션(MCP)이 처리하고, 다른 계정이 올린 작업은 그 세션에 보이지 않는다. 화면 주소는 학습지와 같이 ADMIN·TUTOR가 열 수 있지만 TUTOR에게는 「원장 계정에서만 쓸 수 있습니다」 안내만 보인다(API는 403). 강사(TUTOR)도 요청을 올리게 하려면 대기열의 소유 단위를 계정에서 학원(사업체)으로 바꿔야 한다 — 대표 결정 사항(§7).

| 대상 | 만들기 | 보기 | 고치기 | 지우기 |
|---|---|---|---|---|
| 제작 요청(`coord_jobs`) | 화면 「제작 요청」 또는 MCP `f2r_pc_task_request` | 현황판(최근 100건) | **없음** — 요청(plan)은 바꿀 수 없다. 지시서와 briefHash가 요청에서 나오고, 검수 세션이 「원장님 요청」과 대조하기 때문. 바꾸려면 취소하고 다시 올린다 | **대기 중일 때 취소만**(상태 `cancelled`, 행은 남김). 가져간 뒤에는 취소 불가(세션이 `finish FAILED`로 끝냄). 물리 삭제 없음 |
| 검수·단계 기록(`coord_job_events`) | Claude 세션(MCP)만 | 자세히 창 | 없음 | 없음(감사 기록) |
| 게시 기록(actor `PUBLISH`) | 화면: 승인 → 게시됨 표시 | 자세히 창 | 「게시 내림」으로 되돌림(새 기록을 쌓음) | 없음 |

Create-only에 가까운 까닭: 대기열이 처음부터 「삭제·수정 API 없음, 감사 보존」으로 설계돼 있고(DOMAIN.md), 요청이 바뀌면 이미 받은 검수가 무엇을 기준으로 통과했는지 알 수 없게 된다.

## 5. 게시 — 학생 풀이 페이지에 올리기

### 탑재 방법 세 가지 (대표 결정 2026-11-22: 화면 탑재·PR 둘 다)

| 방식 | 되는 자료 | 언제 올라가나 | 검수와의 관계 |
|---|---|---|---|
| **① 검수받은 시험 묶음** (권장) | 내신(영어)·국어·수학·사회·과학, 사이트에 이미 있는 그림 | 「게시 승인」을 누르는 순간 | 검수 3곳이 **바로 그 묶음**을 보고 통과시킨 것만 올라간다 |
| ② 게시용 파일 올리기 | 내신(영어) 그림 없는 5지선다 글 문항, 묶음이 없는 작업 | 승인 뒤 자세히 창에서 파일을 고를 때 | 파일이 검수한 결과물과 같은지는 원장이 확인 |
| ③ 개발 PR | 수능·iBT·SAT·TOEIC 세트, 새 그림이 필요한 문항 | PR 병합·배포 뒤 「게시됨으로 표시」 | PR 리뷰 |

묶음이 있는 작업에는 ②를 열지 않는다(검수하지 않은 다른 파일이 올라가지 않게).

### ① 검수받은 시험 묶음

**디지털 모의고사 형태(내신 영어·국어·수학·사회·과학)는 서버가 직접 올린다.** 학생 페이지 쪽 계약은 `feat/f2r-learn-hub`의 `docs/learn-hub/README.md` 4장과 같다(코드: `FactoryExamContract`).

```
제작 세션: f2r_pc_task_exam_submit(jobId, exam{…})   ← 검수를 맡기기 전에
검수 세션 3곳: f2r_pc_task_exam_read로 같은 묶음을 받아 대조 → f2r_pc_task_review PASS
제작 세션: f2r_pc_task_finish
원장: 제작 현황판 「게시 승인」 → 서버가 tp_exams(kind=NAESIN, status=PUBLISHED) + tp_questions를 넣고 「게시됨」
원장: 「게시 내림」 → tp_exams.status = ARCHIVED(응시 기록 때문에 지우지 않음). 다시 「게시 승인」하면 같은 시험을 PUBLISHED로
```

| 구분 | `exam_code` 머리 | 문항 `section` | 학생 페이지 |
|---|---|---|---|
| 내신(영어) | `TP-NAESIN-<학교 코드>-` (예 `TP-NAESIN-SGM2-2602-R13`) | `EN` | `/naesintest`(학교 화면은 코드 머리로 자동 분류) |
| 국어 | `TP-NAESIN-KOR-` | `KO` | `/learn?tab=korean` |
| 수학 | `TP-NAESIN-MATH-` | `MATH` | `/learn?tab=math` |
| 사회 | `TP-NAESIN-SOC-` | `SOC` | `/learn?tab=social` |
| 과학 | `TP-NAESIN-SCI-` | `SCI` | `/learn?tab=science` |

- **묶음 검증**(`FactoryExamContract.parse`, 틀린 곳을 문항 번호와 함께 한 번에 알림): 코드 머리·`-R` 두 자리 회차·40자, 제목 1~150자, 범위 글 255자, 1~60문항, 번호 1부터 차례, `NAESIN_MC5`·`NAESIN_MC5_MULTI`, 선택지 5개, 정답 A~E(모두 고르기는 `B,D`), 배점 1~20, 해설 첫 줄 「정답 …」, 그림은 사이트에 이미 있는 `[[img:/naesin/….webp]]`만, 이미 쓰이는 시험 코드 거절.
- **검수와 판**: 묶음을 낼 때 기록(`STAGE · EXAM · 코드 · N문항 · 판`)을 남긴다. 게시 승인은 **마지막 제출 뒤**의 서로 다른 검수 PASS가 3곳일 때만 된다 — 묶음을 다시 내면 검수를 다시 받아야 한다. 판(`package_hash`)은 자세히 창과 `f2r_pc_task_exam_read`에 보인다.
- **시간**: `time_limit_sec`는 묶음·파일 값과 관계없이 **7200**으로 넣는다. 내신 풀이 화면(`naesin_run.js`)이 서버 시간을 7200초로 보고 경과 시간을 계산하기 때문이다(600초를 넣었더니 시작하자마자 자동 제출되는 것을 실제 화면에서 확인해 고침). 학생 화면 시간은 문항당 30초.
- **저장**: `coord_factory_exam_packages`(V1139, 작업마다 한 줄). 게시하면 `exam_id`·`published_at`이 채워진다.
- **수능·iBT·SAT·TOEIC**은 세트 형식(듣기·지문 묶음·점수 환산)이 달라 이 경로로 올리지 않는다. 지금처럼 「게시 승인」 → 탑재 대기(APPROVED) → 세트 마이그레이션 PR → 「게시됨으로 표시」.
- **한계**: 새 그림을 올리는 길이 없다(그림 문항은 먼저 저장소에 그림을 넣는 PR이 필요). 풀이 화면이 수식을 그리지 않으므로 수식·도표는 그림으로. 국어·수학·사회·과학 탭(`/learn?tab=…`)은 PR #1176이 병합돼야 보인다 — 그 전에는 재원생 앱 배정 목록에만 나온다.

### ② 게시용 파일 — 화면에서 바로 올리기 (검수받은 묶음이 없는 내신 영어 글 문항)

```json
{ "examCode": "TP-NAESIN-SGM2-2602-R12",
  "title": "신구중 2학년 2학기 기말 12회 · Lesson 7·8 종합 예상문제 2회",
  "subtitle": "Lesson 7·8 전 범위 · 40문항 · 20분 · 100점",
  "description": "실제 시험처럼 풀이 중에는 해설이 없습니다.",
  "questions": [ { "prompt": "발문", "body": "지문(없으면 생략)", "choices": ["…", "…", "…", "…", "…"],
                   "answer": "A", "points": 2, "explanation": "해설" } ] }
```

- `examCode`: `TP-NAESIN-<학교·학년>-<연도 두 자리+학기+시험>-R<회차>`. 앞부분이 학생 페이지의 학교(`NaesinTestPageController.SCHOOLS`)와 맞아야 하고, 이미 있는 코드는 거절한다(덮어쓰지 않음).
- `answer`: A~E. 「모두 고르면」은 `"B,D"`(문항 유형이 `NAESIN_MC5_MULTI`로 들어간다). `points` 1~20, `choices` 5개, `explanation` 필수, 문항 1~100개.
- 틀린 곳은 문항 번호와 함께 한 번에 알려 주고, 하나라도 틀리면 아무것도 넣지 않는다.
- 들어가는 행은 기존 마이그레이션(V1121)과 같다: `tp_exams`(kind=`NAESIN`, status=`PUBLISHED`) + `tp_questions`(section `EN`, auto_score 1). 스키마 변경 없음.
- 제작 세션은 그림 없는 글 문항이면 `outputs/<jobId>/publish.json`을 함께 만들도록 작업 지시문에 적어 두었다.
- **내림**: 화면에서 올린 회차는 「게시 내림」을 누르면 `status=ARCHIVED`로 바뀌어 학생 페이지에서 사라진다(행과 학생 응시 기록은 남김). 다시 올리려면 회차 번호를 바꾼다.

### ③ PR 탑재 절차 (수능·iBT·SAT·TOEIC, 새 그림이 필요한 문항)

1. 원장이 현황판에서 「게시 승인」 → 작업이 「탑재 대기」로 보인다.
2. 개발 세션이 자세히 창의 「올린 자료」(저장 위치 `wikiRef`)에서 결과물을 받아 탑재 PR을 만든다.
   - 내신: `tp_exams`(kind=`NAESIN`, status=`PUBLISHED`) + `tp_questions`, 기존 V1121과 같은 형식의 마이그레이션(번호는 그때의 `origin/main` 최고 + 1, [WORKFLOW.md](../WORKFLOW.md) §4).
   - 디지털 모의고사 탑재는 원장이 지시한 작업(`publishDigital=true`)만.
   - iBT·SAT·TOEIC: TestPrep 세트 마이그레이션(V1125~V1133과 같은 형식).
   - 타과목: 학생 페이지(`feat/f2r-learn-hub`)의 「디지털 모의고사 틀」이 정해지면 그 형식.
3. PR 병합 → 원장 「배포 승인」 → 운영 반영.
4. 원장(또는 원장 계정으로 들어온 개발 세션)이 현황판에서 학생 페이지 주소(예 `/naesintest/school/singu-m2`)를 적고 「게시됨으로 표시」.


## 6. 검수 3곳 변경이 닿은 곳

- 코드: `CoordPcTaskService.REQUIRED_REVIEWS`(2 → 3), 검수 안내 문장(「n곳 더」), 지시서·작업 지시문의 「최소 3회」·「3곳」, `McpGateway` 도구 설명 4곳(request·claim·review·finish), `McpController` 서버 안내 문구.
- 문서: [API.md](../API.md)·[FLOWS.md](../FLOWS.md)·[DOMAIN.md](../DOMAIN.md), `tools/f2r-pc/CLAUDE.md`·`README.md`(학원 PC 작업 폴더 안내), [SITE_PLAN.md](SITE_PLAN.md).
- 테스트: `CoordPcTaskServiceTest`(2곳으로는 거절, 3곳 뒤 완료, FIX 뒤 다시 3곳), `McpCoordinatorToolsTest`.
- **운영에서 달라지는 점**: ① 지금 처리 중인 작업도 배포 뒤부터 3곳이 필요하다. ② 지시서 글이 바뀌어 briefHash가 달라진다 — 배포 전에 지시서를 받아 둔 검수 세션은 `f2r_pc_task_brief`를 다시 받아야 한다(이미 남긴 PASS는 그대로 센다). ③ 학원 PC·집 노트북에 복사해 둔 `tools/f2r-pc/CLAUDE.md`는 **이번 한 번** 새 파일로 바꿔야 한다(옛 파일은 두 번 검수·네 항목으로 움직여 서버가 통과·완료를 거절한다 — 틀린 결과가 나가지는 않지만 검수가 막힌다). 새 파일에는 기준을 적지 않고 서버가 내려 주는 `workInstructions`(제작)·`reviewInstructions`(검수, `f2r_pc_task_brief` 결과)를 따르라고만 적었으므로, 다음부터는 기준이 바뀌어도 기기의 파일을 고치지 않는다(대표 승인 2026-11-22). 클라우드 세션은 처음부터 서버 지시문을 읽으므로 바꿀 것이 없다.

## 7. 대표 결정 (2026-11-22)

1. **제작 요청은 운영자(ADMIN)만.** 지금 구현 그대로.
2. **게시 승인을 거친다.** 검수 3곳 통과 → 완료 → 원장 「게시 승인」. → **2026-11-24 원장 지시로 바꿈: 시험 묶음이 있는 작업은 승인 없이 자동 게시**(§9).
3. **탑재는 둘 다.** 화면에서 바로 올리기 + 개발 PR(§5).
4. **타과목도 모두 같은 공정.** 국어·수학·사회·과학 자료와 시범 문항 9개도 요청 → 검수 3곳 → 게시 승인을 거친다. 공정에 예외를 두지 않는다.

5. **「새로 쓴 문항인가」를 필수 검수 항목으로 넣는다.** 검수 PASS는 다섯 항목(pastExamStyle·modelHtml·operatorRequest·logo·**original**)이 모두 확인돼야 한다. original = 지문·보기·선지·해설이 모두 새로 쓴 것이고 다른 회사의 지문·문항·그림이 없다.

## 8. 하지 못한 것 · 알아 둘 것

- 그림 문항·iBT·SAT·TOEIC·타과목의 화면 탑재(§5 — PR 방식으로 올린다).
- 기출·시험범위 **자료 파일** 올리기 연결(자료 위치를 글로 적는 방식).
- 현황판은 최근 100건만 보인다. 기존 MCP `f2r_pc_task_list`는 **오래된 것부터 50건**만 읽는다(`findTop50…OrderByCreatedAtAsc`) — 작업이 50건을 넘으면 새 작업이 그 목록에 안 보일 수 있다. 이번 범위 밖이라 고치지 않고 적어만 둔다.
- 화면은 자동으로 새로 고치지 않는다(「새로 고침」 단추).

## 9. 검증

- Java: `CoordPcTaskServiceTest`, `CoordFactoryServiceTest`, `FactoryExamUploaderTest`, `FactoryPlanTest`, `FactoryStageTest`, `CoordFactorySecurityWebMvcTest`, `McpCoordinatorToolsTest`, `WorksheetTemplateContractTest`.
- JS: `node --test src/test/js/factory_core.test.js`.
- 화면: 로컬 부팅 뒤 PC 1280px·휴대폰 375px에서 가로 넘침 없음 확인(캡처).

## 9. 자동 게시 (원장 지시 2026-11-24 「앞으로 승인 없이 모두 업로드」)

- **무엇이 자동인가**: 시험 묶음(`f2r_pc_task_exam_submit`)을 낸 작업이 **완료**되고, 마지막 묶음 제출 뒤 서로 다른 검수 세션 **PASS 3곳**이면 원장 승인 없이 학생 풀이 페이지에 올린다(`CoordFactoryService.autoPublish`). 검수 3곳 기준은 그대로다.
- **언제**: `FactoryAutoPublisher`가 1분마다 아직 올리지 않은 묶음(`exam_id IS NULL`)을 훑는다. 완료 뒤 1분 안에 올라가고, 배포 전에 완료돼 게시 대기에 있던 묶음도 배포 뒤 첫 훑기에 올라간다.
- **기록**: `coord_job_events` actor=PUBLISH, 사용자 `AUTO` — `APPROVED · 자동 게시(검수 3곳 통과)` → `PUBLISHED · /주소`. 현황판에는 「게시됨」으로 보인다.
- **다시 올리지 않는 경우**: 게시 기록(승인·게시·내림)이 한 번이라도 있는 작업. 원장이 「게시 내림」한 시험은 자동으로 다시 올라가지 않는다(다시 올리려면 「게시 승인」).
- **자동이 아닌 것**: 시험 묶음이 없는 작업(PDF만 만든 학습지·서술형 세트 등)은 올릴 것이 없어 지금처럼 현황판 「게시 승인」·「게시됨으로 표시」를 쓴다. 디지털 모의고사 탑재 여부는 여전히 요청의 `publishDigital`(원장 지시)로 정한다.
- **실패**: 시험 코드가 겹치는 등으로 올리지 못하면 로그에 남기고 그 작업은 서버를 다시 시작할 때까지 건너뛴다. 현황판 「게시 승인」을 누르면 이유가 보인다.
