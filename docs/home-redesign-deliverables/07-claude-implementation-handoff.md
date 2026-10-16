# 07. Claude 구현 인수인계 — BuildIt 홈 첫 화면 개편 (권장안 C)

> 지시문 §6 산출물 6종 + §7 "최종 권장안 1개와 Claude 구현 지시"
> **전제**: 이 문서는 **구현 지시서**이지만, **소유자 승인 전에는 구현을 시작하지 않는다.**
> 승인 전까지 Claude는 §0의 금지사항을 절대 위반하지 않는다.

---

## 0. 즉시 적용되는 제약 (지시문 §7 원문 준수)

```
금지 (어긴다면 중단하고 질문할 것):
- 운영 코드 수정 금지          → 이 단계에서는 산출물 검토만. 승인 후에만 코드를 건드린다.
- PR 병합 금지
- 운영 배포 금지
- AWS 및 운영 DB 변경 금지
- 기존 Builder, Wiki, Chart, TESTPREP, Ledger 기능 변경 금지
  → *.java(컨트롤러·WebMvcConfig·SecurityConfig·Program) 수정 대상 아님
  → application*.yaml, .github/workflows, AWS 리소스 수정 대상 아님
  → /builder, /wiki, /chart, /testprep, /problem-generator, /ledger 의
     라우트·권한·폼 submit 로직·엔드포인트를 바꾸지 않는다.
추측 금지:
- 이 문서에 없는 URL을 만들지 않는다. 404/302 미검증 경로를 링크하지 않는다.
  (/report /shorts /consult /agent /signin /lms/demo → 전부 404 실측, 사용 금지)
```

**변경 허용 파일 (승인 후)**
| 파일 | 변경 종류 |
|---|---|
| `src/main/resources/templates/public/landing.html` | 섹션 순서·마크업 배치, 헤더 라벨/href, `#biMenu` 행 추가, 신규 섹션 2개 |
| `src/main/resources/static/css/landing_v3.css` (또는 신규 `landing_home.css` + `<link>`) | 카드 그리드·칩 스트립 스타일. **기존 토큰 재사용** |
| **수정 금지** | `**/*.java`, `application*.yaml`, `.github/**`, `static/js/**`(폼 submit 로직), Wiki SPA, `/testprep`·`/chart`·`/ledger` 템플릿 |

---

## 1. 최종 권장안: **안 C 혼합형**

| 근거 | 실측 |
|---|---|
| 첫 화면 서비스 진입구가 0개 | PC 0–900px 인터랙티브 11개 중 서비스 링크 0 / 모바일 0–844px도 로고 제외 0 |
| 헤더 라벨 4개 중 2개가 다른 서비스로 이동 | `BUILD IT WIKI`→`/problem-generator`, `Buildit 모의고사`→`/wiki`(카드에선 "내신위키") |
| 서비스 카드가 3.8화면 아래 | `#services` y=3453 (PC) / y=4523 (모바일) |
| 모바일 13화면 | docH 11005 / 844 |
| 드로어가 서비스 4개만 노출 (전체 8종 중 4개 누락) | `#biMenu` = 링크 4 + 버튼 1 |

→ 안 C는 **첫 화면 900px에 카드 8장 + 빌더 배너 + 소식 패널**을 넣고, **기존 `#services`/`#hero`/`#nation`은 삭제하지 않고 아래로 재배치**한다. (상세: `05-buildit-home-wireframes.md` C-1/C-2, `04-buildit-home-redesign-spec.md`)

---

## 2. 구현 스텝 (순서대로, 각 스텝 후 검증)

### STEP 1 — 헤더 라벨/href 수정 (가장低风险, 최우선 효과)

`landing.html:24-45`의 `.nav-links` 4개와 `#biMenu` 4개를 아래 표대로 **라벨·href 동시 교체**.

```
| 라벨            | href                 |
| 내신위키        | /wiki                |
| 문제지 제작기   | /problem-generator   |
| CHART           | /chart               |
| TestPrep        | /testprep            |
```

- **제거**: `<a href="/home" class="btn outline nav-cta">F2R</a>` — 헤더·드로어 양쪽에서 삭제. `/home` 라우트 자체는 **건드리지 않는다**(다른 유입 경로 유지).
- **추가 금지**: `/builder`를 헤더에 넣지 않는다. (C-1 배너가 유일한 1순위 진입구)
- `문의하기` 버튼(`onclick="openContactModal()"`)은 PC 헤더 유지, **`<=860px`에서 CSS로 숨기고 드로어로 이동** (기존 내비 기준점이 900이 아니라 **860** — `landing_buildit.css:1473-1489`).
- `fragments/_account_menu :: accountMenuControls('/stdt_dashboard','학생 홈')` 호출부는 **그대로**. 비로그인 시 `.auth-actions`(로그인 버튼 + `/register` 링크)가 서버 HTML에 `hidden`으로 존재하고 JS가 토글한다 — 이 분기를 수정하지 말 것.

**검증**: `curl -s https://…/ | grep -o 'nav-cta[^<]*'` 로 4개 라벨 확인 + 각 href가 §5 링크표와 일치.

### STEP 2 — C-1 배너+소식 2분할 섹션 신설 (y=68–368)

`#biMenu` 바로 뒤, `#nation` **앞**에 삽입.

```html
<section class="home-top" id="homeTop">
  <div class="home-top-wrap">
    <!-- 좌: AI 홈페이지 제작 (기존 #hero의 form 마크업을 그대로 이관) -->
    <div class="home-top-ask">
      <p class="v3-brandline">BuildIt Web</p>
      <h2 class="home-top-h2">홈페이지, 한 문장으로 시작하세요</h2>
      <!-- ▼ 기존 form.v3-ask 마크업·data-buildit-*·maxlength=2000·aria-label 그대로 -->
      <form class="v3-ask" data-buildit-form>
        <input class="v3-ask-input" data-buildit-prompt maxlength="2000"
               placeholder="어떤 홈페이지를 만들고 싶으신가요?">
        <button type="submit" class="v3-btn v3-btn-primary" aria-label="만들기">만들기</button>
      </form>
      <ul class="v3-assure"><li>편집기 수정</li><li>미리보기 확인</li><li>승인 후에만 공개</li></ul>
      <a class="v3-btn v3-btn-ghost" href="/builder">빌더 소개 보기</a>
    </div>
    <!-- 우: 소식 -->
    <aside class="home-news" aria-label="주요 서비스 및 소식">
      <h3 class="home-news-h3">소식</h3>
      <div class="home-news-group"><span class="home-news-tag">새 서비스</span>
        <a href="/ledger">이지장부 공개</a>
        <a href="/designcraft">DesignCraft 소개</a></div>
      <div class="home-news-group"><span class="home-news-tag">업데이트</span>
        <a href="/testprep">iBT TestPrep 연습 시작</a></div>
      <div class="home-news-group"><span class="home-news-tag">자주 찾는 기능</span>
        <a href="/wiki">내신위키 최신 자료</a>
        <a href="/chart">CHART 전국 TOP100</a>
        <a href="/exam">문제지 제작</a></div>
    </aside>
  </div>
</section>
```

**주의**
- **`form.v3-ask`를 두 곳에 두지 말 것.** `#hero`에서 이관(移管) — `data-buildit-*` 훅이 중복되면 submit 스크립트가 첫 번째 요소만 처리하거나 중복 바인딩한다. 이관 후 `#hero`에는 폼 없이 상세 설명·샘플만 남긴다.
- 소식 패널은 **정적 HTML**. CMS·DB·API 연동 금지 (`/api/landing/videos/{slot}/stream` 등 기존 엔드포인트 사용 금지).
- `/home`(F2R) 링크는 소식 패널 하단에 `F2R 학원 찾기` 1행으로만 허용 — 헤더처럼 1순위 메뉴로 올리지 않는다.

### STEP 3 — B 영역 서비스 카드 그리드 신설 (y=368–872)

```html
<section class="svc-tiles" id="svcTiles">
  <div class="svc-tiles-head">
    <h2 class="v3-h2">BuildIt 서비스 8개</h2>
    <a class="svc-tiles-more" href="#services">전체 서비스 상세 →</a>
  </div>
  <ul class="svc-tiles-grid">
    <li><a class="svc-tile" href="/builder">
      <span class="svc-tile-icon" aria-hidden="true">▣</span>
      <span class="svc-tile-title">AI 홈페이지 제작</span>
      <span class="svc-tile-desc">한 문장으로 학원 홈페이지 초안을 만들고, 편집해서 내 주소로 게시합니다.</span>
      <span class="svc-tile-badge svc-badge-role">강사·원장용</span>
      <span class="svc-tile-cta">만들어 보기 ▶</span>
    </a></li>
    <!-- 2~8 반복 -->
  </ul>
</section>
```

**8장 확정 데이터 (문구는 `Program.java`·`#svc-*` 카드 원문 차용 — 새 문장 발명 금지)**

| # | title | desc | badge class / text | href |
|---|---|---|---|---|
| 1 | AI 홈페이지 제작 | 한 문장으로 학원 홈페이지 초안을 만들고, 편집해서 내 주소로 게시합니다. | `svc-badge-role` / 강사·원장용 | `/builder` |
| 2 | 내신 영어 문제지 제작기 | 학교·교재를 고르고 문제를 모아 내신 영어 문제지를 만들고 오답 노트로 복습합니다. | `svc-badge-login` / 로그인 후 이용 | `/problem-generator` |
| 3 | 내신위키 | 고1·고2·고3 영어 기출 원본, 시험 분석, 문항 질문·답변을 모은 자료 위키입니다. | `svc-badge-free` / 무료 열람 | `/wiki` |
| 4 | BUILD IT CHART | BuildIt이 공개 교육활동 근거로 찾아낸 동네 실력 있는 영어 선생님 랭킹입니다. | `svc-badge-free` / 무료 | `/chart` |
| 5 | iBT TestPrep | TOEFL iBT 읽기·듣기·말하기·쓰기를 연습하고 모의시험 채점·성적을 관리합니다. | `svc-badge-login` / 응시는 로그인 | `/testprep` |
| 6 | 이지장부 | 수입·지출 CSV를 넣으면 AI가 계정을 추천하고, 승인 후 복식분개·원장·시산표까지 정리합니다. | `svc-badge-role` / 원장용 | `/ledger` |
| 7 | DesignCraft | 원고만 넣으면 책자·보고서·학습자료를 편집해 PDF로 내보냅니다. | `svc-badge-soon` / **준비 중** | `/designcraft` |
| 8 | 입시상담 | 전형·지원 자격·일정·평가 기준을 확인하고 지원 전략을 검토합니다. | `svc-badge-soon` / **준비 중** | `/admissions/guide` |

**강제 규칙**
- `a.svc-tile`은 **실제 `<a href>`**. `<button onclick="location.href=…">` 금지 — 중클릭/⌘클릭 새 탭이 작동해야 한다.
- 카드 7·8은 `svc-badge-soon` + CTA 문구를 반드시 `소개 보기` / `가이드 보기`로. **"이용 가능" 표기 금지.** 추가 `aria-describedby="svcSoonNote"` + `<p id="svcSoonNote" class="v3-sr-only">아직 정식으로 오픈되지 않았습니다.</p>`.
- 카드 7·8과 `#soon` 섹션의 DesignCraft/입시상담 카드가 **동일 href**인지 확인 (02 §10: DesignCraft는 `곧 준비중`+실링크가 **의도된 현행 상태** — 커밋 `f8535b6f` #712).
- **카드화 금지**: `/report`, `/shorts`, `/consult`, `/agent`, `/easy`, `/lms/*`, `/newstart`, `/choonggi`, `/wemade`. (각각 404 / ADMIN 전용 / 협소 대상자 / 영상 슬롯 전용 — 03 §1-B·1-C)
- "AI Agent로 제작" 류 문구 금지 — `buildit.assistant.enabled` 기본 **false** (`AssistantFeatureFlag.java:26-35`).

### STEP 4 — CSS (기존 브레이크포인트 재사용)

현행 기준점: `min-width:900px`(`--v3-gutter:40px`, `--v3-sec:112px`), `1100px`, `1280px`, `max-width:720px`(`--v3-sec:48px`), `520px`. **신규 1024/768 같은 값 만들지 말 것.**

```css
.bi-page .svc-tiles-grid{display:grid;gap:20px;grid-template-columns:repeat(2,minmax(0,1fr))}
@media (min-width:900px){ .bi-page .svc-tiles-grid{grid-template-columns:repeat(3,minmax(0,1fr))} }
@media (min-width:1100px){ .bi-page .svc-tiles-grid{grid-template-columns:repeat(4,minmax(0,1fr))} }
.bi-page .svc-tile{display:flex;flex-direction:column;min-height:220px;padding:20px;
  border:1px solid var(--line);border-radius:16px;background:#fff}
.bi-page .home-top-wrap{display:grid;gap:20px}
@media (min-width:1100px){ .bi-page .home-top-wrap{grid-template-columns:620px 380px;justify-content:center} }
```

- 색상·보더·그림자는 `landing_v3.css`의 `--accent`, `--line`, `--v3-ink-2`, `#f7f8fc` 재사용. **하드코딩 신규 색 금지.**
- `:focus-visible` 3px outline 유지 (`landing_v3.css:58`).
- 모바일 칩 스트립(`<=860px`)은 기존 `ul.nation-regions`의 가로 스크롤 규칙을 재사용하되, **우측 페이드 그라디언트**를 `::after`로 추가해 스크롤 가능성을 시각화 (02 §11 P3 대응).

### STEP 5 — 기존 섹션 재배치 (삭제 없음)

```
변경 후 DOM 순서:
nav#biNav → #biMenu → section#homeTop(신설) → section#svcTiles(신설)
  → #nation → #agent → #hero(폼 이관 후 상세만) → #wiki → #why
  → #services → #soon → #chart-showcase → #faq → #cta → footer
```

- `#agent`(y=900, 146px)는 **삭제하지 말고** `#svcTiles` 타이틀 하위 1행 카피로 통합. (`assistant` 플래그 off이므로 전면에 세우지 않음)
- `#services`·`.svc-anchors`·`.svc-card` 4장·`id="svc-builder|svc-problem|svc-wiki|svc-testprep"` **전부 유지**. 상단에 `<a href="#svcTiles">첫 화면 서비스 목록으로</a>` 1행 추가.
- `#nation`·`[data-nation]`·`.nation-region`·`.nation-card` 인터랙션 유지. 위치만 폴드 밖.
- `#chart-showcase`, `#faq`, `#cta`(만들기→`/builder`), `footer.public-footer` 그대로.
- **커밋 `c02aa0a3`(#705)에서 확정된 "Agent → 문제지 제작 → 오픈 서비스 → 출시 예정 → CHART" 상대 순서를 이 개편에서도 보존**한다. (`#services` → `#soon` → `#chart-showcase` 순서 불변)

### STEP 6 — 모바일 드로어 재설계

```html
<div class="bi-menu" id="biMenu" hidden>
  <a href="/wiki">내신위키</a>
  <a href="/problem-generator">내신 영어 문제지 제작기</a>
  <a href="/chart">BUILD IT CHART</a>
  <a href="/testprep">iBT TestPrep</a>
  <a href="/builder">AI 홈페이지 제작</a>
  <a href="/ledger">이지장부</a>
  <a href="/designcraft">DesignCraft <span class="bi-menu-badge">준비 중</span></a>
  <a href="/admissions/guide">입시상담 <span class="bi-menu-badge">준비 중</span></a>
  <a href="/register?returnUrl=%2F" data-auth-signup>회원가입</a>
  <button type="button" onclick="openContactModal()">문의하기</button>
</div>
```

- 행 높이 64 → 56 (10행을 844px에 수용).
- **드로어에 로그인 버튼을 넣지 말 것.** `static/js/account_menu.js:20`이 `document.querySelector(AUTH_LOGIN)`로 **첫 번째 요소만** 바인딩하므로, 두 번째 `data-auth-login`은 클릭이 무효다. `static/js/**`는 수정 금지이므로 **회원가입 `<a>`만 드로어에 추가**하고, 로그인은 헤더 `.auth-actions`에 맡긴다.
- 헤더 `.auth-actions`(로그인·회원가입)는 **`<=860px`에서도 이미 노출**된다 — 실제 내비 브레이크포인트는 900이 아니라 **860** (`landing_buildit.css:1473-1489`), `.auth-actions`는 어떤 미디어 쿼리에도 안 숨음 (`account_menu.css:27`). 390px 렌더 확인: 로고(x20)·로그인(x156,65×39)·회원가입(x230,73×40)·햄버거(x311,44×44) — 02 §6, `buildit-mb-390-anon-header.png`. **따라서 인증 행 추가는 "결함 수정"이 아니라 "로그인 상태 사용자를 위한 재확인" 목적이다.**
- `/signin` 링크 생성 금지 — **404 재확인**. 로그인은 모달(`#login-modal`)뿐. `/login`은 200이지만 `/home`으로 처리되어 F2R 셸이 렌더되므로 **홈에서 쓰지 말 것**.

---

## 3. 수용 기준 (Acceptance Criteria) — 전부 충족해야 완료

| # | 기준 | 측정 방법 |
|---|---|---|
| AC1 | PC 1440×900에서 `.svc-tile` 8개 전부 `getBoundingClientRect().top < 900` | 브라우저 evaluate |
| AC2 | 모바일 390×844에서 칩 스트립 + `.svc-tile` 4개 노출 | 동일 |
| AC3 | 헤더 4개 라벨↔href: `내신위키`/`/wiki`, `문제지 제작기`/`/problem-generator`, `CHART`/`/chart`, `TestPrep`/`/testprep` | DOM dump |
| AC4 | 카드 8개 href가 §2 STEP3 표와 일치 + 전부 **200** (curl) | curl |
| AC5 | `#hero` 폼 submit이 개편 전과 동일 동작 | **소유자 수동 1회** (자동 생성 테스트 금지) |
| AC6 | 비로그인: 헤더에 로그인·회원가입 / 로그인: 👤 메뉴 — 두 상태 모두 정상 | 시드 시트 2종 |
| AC7 | DOM에 `#nation #agent #hero #wiki #why #services #soon #chart-showcase #faq #cta` + `.svc-card` 4장 + `.svc-anchors` 전부 존재 | id 전수 체크 |
| AC8 | 카드 7·8에 `준비 중` 배지 + CTA `소개 보기`/`가이드 보기`, "이용 가능" 문자열 없음 | grep |
| AC9 | `form.v3-ask` / `data-buildit-prompt`가 **문서당 1개** | grep count = 1 |
| AC10 | 문서에 `/report` `/shorts` `/consult` `/agent` `/signin` `/lms` href 없음 | grep |
| AC11 | `git diff --stat`에 `*.java`, `application*.yaml`, `.github/**`, `static/js/**` 변경 없음 | diff |
| AC12 | 모바일 드로어에 서비스 8행 + `회원가입` 행 존재, **`data-auth-login` 중복 없음** | DOM + grep count |

---

## 4. 소유자 승인 필요 항목 (구현 전에 답을 받아야 하는 것)

| # | 질문 | 왜 필요한가 |
|---|---|---|
| Q1 | 헤더에서 **`F2R` 제거**를 승인하는가? 제거 시 `/home` 유입이 줄어든다. | `/home`은 자체 5개 내비를 가진 두 번째 진입점(02 §5). IA 통합은 이번 범위 밖이라 **링크 강등**만 제안 |
| Q2 | 카드 8개 중 **`이지장부` `/ledger`를 첫 화면에 노출**하는가? (현행 홈에는 링크 0개) | 원장(TUTOR/ADMIN) 전용. 일반 방문자 눈에는 "내 서비스와 무관"으로 보일 수 있음 |
| Q3 | **`입시상담` `/admissions/guide`**를 "준비 중" 카드로 노출하는가? | 페이지는 200이지만 API 스위치 기본 OFF(커밋 #714). 내부 동작 **미검증**(03 §4) |
| Q4 | `#nation`(전국 무대, 현행 폴드 100% 점유)을 **폴드 밖으로 내리는** 것을 승인하는가? | 이 개편의 핵심 트레이드오프. 내리지 않으면 900px에 카드 8장이 안 들어간다 |
| Q5 | 소식 패널 6개 항목을 **정적 HTML**로 시작하는가? (운영 수동 편집) | CMS화하면 `*.java`·DB 변경이 필요해 §7 금지사항에 저촉 |
| Q6 | 문서 길이 압축(PC 8732 / MB 11005)을 **2단계로 분리**하는가? | 이번 개편으로 줄지 않는다(04 §6). 1차 범위를 첫 화면으로 한정해야 검증 가능 |
| Q7 | 배지 문구 4종(`무료`/`무료 열람`/`로그인 후 이용`/`응시는 로그인`/`강사·원장용`/`원장용`/`준비 중`)을 확정하는가? | `Program.java` requiredRoles와 실제 게이트를 실측으로 대조한 결과(02 §9). 문구가 다르면 오해 발생 |

---

## 5. 링크 계약 (전수 — 이 표 밖의 URL 사용 금지)

| 목적지 | 상태(비로그인) | 용도 |
|---|---|---|
| `/` | 200 | 로고 |
| `/wiki` | 200 공개 | 헤더·카드3·드로어 |
| `/problem-generator` | 200 공개 소개 | 헤더·카드2·드로어 |
| `/chart` | 200 공개 | 헤더·카드4·드로어·소식 |
| `/testprep` | 200 공개 소개 | 헤더·카드5·드로어·소식 |
| `/builder` | 200 공개 소개 | 카드1·C-1 보조CTA·드로어·`#cta` |
| `/ledger` | 200 공개 소개 | 카드6·소식·드로어 |
| `/designcraft` | 200 공개 소개 | 카드7·소식·드로어 |
| `/admissions/guide` | 200 | 카드8·드로어 |
| `/exam` | 200 | 소식 "문제지 제작" |
| `/home` | 200 | 소식 보조 1행 (Q1 승인 시) |
| `/register` (+`?returnUrl=%2F`) | 200 | 회원가입 |
| `/signin/reset/password` | (계정 메뉴 내) | 계정 찾기 |
| `/stdt_dashboard`, `/my/profile` | 로그인 시 | 계정 메뉴 |
| `#services`, `#svcTiles`, `#svc-builder`, `#svc-problem`, `#svc-wiki`, `#svc-testprep`, `#chart-showcase` | 앵커 | 내부 이동 |
| `https://open.kakao.com/o/sMf4ifRi`, `mailto:woorinaesin@naver.com`, `mailto:admin@feel2real.co.kr`, `tel:02-6324-1453`, `/privacy`, ftc.go.kr 사업자정보 | 200/외부 | 푸터·문의 (현행 유지) |
| **금지**: `/report` `/shorts` `/consult` `/agent` `/admin` `/signin` `/lms/demo` | **404 재확인(11:3x KST 전수)** | 생성 금지 |
| **금지**: `/login` | 200이으나 `/home`(F2R 셸)으로 처리 | 홈에서 로그인 링크로 쓰지 말 것 |
| **주의**: `/wiki/analysis` `/wiki/questions` `/conversation/dashboard` `/wemade` | 302 | 첫 화면 링크로 쓰지 말 것 (로그인 요구로 사용자 혼란) |

---

## 6. 산출물 파일 목록

| 파일 | 내용 |
|---|---|
| `01-megastudy-reference-analysis.md` | 메가스터디 PC/모바일 REA 실측 + 가져올 원칙 P1–P7 + 미검증 |
| `02-buildit-current-audit.md` | BuildIt 라이브 감사 + 배포 전후 + 라벨 불일치 + 익명 게이트 + 결함 우선순위 |
| `03-buildit-service-inventory.md` | 서비스 전수 7열 표 + 코드 라우트/권한 근거 + Report/Shorts 미존재 정정 |
| `04-buildit-home-redesign-spec.md` | 개편 설계 (A/B/C/D 영역, 버튼 동작, 브레이크포인트, 보존 조건) |
| `05-buildit-home-wireframes.md` | 안 A/B/C × PC 1440 + 모바일 390 와이어프레임 + 비교표 |
| `06-screen-captures.md` | 캡처 25종 매니페스트 (주석 6종 번호 대응표) + 재현 절차 |
| `07-claude-implementation-handoff.md` | **이 문서** — 권장안 C + 6스텝 + AC12 + 승인 질문 7 |
| `./captures/` | 실제 캡처 이미지 (주석 6종 포함) |
