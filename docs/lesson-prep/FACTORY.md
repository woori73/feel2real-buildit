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
| **게시 연결(추가)** | 시험 묶음 제출·읽기 MCP(`f2r_pc_task_exam_submit`·`f2r_pc_task_exam_read`), 게시 승인 때 `tp_exams`·`tp_questions` 탑재, 내림 = ARCHIVED (`FactoryExamPublisher`·`FactoryExamContract`, 표 `coord_factory_exam_packages` V1139) — §5 |

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

## 5. 게시 — 학생 풀이 페이지에 올리기 (2026-11-22 연결)

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

- **묶음 검증**(`FactoryExamContract.parse`, 틀린 곳을 문항 번호와 함께 한 번에 알림): 코드 머리·`-R` 두 자리 회차·40자, 제목 1~150자, 범위 글 255자, 시간 60~10800초, 1~60문항, 번호 1부터 차례, `NAESIN_MC5`·`NAESIN_MC5_MULTI`, 선택지 5개, 정답 A~E(모두 고르기는 `B,D`), 배점 1~20, 해설 첫 줄 「정답 …」, 그림은 사이트에 이미 있는 `[[img:/naesin/….webp]]`만, 이미 쓰이는 시험 코드 거절.
- **검수와 판**: 묶음을 낼 때 기록(`STAGE · EXAM · 코드 · N문항 · 판`)을 남긴다. 게시 승인은 **마지막 제출 뒤**의 서로 다른 검수 PASS가 3곳일 때만 된다 — 묶음을 다시 내면 검수를 다시 받아야 한다. 판(`package_hash`)은 자세히 창과 `f2r_pc_task_exam_read`에 보인다.
- **저장**: `coord_factory_exam_packages`(V1139, 작업마다 한 줄). 게시하면 `exam_id`·`published_at`이 채워진다.
- **수능·iBT·SAT·TOEIC**은 세트 형식(듣기·지문 묶음·점수 환산)이 달라 이 경로로 올리지 않는다. 지금처럼 「게시 승인」 → 탑재 대기(APPROVED) → 세트 마이그레이션 PR → 「게시됨으로 표시」.
- **한계**: 새 그림을 올리는 길이 없다(그림 문항은 먼저 저장소에 그림을 넣는 PR이 필요). 풀이 화면이 수식을 그리지 않으므로 수식·도표는 그림으로. 국어·수학·사회·과학 탭(`/learn?tab=…`)은 PR #1176이 병합돼야 보인다 — 그 전에는 재원생 앱 배정 목록에만 나온다.

## 6. 검수 3곳 변경이 닿은 곳

- 코드: `CoordPcTaskService.REQUIRED_REVIEWS`(2 → 3), 검수 안내 문장(「n곳 더」), 지시서·작업 지시문의 「최소 3회」·「3곳」, `McpGateway` 도구 설명 4곳(request·claim·review·finish), `McpController` 서버 안내 문구.
- 문서: [API.md](../API.md)·[FLOWS.md](../FLOWS.md)·[DOMAIN.md](../DOMAIN.md), `tools/f2r-pc/CLAUDE.md`·`README.md`(학원 PC 작업 폴더 안내), [SITE_PLAN.md](SITE_PLAN.md).
- 테스트: `CoordPcTaskServiceTest`(2곳으로는 거절, 3곳 뒤 완료, FIX 뒤 다시 3곳), `McpCoordinatorToolsTest`.
- **운영에서 달라지는 점**: ① 지금 처리 중인 작업도 배포 뒤부터 3곳이 필요하다. ② 지시서 글이 바뀌어 briefHash가 달라진다 — 배포 전에 지시서를 받아 둔 검수 세션은 `f2r_pc_task_brief`를 다시 받아야 한다(이미 남긴 PASS는 그대로 센다). ③ 학원 PC·집 노트북에 복사해 둔 `tools/f2r-pc/CLAUDE.md`는 새 파일로 바꿔야 「세 번 검수」로 움직인다(서버는 2곳으로는 완료를 거절하므로 틀린 결과가 나가지는 않는다).

## 7. 대표가 정할 것

1. **강사(TUTOR)도 제작 요청을 올리게 할지.** 지금은 원장(ADMIN) 계정만. 열려면 대기열 소유 단위를 학원으로 바꾸는 작업이 따로 필요하다.
2. **게시 승인 단계.** 지금: 검수 3곳 통과 → 세션이 원장 동의를 받아 저장·완료 → 원장이 화면에서 「게시 승인」 한 번 더(SITE_PLAN §5-3의 기본값 「거친다」). 완료와 동시에 게시 대기열로 바로 넘길지.
3. **실제 탑재 방식.** 지금처럼 개발 세션의 마이그레이션 PR로 올릴지, 화면에서 바로 올리는 기능(문항 형식 검증 포함)을 만들지 — 학생 페이지 게시 계약과 함께 정한다.
4. **타과목 시범 문항 9개**를 이 공정(검수 3곳)에 태울지(SITE_PLAN §5-1).
5. 검수 체크 항목에 「새로 쓴 문항인가(다른 회사 자료를 옮기지 않았는가)」를 **필수 다섯째 항목**으로 넣을지. 지금은 지시서와 검수 안내 문장에만 있고, PASS 필수 체크는 기존 네 항목 그대로다(MCP 검수 계약을 바꾸는 일이라 남겨 둠).

## 8. 하지 못한 것 · 알아 둘 것

- 문항을 tp_exams에 넣는 자동 탑재(§5).
- 파일 올리기 연결(자료 위치를 글로 적는 방식).
- 현황판은 최근 100건만 보인다. 기존 MCP `f2r_pc_task_list`는 **오래된 것부터 50건**만 읽는다(`findTop50…OrderByCreatedAtAsc`) — 작업이 50건을 넘으면 새 작업이 그 목록에 안 보일 수 있다. 이번 범위 밖이라 고치지 않고 적어만 둔다.
- 화면은 자동으로 새로 고치지 않는다(「새로 고침」 단추).

## 9. 검증

- Java: `CoordPcTaskServiceTest`, `CoordFactoryServiceTest`, `FactoryPlanTest`, `FactoryStageTest`, `CoordFactorySecurityWebMvcTest`, `McpCoordinatorToolsTest`, `WorksheetTemplateContractTest`.
- JS: `node --test src/test/js/factory_core.test.js`.
- 화면: 로컬 부팅 뒤 PC 1280px·휴대폰 375px에서 가로 넘침 없음 확인(캡처).
