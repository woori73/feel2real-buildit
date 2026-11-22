# 제작 공장 — Claude와 함께 문제 제작 (`/dashboard/factory`)

> 2026-11-22 대표 지시. 기준: [SITE_PLAN.md](SITE_PLAN.md) §3 「제작 공정(공장식)」. 브랜치 `feat/f2r-question-factory`(PR #1173 위에 쌓음).
> **스키마 변경 없음** — 마이그레이션 파일을 더하지 않았다(이 작업 몫으로 잡아 둔 V1145~는 쓰지 않음).

## 1. 무엇을 만들었나

| 구분 | 내용 |
|---|---|
| **그대로 쓴 것** | 제작 작업 대기열 `CoordPcTaskService`(`coord_jobs` kind=PC_TASK, 기록 `coord_job_events`), MCP 도구 `f2r_pc_task_request/list/claim/brief/review/finish/cancel`, 제작 지시서(brief)·briefHash, 화면 틀 `fragments/_prep_shell.html` + `worksheets.css` |
| **고친 것** | 검수 필요 수 2곳 → **3곳**(`REQUIRED_REVIEWS`). 요청에 「공장 정보」(과목·시험 구분, 쪽수 구간, 자료 위치, 문항 표시)를 받도록 `request` 확장. 수능·공인시험은 학교 없이 맡길 수 있게. 지시서·작업 지시문에 「새로 쓰기」 원칙과 공정 6단계 |
| **새로 더한 것** | MCP 도구 `f2r_pc_task_stage`(제작 세션의 단계 보고), 화면 `/dashboard/factory`, API `/api/admin/coord/factory/**`(`CoordFactoryService`·`CoordFactoryController`), 공정 판정 `FactoryStage`, 요청 검증 `FactoryPlan`, 왼쪽 메뉴 「제작 공장」 |
| **화면 탑재** | `FactoryExamUploader` — 게시 승인된 내신(영어) 5지선다 글 문항을 화면에서 바로 학생 풀이 페이지에 올림(§5, 대표 결정 2026-11-22) |
| **만들지 않은 것** | 새 대기열·새 테이블, Claude API 호출(전 환경 OFF 그대로), 자료 파일 올리기 |

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

자료 올리기: 파일을 이 화면에서 올리지 않는다. 기존 내신위키 올리기(`/problem-generator`)에 먼저 올리고 그 자료 번호·이름을 「자료 위치」에 적는다.

타과목(국어·수학·사회·과학)은 단원 트리·유형 체계를 따로 두지 않았다 — 기출과 시험범위 자료 위치, 쪽수 구간을 받아 그에 맞춰 만들게 한다(SITE_PLAN §2).

캡처: [captures/](captures/) `factory-pc-*.jpg`(1280px), `factory-mobile-*.jpg`(375px) — 1 현황판 · 2 자세히(검수 단계) · 3 자세히(게시) · 4 제작 요청. 로컬 DB에 넣은 가짜 작업으로 찍었다.

## 4. 권한 · CRUD 범위 · 삭제 정책

**권한**: 기존 대기열이 `ADMIN` + 본인(같은 계정) 작업만 다루므로 그대로 따랐다 — 대기열 작업은 원장 계정으로 연결한 Claude 세션(MCP)이 처리하고, 다른 계정이 올린 작업은 그 세션에 보이지 않는다. 화면 주소는 학습지와 같이 ADMIN·TUTOR가 열 수 있지만 TUTOR에게는 「원장 계정에서만 쓸 수 있습니다」 안내만 보인다(API는 403). 강사(TUTOR)도 요청을 올리게 하려면 대기열의 소유 단위를 계정에서 학원(사업체)으로 바꿔야 한다 — 대표 결정 사항(§7).

| 대상 | 만들기 | 보기 | 고치기 | 지우기 |
|---|---|---|---|---|
| 제작 요청(`coord_jobs`) | 화면 「제작 요청」 또는 MCP `f2r_pc_task_request` | 현황판(최근 100건) | **없음** — 요청(plan)은 바꿀 수 없다. 지시서와 briefHash가 요청에서 나오고, 검수 세션이 「원장님 요청」과 대조하기 때문. 바꾸려면 취소하고 다시 올린다 | **대기 중일 때 취소만**(상태 `cancelled`, 행은 남김). 가져간 뒤에는 취소 불가(세션이 `finish FAILED`로 끝냄). 물리 삭제 없음 |
| 검수·단계 기록(`coord_job_events`) | Claude 세션(MCP)만 | 자세히 창 | 없음 | 없음(감사 기록) |
| 게시 기록(actor `PUBLISH`) | 화면: 승인 → 게시됨 표시 | 자세히 창 | 「게시 내림」으로 되돌림(새 기록을 쌓음) | 없음 |

Create-only에 가까운 까닭: 대기열이 처음부터 「삭제·수정 API 없음, 감사 보존」으로 설계돼 있고(DOMAIN.md), 요청이 바뀌면 이미 받은 검수가 무엇을 기준으로 통과했는지 알 수 없게 된다.

## 5. 게시 — 어디까지 되나

흐름: **게시 대기 → (원장 「게시 승인」) → 탑재 대기 → 탑재 → 게시됨**, 그리고 「게시 내림」. 승인은 완료(`completed`) + 검수 통과 3곳인 작업만 된다(검수 2곳 기준으로 끝난 예전 작업은 승인 불가 — 화면에 까닭 표시).

탑재는 **두 가지 모두** 된다(대표 결정 2026-11-22).

| 방식 | 되는 자료 | 하는 법 |
|---|---|---|
| ① 화면에서 바로 올리기 | 내신(영어), **그림 없는 5지선다 글 문항**, 학생 풀이 페이지에 이미 있는 학교·학년(압구정중 1·2학년, 신구중 2학년) | 자세히 창 「게시」에서 게시용 파일(publish.json)을 고르고 「학생 페이지에 올리기」 → 바로 「게시됨」 |
| ② 개발 PR로 올린 뒤 표시 | 그림 문항, iBT·SAT·TOEIC, 타과목, 새 학교 | 아래 「PR 탑재 절차」 → 「게시됨으로 표시」 |

①이 내신(영어) 글 문항뿐인 까닭: 그림은 서버의 정적 파일이라 배포가 있어야 보이고, iBT·SAT·TOEIC은 듣기·말하기 등 문항 형식이 제각각이며, 타과목은 학생 풀이 화면(`feat/f2r-learn-hub`의 디지털 모의고사 틀)이 아직 없다. 타과목 화면이 생기면 같은 올리기에 구분만 더한다.

### 게시용 파일 (화면에서 바로 올리기)

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

### PR 탑재 절차 (그림 문항·다른 구분)

1. 원장이 현황판에서 「게시 승인」 → 작업이 「탑재 대기」로 보인다.
2. 개발 세션이 자세히 창의 「올린 자료」(저장 위치 `wikiRef`)에서 결과물을 받아 탑재 PR을 만든다.
   - 내신: `tp_exams`(kind=`NAESIN`, status=`PUBLISHED`) + `tp_questions`, 기존 V1121과 같은 형식의 마이그레이션(번호는 그때의 `origin/main` 최고 + 1, [WORKFLOW.md](../WORKFLOW.md) §4).
   - 디지털 모의고사 탑재는 원장이 지시한 작업(`publishDigital=true`)만.
   - iBT·SAT·TOEIC: TestPrep 세트 마이그레이션(V1125~V1133과 같은 형식).
   - 타과목: 학생 페이지(`feat/f2r-learn-hub`)의 「디지털 모의고사 틀」이 정해지면 그 형식.
3. PR 병합 → 원장 「배포 승인」 → 운영 반영.
4. 원장(또는 원장 계정으로 들어온 개발 세션)이 현황판에서 학생 페이지 주소(예 `/naesintest/school/singu-m2`)를 적고 「게시됨으로 표시」.

학생 페이지 쪽 게시 계약(`feat/f2r-learn-hub`의 `docs/learn-hub/README.md`)에는 2026-11-22 현재 게시 계약이 없다(링크 모음 설명만 있음). 타과목 화면과 계약이 정해지면 `FactoryExamUploader`에 그 구분을 더한다.

## 6. 검수 3곳 변경이 닿은 곳

- 코드: `CoordPcTaskService.REQUIRED_REVIEWS`(2 → 3), 검수 안내 문장(「n곳 더」), 지시서·작업 지시문의 「최소 3회」·「3곳」, `McpGateway` 도구 설명 4곳(request·claim·review·finish), `McpController` 서버 안내 문구.
- 문서: [API.md](../API.md)·[FLOWS.md](../FLOWS.md)·[DOMAIN.md](../DOMAIN.md), `tools/f2r-pc/CLAUDE.md`·`README.md`(학원 PC 작업 폴더 안내), [SITE_PLAN.md](SITE_PLAN.md).
- 테스트: `CoordPcTaskServiceTest`(2곳으로는 거절, 3곳 뒤 완료, FIX 뒤 다시 3곳), `McpCoordinatorToolsTest`.
- **운영에서 달라지는 점**: ① 지금 처리 중인 작업도 배포 뒤부터 3곳이 필요하다. ② 지시서 글이 바뀌어 briefHash가 달라진다 — 배포 전에 지시서를 받아 둔 검수 세션은 `f2r_pc_task_brief`를 다시 받아야 한다(이미 남긴 PASS는 그대로 센다). ③ 학원 PC·집 노트북에 복사해 둔 `tools/f2r-pc/CLAUDE.md`는 새 파일로 바꿔야 「세 번 검수 · 다섯 항목 확인」으로 움직인다(옛 파일로는 서버가 통과·완료를 거절하므로 틀린 결과가 나가지는 않지만 검수가 막힌다). 클라우드 세션은 서버가 주는 지시서를 읽으므로 바꿀 것이 없다.

## 7. 대표 결정 (2026-11-22)

1. **제작 요청은 운영자(ADMIN)만.** 지금 구현 그대로.
2. **게시 승인을 거친다.** 검수 3곳 통과 → 완료 → 원장 「게시 승인」. 지금 구현 그대로.
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
