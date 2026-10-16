# 03. BuildIt 서비스 전수 인벤토리

> 산출물 3/6 · 라이브 실측(Playwright + curl) + 코드(`woori73/feel2real-buildit` @ `69fe4bd3`) 대조 · 작성 2026-10-16

## 0. 운영 상태 정의 (지시문 §3 "운영 중 / 개발 중 / 준비 중을 명확히 구분")

| 표기 | 판정 근거 |
|---|---|
| **운영 중** | 실URL이 200을 반환하고, 비로그인 또는 로그인 상태에서 실제 기능 화면에 도달 가능 |
| **공개 소개 + 로그인 게이트** | 소개 페이지는 200, 기능 진입은 `Program.java` 요구역할 검증 후 로그인 요구 |
| **준비 중** | 홈 `#soon`에 카드는 있으나 `aria-disabled="true"` + 링크 없음, 또는 기능 라우트가 404 |
| **내부/비공개** | 라우트는 코드에 존재하나 일반 사용자 진입로 없음 (관리자/서브도메인 전용) |
| **미검증** | 존재는 확인했으나 실제 동작을 브라우저로 확인하지 못함 |

**중요**: 배포 후 비로그인 기준 `/builder` `/problem-generator` `/ledger` `/easy` `/designcraft` 가 모두 **200**이다. 이들은 401/302가 아니라 **Program.java 기반 "프로그램 소개 페이지"를 공개 렌더**한다. 따라서 첫 화면 배지 문구는 "로그인 필요"가 아니라 **"로그인 후 이용"** 이 정확하다. (02 문서 §9)

---

## 1. 전수 조사 결과 — 지시문이 요구한 7열 표

### 1-A. 첫 화면 후보 (우선순위)

| 서비스명 | 일반인용 한 줄 설명 | 주요 사용자 | 실제 URL | 로그인 필요 여부 | 운영 상태 | 첫 화면 노출 권장 |
|---|---|---|---|---|---|---|
| **AI 홈페이지 제작 (BuildIt Web / Builder)** | 한 문장으로 학원 홈페이지 초안을 만들고, 편집기에서 다듬어 나만의 주소로 게시합니다. | 학원 원장·강사 (TUTOR/ADMIN) | `/builder` (소개+진입) · `/builder/entry` | 소개는 무료 / 제작은 **TUTOR·ADMIN** | **운영 중** (공개 소개 + 로그인 게이트) | ✅ **필수** — 현재 첫 화면 노출 0개 |
| **내신 영어 문제지 제작기 (BuildIt 문제지)** | 학교·교재를 골라 문제를 모으고, 내신 영어 문제지를 만들어 오답 노트로 복습합니다. | 학생 + 강사 (STUDENT/TUTOR/ADMIN) | `/problem-generator` → 내부 `/problem-generator/wiki/*` (Vue SPA, Python:8000 프록시) | 소개 무료 / 제작 **로그인 필수** | **운영 중** | ✅ **필수** |
| **BuildIt Wiki (내신위키)** | 고1·고2·고3 영어 내신·모의고사 기출 원본, 시험 분석, 문항 질문·답변을 모은 자료 위키입니다. | 학생·학부모·강사 | `/wiki` (공개) · `/wiki/originals` · `/wiki/analysis` · `/wiki/questions` | `/wiki` 무료 / **하위 경로 로그인 필수**(302 실측) | **운영 중** (하이브리드) | ✅ **필수** — 단 라벨을 "내신위키"로 통일 |
| **BUILD IT CHART** | BuildIt이 공개 교육활동 근거로 찾아낸 동네 실력 있는 영어 선생님 랭킹입니다. | 학원 탐색자(학부모·학생), 강사 자기진단 | `/chart` · `/chart/educators/{id}` · `/chart#criteria` | **불필요** (완전 공개) | **운영 중** | ✅ **필수** |
| **iBT TestPrep** | TOEFL iBT 읽기·듣기·말하기·쓰기를 연습하고 모의시험 채점·성적·오답을 관리합니다. | 학생·유학 준비생 | `/testprep` · `/testprep/practice` · `/vocab` · `/scores` · `/learning` | 소개 무료 / 시험 응시·성적 **로그인 필수** | **운영 중** | ✅ 권장 |
| **이지장부 (Easy 장부 · 원장용)** | 학원 수입·지출 CSV를 넣으면 AI가 계정을 추천하고, 승인하면 복식분개·총계정원장·시산표까지 정리합니다. | 학원 원장 (TUTOR/ADMIN) | `/ledger` | 소개 무료 / 이용 **TUTOR·ADMIN** | **운영 중** (홈에는 "곧 준비중" 링크 없음) | ✅ 권장 — **현재 홈에 링크 0개** |
| **DesignCraft** | 원고만 넣으면 책자·보고서·학습자료를 편집해 PDF로 내보내는 출판 편집기입니다. | 강사·출판 담당 (TUTOR/ADMIN) | `/designcraft` · `/designcraft/editor` | 소개 무료 / 편집기 **TUTOR·ADMIN** | **준비 중 + 공개 소개 연결** (배지 `곧 준비중`, 실링크 존재 — 의도된 상태, 커밋 `f8535b6f` #712) | ⚠️ 2군(준비중 영역) 유지 |
| **입시상담 AI / 입시 가이드** | 공식 입시자료를 바탕으로 전형·지원 자격·일정·평가 기준을 안내하고 지원 전략을 검토합니다. | 학생·학부모 | `/admissions/guide` (200 실측) · `/consult` 404 | **미검증** | **준비 중** — 홈 카드는 `aria-disabled`, 링크 없음. 단 `/admissions/guide` 페이지는 존재·200. API 스위치 기본 OFF(커밋 `69fe4bd3` #714) | ⚠️ "준비 중"으로만. `/admissions/guide`를 안내 링크로 붙일지는 소유자 판단 |
| **Easy 회계·학생원장 (관리자판)** | 학원 회계와 학생 원장·수납을 관리자가 한곳에서 검토하는 운영 도구. | 관리자 (ADMIN) | `/easy` + 7개 서브 (`/transactions`, `/students`, `/payments`, `/card-review`, `/reviews`, `/evidence`, `/accounting`) | **ADMIN 전용** (`SecurityConfig:266`) | **운영 중(내부)** | ❌ **첫 화면 제외** — 일반인 서비스 아님 |

### 1-B. 첫 화면 후보에서 제외해야 하는 것

| 항목 | URL | 상태 | 제외 사유 |
|---|---|---|---|
| F2R (Feel to Real) | `/home` (+ `/academies`, `/analyzes`, `/academy-posts`, `/community`) | **운영 중** | 서비스가 아니라 **두 번째 진입점(경쟁 홈)**. 02 §5. 첫 화면에서 "F2R" 메뉴로 노출하면 IA가 갈라짐 → 카드가 아니라 **하위 영역 링크**로 강등 권장 |
| 새출발기금 (NewStart) | `/newstart` + 서브, `/admin/newstart/*` | **운영 중(협소 대상)** | 학원/교육 서비스가 아닌 특정 채무조정 대상자 도구. `NewStartMembersOnlyInterceptor` (`WebMvcConfig:31-33`) 적용 |
| 추기 (Choonggi) | `/choonggi/**` | **준비 중** | `choonggi/page.html:36`에 "COMING SOON" 문구 존재 |
| Wemade | `/wemade` | 302→`/wemade/login` | 별도 로그인 필요, 일반 목록 밖 |
| LMS (AcademyCraft / StudyCraft) | `/lms/{handle}` 계열 (`AcademyLmsPageController:37-73`) | **준비 중(시범)** | `/lms/demo` 404 실측. 대외 표시명 분리 커밋 `f2b430c0` #711. 시범 계정 유무 **미검증** |
| 학원별 공개 사이트 | `https://{handle}.feel2real.co.kr/` | **운영 중** | 서비스가 아니라 **빌더 산출물**. `/builder` 카드의 성과 사례로 언급만 (`SubdomainAcademyFilter:19-103`, `application-prd.yaml:121`) |
| 학생 홈 / 프로필 | `/stdt_dashboard`, `/my/profile`, `/signin/reset/password` | **운영 중(회원)** | 로그인 후 화면. 첫 화면에서는 **계정 메뉴**로만 노출 |
| 차트 관리자 | `/admin/chart` | 내부 | ADMIN 전용 |
| 대화형 리포트 | `/conversation/report` | 200이나 "곧 제공돼요" | **준비 중** (`student/conversation_report.html:32`) |
| 대화형 다른 탭 | `/conversation/{dashboard,chat,tutor-materials,homework,review}` | dashboard 302→`/stdt_dashboard` | 회원 전용 |

### 1-C. 지시문 후보 중 **실존하지 않는** 서비스 (정정)

| 지시문 명칭 | 실측 결과 | 결론 |
|---|---|---|
| **BuildIt Report (학생 성장 리포트)** | `/report` **404**. 독립 라우트 없음. `LandingPageController.java:16` "Build It Shorts/Report 섹션은 **관리형 영상 슬롯**을 쓴다" — `LandingVideoSlotKey.PARENT_REPORT_DEMO`일 뿐. `/conversation/report`은 "성장 추세 리포트 곧 제공돼요" | **독립 서비스 아님.** "준비 중"으로도 카드 생성 금지. 원하면 `/conversation/report` 기준 "준비 중" 1행 텍스트로만 |
| **BuildIt Shorts (AI 홍보영상)** | `/shorts` **404**. 코드상 `LandingVideoSlotKey.ACADEMY_SHORTS_DEMO` (`:25` `/video/landing/academy-shorts-demo.mp4`) = **홈에 띄울 데모 영상 파일**. AI 영상 생성 기능 없음 | **독립 서비스 아님.** 첫 화면 목록에서 제거 |
| **BuildIt Chart (영어교육자 소개)** | ✅ 실존 `/chart` | 유효 |
| **BuildIt 모의고사 및 TESTPREP** | ⚠️ 두 개로 나뉨: "모의고사"는 `/wiki`(내신위키)의 별칭이고, TESTPREP은 `/testprep`(TOEFL iBT)로 별개 서비스 | **분리 표기 필수** — 현재 헤더 라벨 "Buildit 모의고사"가 `/wiki`를 가리켜 혼동의 원인 (02 §4) |
| **Easy 장부/Ledger** | ✅ 실존, 2종: `/ledger`(원장 TUTOR·ADMIN), `/easy`(관리자) | 유효 |
| **입시상담 및 AI Agent** | `/consult`·`/agent` 404. `/admissions/guide` 200. 홈 `#agent`(y=900)는 **섹션 타이틀일 뿐 라우트 아님** | 부분 실존 |

---

## 2. 코드 근거 — 라우트 등록 위치 (구현 인수인계용)

| 경로 | 등록 코드 | 템플릿/타깃 |
|---|---|---|
| `/` | `LandingPageController:25` | `public/landing.html` (339줄) |
| `/home` | `WebMvcConfig:42-45` (`/login`도 여기 302) | F2R 셸 |
| `/eduholic` | `WebMvcConfig:40` | `/home` 301 |
| `/builder`, `/builder/entry` | `AcademyBuilderPageController:51,46` | Program `BUILDER` |
| `/chart`, `/chart/educators/{id}`, `/admin/chart` | `ChartPageController:18,28,49` | — |
| `/wiki`, `/wiki/originals`, `/wiki/analysis` | `WikiPageController:24-36` | Python 프록시 (`BuildItWikiProxyController:48-65`) |
| `/wiki/questions*` | `QuestionWikiPageController:20,25` | — |
| `/problem-generator` + 6개 서브 | `ProblemGeneratorPageController:13,19-30` → 302 `/problem-generator/wiki/index.html` | Vue SPA (`--base=/problem-generator/wiki/`) |
| `/testprep` + 7개 서브 | `TestPrepPageController:16-67` | — |
| `/ledger` | `WebMvcConfig:111` → `easy/tutor_agent` | Program `LEDGER` |
| `/tutor/easy` → `/ledger` | `WebMvcConfig:113` 301 | — |
| `/easy` + 7개 | `WebMvcConfig:101-108` | Program `EASY` |
| `/designcraft`, `/designcraft/editor` | `DesignCraftEditorController:28,38` | Program `DESIGNCRAFT` |
| `/newstart` + 서브 | `NewStartPageController` + `WebMvcConfig:31-33` 인터셉터 | — |
| `/choonggi/**`, `/choonggi/admin` | `ChoonggiPageController:41-103` | — |
| `/lms/{handle}` 계열 | `AcademyLmsPageController:37-73` | — |
| `/conversation/{dashboard,chat,tutor-materials,homework,report,review}` | 대화형 컨트롤러 | `student/conversation_*.html` |
| `/admissions/guide` | `AdmissionsGuidePageController:17` | — |
| `/exam` | `ExamPageController:12` | — |
| `/program/intro` | forward 전용 | `Program.java` |
| `/my/profile`, `/stdt_dashboard`, `/tutor_dashboard` | `WebMvcConfig:78-79` 등 | — |
| `/auth/naver/start` | 네이버 OAuth 시작 | — |

### 권한 정책 (`SecurityConfig.java`, first-match-wins / STATELESS / CSRF off)

| 라인 | 정책 |
|---|---|
| `:266` | `/easy/**` → `hasRole(ADMIN)` |
| `:264` | `/ledger/**` → `hasRole(ADMIN, TUTOR)` |
| `:316-322` | `/problem-generator` GET 공개, `/problem-generator/**` STUDENT·TUTOR·ADMIN |
| `:325-330` | `/testprep` 공개, `/testprep/**` 회원 |
| `:337, :368` | `/wiki` 정확히 공개, `/wiki/` 포함 하위 인증 |
| `:153-246` | 비로그인 GET 접근불가 → **302 `/?login=required&returnUrl=…`** |
| `:227-236` | `/ledger` `/easy` → Program 소개 페이지 forward |
| `:566` | catch-all permitAll |

`Program.java:13-83` = 공개 소개 페이지 + 요구역할 + 거부 메시지의 단일 출처. **첫 화면 카드의 "로그인 후 이용" 배지를 데이터화하려면 이 enum을 재사용**해야 한다(문구 중복 방지).

### 기능 플래그 / 스텁 (노출 금지 항목)

| 항목 | 근거 | 상태 |
|---|---|---|
| 대화형 빌더(AI Agent) | `AssistantFeatureFlag.java:26-35` `buildit.assistant.enabled` 기본 **false** | **off** — 첫 화면에서 "AI Agent로 제작" 같은 문구 금지 |
| 데모 기능 | `DemoFeatureDisabledException` | off |
| `/archived/**` | 별칭 필터 ADMIN 전용 | 내부 |
| NAVER_CLIENT_ID/SECRET | 코드 주석 "z지금은 이 값을 읽는 코드가 없다" | 미배선 |

---

## 3. 첫 화면 노출 권장 최종 목록 (8개 → 카드 8장)

| 슬롯 | 카드 제목 | 라벨 통일 근거 | CTA 목적지 | 배지 |
|---|---|---|---|---|
| 1 | **AI 홈페이지 제작** | `Program.BUILDER.title` | `/builder` | 로그인 후 이용 (강사·관리자) |
| 2 | **내신 영어 문제지 제작기** | `Program.PROBLEM_GENERATOR.title` | `/problem-generator` | 로그인 후 이용 |
| 3 | **내신위키 (모의고사·기출 분석)** | `/wiki` 타이틀 + 카드 `#svc-wiki` 명칭 절충 | `/wiki` | 일부 로그인 (자료·문항 열람 시) |
| 4 | **BUILD IT CHART** | `/chart` 타이틀 | `/chart` | 무료 |
| 5 | **iBT TestPrep** | `#svc-testprep` 카드 | `/testprep` | 일부 로그인 (응시·성적) |
| 6 | **이지장부** | `Program.LEDGER.title` | `/ledger` | 로그인 후 이용 (원장) |
| 7 | **DesignCraft** | `Program.DESIGNCRAFT.title` | `/designcraft` | 준비 중 (링크 유지 — 현행 의도) |
| 8 | **입시상담** | `#soon` 카드명 | `/admissions/guide` | 준비 중 |

**제외 확정**: Easy 회계(`/easy`, ADMIN), NewStart, Choonggi, Wemade, LMS, `/home`(F2R), Report, Shorts.
→ **F2R은 카드가 아니라** 헤더 보조 링크 또는 D영역 "기존 콘텐츠" 섹션의 내부 링크로 배치 (02 §5의 두 홈 문제 악화 방지).

## 4. 미검증 항목

| 항목 | 상태 |
|---|---|
| `/builder` 로그인 후 실제 제작 흐름·완료율 | **미검증** (운영 데이터 생성 회피) |
| `/problem-generator` SPA 내부 화면 | **미검증** — 302 대상(`/problem-generator/wiki/index.html`) 미추적 |
| `/wiki/analysis`, `/wiki/questions` 내부 | **미검증** (비로그인 302만 확인) |
| LMS 시범 운영 여부·계정 | **미검증** — `/lms/demo` 404, 코드 라우트 존재만 확인 |
| `/admissions/guide` 내부 기능 동작 여부 | **미검증** — 200 확인, API 스위치 기본 OFF(커밋 #714)이 화면에 어떻게 반영되는지 미확인 |
| `/newstart`, `/choonggi`, `/wemade` 실제 내용 | **미검증** — 상태 코드만 확인 |
| `/chart` 랭킹 산출 로직·갱신 주기 | **미검증** (AI 디스커버리 프롬프트 코드만 확인) |
| 역할별(STUDENT/TUTOR/ADMIN) 헤더·카드 노출 차이 | **미검증** — STUDENT + 익명 2종만 실측 |
| `{handle}.feel2real.co.kr` 실도메인 접속 | **미검증** |
