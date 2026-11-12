# BuildIt 홈페이지 v3 — Muse 구현 핸드오프

> 작성: Claude (디자인 담당) · 대상: Muse (홈페이지 UI 구현 담당)
> 상태: **확정 v1.0** — 사용자가 v3 디자인 방향을 채택했고 아래 §10의 결정을 확정했다. 구현 착수는 사용자 지시 후 Muse가 한다.
> 이 문서와 시안은 `docs/design/homepage-v3/`에 보존한다(문서 PR). **Claude는 운영 홈페이지 코드를 수정·배포하지 않고, PR 병합은 별도 승인 후 진행한다.**
> 기준 코드: `origin/main` (e175bf45 기준 조사). PR #651(샘플 카드 제거)은 CI 통과·병합 준비 상태이며 **미병합**이다.

---

## 0. 먼저 읽을 것 (요약)

1. **구현 기준은 "검증된 HTML v3"** 이다. Figma는 디자인 토큰·컴포넌트·레이아웃 참조용이고, 동작·반응형은 HTML을 따른다.
2. 그러나 **HTML v3도 "정적 시안"** 이다. Spring/Thymeleaf·로그인·권한·실제 API와 연결하지 않았다. 아래 §3 "v3 시안의 정정 사항"을 먼저 반영해야 한다. (링크 3종과 문구 1건이 실제 동작과 다르다.)
3. 기존 운영 홈페이지의 **시작하기 게이트(로그인·역할 분기), Chart 실데이터 위젯, 영상 슬롯, 서비스 링크, 소개영상·슬로건**은 그대로 유지한다. 시안의 "예시" 화면으로 운영 데이터를 대체하지 않는다.
4. 기존 **계약 테스트가 현재 랜딩 구조를 고정**하고 있다(§6). 서비스 노출 순서 변경은 사용자가 확정했지만(§10-1) **테스트 변경은 "별도 검토 대상"** 이다. 임의로 고치거나 완화하지 말고, 변경이 필요한 항목을 PR 본문에 사유와 함께 올려 승인을 받는다.
5. **모바일 Chart·콘텐츠(Wiki/Report/Shorts)·FAQ 구간은 검수가 끝나지 않았다.** 반드시 브라우저로 직접 검증한다(§9).
6. 한 번에 한 사람만 `landing.html` / `landing_buildit.css` / `landing_buildit.js`를 수정한다(§11).

---

## 1. 참조 자료 위치

### 1-1. Figma (편집 가능, 읽기 전용 참조)

| 대상 | 링크 |
|---|---|
| 파일 | https://www.figma.com/design/vfoT1AqngzCNxxyFCyspG2 |
| PC 1440 프레임 | https://www.figma.com/design/vfoT1AqngzCNxxyFCyspG2?node-id=5-2 |
| Mobile 390 프레임 | https://www.figma.com/design/vfoT1AqngzCNxxyFCyspG2?node-id=9-20 |
| 서비스 상태 보드 (PC) | …?node-id=7-287 |
| 서비스 상태 보드 (Mobile) | …?node-id=10-303 |
| Button 컴포넌트 세트 | …?node-id=4-12 |
| Badge 컴포넌트 세트 | …?node-id=4-22 |

- 페이지: `PC 1440` / `Mobile 390` / `Tokens`
- Figma 변수 컬렉션 `BuildIt/Color` (23개), 텍스트 스타일 `BuildIt/*` (15개)
- **제약:** Figma 무료 플랜(Starter) MCP 호출 한도에 도달해 현재 Figma 쪽 추가 수정·재점검이 불가능하다. 사용자가 유료 전환을 보류했다. Figma 파일은 "마지막으로 만든 상태"이며, **Figma 내용 일부는 렌더링 확인을 못 했다**(§8).
- 좌석이 View라 편집 권한이 없을 수 있다. 열람이 안 되면 사용자에게 요청한다.

### 1-2. HTML v3 시안 (동작·반응형 기준)

이번 세션 샌드박스 경로(세션 종료 후 사라질 수 있음):

```
docs/design/homepage-v3/source/
  index.html            # 마크업
  tokens.css            # 디자인 토큰(CSS 변수)
  home.css              # 스타일
  home.js               # 인터랙션(헤더 스크롤, 등장, 탭, 칩, placeholder 타자)
  verify.playwright.js  # 390/1440 렌더·가로넘침·axe 검증 스크립트
docs/design/homepage-v3/screens/   # 비교 이미지, PC/모바일 캡처
```

**보존 위치(확정):** 위 시안 원본·스크린샷·이 문서는 repo의 `docs/design/homepage-v3/`에 보존한다(문서 PR). 해당 폴더 구성:

```
docs/design/homepage-v3/
  README.md          # 폴더 안내·Figma 링크·검증 요약
  HANDOFF.md         # 이 문서
  source/            # index.html, tokens.css, home.css, home.js, verify.playwright.js
  screens/           # 비교 이미지, PC/모바일 캡처
```

`docs/` 아래이므로 정적 리소스로 서빙되지 않고, 운영 코드에 영향을 주지 않는다. 시안 파일(`source/`)은 **참조용**이며 `src/main/resources`로 복사해 그대로 쓰지 않는다(§3 정정 사항 반영 필수).

### 1-3. 구현 대상 기존 파일 (모두 Muse 단독 수정)

```
src/main/resources/templates/public/landing.html
src/main/resources/static/css/landing_buildit.css      (cache-bust ?v= 갱신 필요)
src/main/resources/static/js/landing_buildit.js        (시작하기 게이트 — 유지)
src/main/resources/static/js/landing_chart.js          (Chart 실데이터 — 유지)
src/main/java/.../domain/landing/controller/LandingPageController.java
src/main/resources/templates/fragments/_account_menu.html   (로그인 모달·회원가입 링크)
```

---

## 2. 검증 현황 — 무엇이 검증됐고 무엇이 아닌가

| 항목 | HTML v3 | Figma |
|---|---|---|
| 390 / 1440 렌더링 | ✅ Playwright 확인 | ✅ 스크린샷 확인(아래 제외) |
| 가로 넘침 | ✅ 요소 단위 검사 통과 | – |
| axe-core | ✅ 위반 0 | – |
| Lighthouse(정적 파일, 샌드박스) | 모바일 성능 100·접근성 100 / 데스크톱 성능 88·접근성 100 | – |
| 모바일 **Chart** 구간 | ⚠ 수정 후 1회 확대 확인, 정식 검수 아님 | ⚠ 축소본으로만 봄 |
| 모바일 **콘텐츠(Wiki/Report/Shorts)** | ⚠ 축소본 위주 | ⚠ 축소본으로만 봄 |
| 모바일 **FAQ** | ⚠ 축소본 위주 | ⚠ 축소본으로만 봄 |
| Figma 구조 점검(노드 수·변수 바인딩 수·이미지 fill 0) | – | ❌ **호출 한도로 실행 못 함** |
| Spring 통합·로그인·권한·실데이터 | ❌ 미연결 | – |
| 태블릿(768~1024) | ❌ 미검증 | – |
| 실제 한국어 폰트(Pretendard) 렌더 | ❌ 샌드박스에서 CDN 차단 → 시스템 폰트로 검증 | Noto Sans KR |

---

## 3. v3 시안의 정정 사항 (구현 전 반드시 반영)

시안을 만들 때 실제 라우트를 확인하지 않아 생긴 오류다. 코드에서 확인한 사실로 정정한다.

| # | 시안 | 실제 | 구현 지침 |
|---|---|---|---|
| 1 | 히어로 `<form action="/builder/entry" method="get">` | 기존 랜딩은 `form[data-buildit-form]` + `input[data-buildit-prompt]` + `[data-buildit-example]` + `[data-buildit-start]` 훅을 `landing_buildit.js`가 처리한다. 로그인 여부·역할(TUTOR/ADMIN/학생)에 따라 `/builder`, 로그인 모달, 강사 가입 게이트로 분기하고 입력문은 `sessionStorage`에 보관한다. | **시안의 form action을 쓰지 않는다.** 기존 data 속성 훅을 그대로 새 마크업에 붙인다. `landing_buildit_start.test.js`(11개)가 이 동작을 고정한다. |
| 2 | 헤더 `href="/login"` | `/login`은 `/home`으로 302 리다이렉트된다(`WebMvcConfig`). 로그인은 **모달**(`f2r:login`, `openLoginModal`)이다. | 헤더 우측은 기존 `fragments/_account_menu :: accountMenuControls(...)`를 그대로 사용한다. 시안의 "로그인/시작하기" 버튼은 **스타일만** 차용한다. |
| 3 | 헤더 `href="/register"` | `/register`는 존재한다(`auth/register`). 기존 `[data-auth-signup]` 링크가 `returnUrl`을 붙인다. | 하드코딩하지 말고 `_account_menu`의 회원가입 링크를 쓴다. |
| 4 | 콘텐츠 카드 `/report-demo`, `/shorts-demo` | **이 라우트는 존재하지 않는다.** (`parent-report-demo.mp4`, `academy-shorts-demo.mp4` 파일명을 오인한 것) | Report/Shorts 카드는 랜딩 내 앵커(`#report`, `#shorts`)로 연결하거나, 기존 **영상 슬롯 모델**(`parentReportVideo`, `academyShortsVideo`: `visible`/`sourceUrl`)을 그대로 쓴다. `visible=false`면 기존처럼 "준비 중" 표시. |
| 5 | 콘텐츠 카드 "내신 Wiki → `/wiki`" | `/wiki`는 공개 소개 화면이고, `/wiki/**`는 **로그인 필요**다. 기존 내비의 `/wiki`는 "Buildit 모의고사", `/problem-generator`가 "BUILD IT WIKI"(STUDENT/TUTOR/ADMIN 로그인 필요)다. | 내비 4개 라벨·링크는 **기존 그대로 유지**(§10-2). 콘텐츠 카드의 "Wiki"는 `BUILD IT WIKI`(`/problem-generator`, 로그인 필요) 의미로 쓰고 로그인 필요를 표기한다. |
| 6 | FAQ "Chart와 Wiki는 로그인 없이 둘러볼 수 있습니다." | `/chart`, `/chart/educators/**`, `/wiki` 소개는 공개. 그러나 Wiki **자료·문항·Thread는 로그인 필요**, `/problem-generator/**`는 로그인 필요. | 문구를 "Chart는 로그인 없이, Wiki는 소개만 공개되고 자료는 회원 전용입니다."로 정정한다. |
| 7 | 서비스 탐색기 "Easy AI 장부 — 로그인하고 확인" 버튼 | `/easy/**`는 **ADMIN 전용**(`hasRole("ADMIN")`). 일반 사용자가 누르면 접근 불가. | **일반 사용자를 `/easy`로 보내지 않는다.** 버튼은 "도입 문의"로 바꾸고 `openContactModal()`에 연결한다(§7-2). |
| 8 | 시안 `<title>` | 기존 계약 테스트가 `<title>Build It | 원장과 강사가 브랜드가 되는 시대</title>`와 로고 마크업을 고정한다. | 기존 title·로고 마크업 유지. |
| 9 | 시안 Chart 창의 "교육자 A/B/C", 근거 행 | 현재 랜딩은 **실제 Chart 공개본**(`/api/chart/publication/current`)으로 TOP5를 그린다. | §7 참고. 실데이터 위젯을 유지한다. |

---

## 4. 디자인 토큰 → CSS 변수

시안 `tokens.css` 값이 기준이다. Figma 변수 `BuildIt/Color`와 1:1이며, Figma에만 추가된 3개는 HTML에서 하드코딩돼 있으므로 **변수로 승격**한다.

### 4-1. 색상

| Figma 변수 | CSS 변수 | 값 | 용도 |
|---|---|---|---|
| color/bg | `--bg` | #F7F8FC | 페이지 배경 |
| color/surface | `--surface` | #FFFFFF | 카드 |
| color/surface-2 | `--surface-2` | #EFF1F8 | 보조 면 |
| color/line | `--line` | #E2E6F1 | 테두리 |
| color/ink · ink-2 · ink-3 | `--ink` `--ink-2` `--ink-3` | #0A0F26 · #394061 · #545C7E | 텍스트 (ink-3은 대비 4.5:1 확보용으로 조정한 값 — **밝게 바꾸지 말 것**) |
| color/brand · brand-600 · brand-50 | `--brand` `--brand-600` `--brand-50` | #2F4BFF · #2238D6 · #EAEEFF | 주조색 |
| color/violet | `--violet` | #7A5CFF | 그라디언트·3단계 |
| color/mint · mint-50 | `--mint` `--mint-50` | #12C79B · #DDF7EF | 상태·체크 |
| color/amber · amber-50 | `--amber` `--amber-50` | #F5A524 · #FFF2D9 | 베타·수정 중 |
| color/night · night-2 · night-3 | `--night` `--night-2` `--night-3` | #080D24 · #121A3F · #1C2552 | 다크 면 |
| color/on-night · on-night-2 | `--on-night` `--on-night-2` | #E6E9FF · #9EA8DC | 다크 위 텍스트 |
| **color/mint-ink** (신규) | `--mint-ink` | #066D52 | 민트 배지 텍스트 (HTML에 하드코딩 → 변수화) |
| **color/amber-ink** (신규) | `--amber-ink` | #7A4F00 | 앰버 배지 텍스트 (동일) |
| **color/night-line** (신규) | `--night-line` | #232C5C | 다크 창 테두리 (동일) |

기존 `landing_buildit.css`의 변수(`--bg`, `--bg-soft` 등)와 **이름 충돌**이 있을 수 있다. 신규 토큰은 `--bi3-*` 접두사로 두거나, 충돌 확인 후 병합한다(Muse 판단, 기존 페이지 회귀 금지).

### 4-2. 타이포그래피

- 폰트: **Pretendard Variable**(`cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/...`) → fallback `-apple-system, "Noto Sans KR", "Noto Sans CJK KR", "Apple SD Gothic Neo", "Malgun Gothic", sans-serif`. 운영에서 외부 CDN 사용이 정책상 가능한지 **먼저 확인**한다(기존 랜딩의 폰트 로딩 방식 우선). 불가하면 self-host.
- `word-break: keep-all` 필수(한글 단어 단위 줄바꿈).

| 역할 | PC | 모바일 | weight | line-height | letter-spacing | Figma 스타일 |
|---|---|---|---|---|---|---|
| Display(h1) | 76px | 39px (`clamp(38px,10vw,76px)`) | 800 | 1.08~1.1 | -0.05em | pc/Display · m/Display |
| H2 | 46px | 28px (`clamp(28px,7vw,46px)`) | 800 | 1.15 | -0.045em | pc/H2 · m/H2 |
| Pane title | 32px | 24px | 800 | 1.2 | -0.04em | Pane/Title · Pane/Title-m |
| H3 | 22px | 20px | 800 | 1.3 | -0.03em | H3 |
| Lead | 20px | 17px | 400 | 1.6 | 0 | pc/Lead · m/Lead |
| Body | 16px | 16px | 400/700 | 1.65 | 0 | Body · Body/Strong |
| Small | 14~15px | 14~15px | 400/700 | 1.5~1.6 | 0 | Small · Small/Strong |
| Caption | 12px | 12px | 700 | 1.4 | 0 | Caption |

> ⚠ **Figma와 HTML의 차이:** Figma의 `Small`은 14px, HTML의 카드/단계 본문은 15px(`.j p`, `.tool p`, `.faq p`). HTML 값을 따른다.

### 4-3. 간격·모서리·그림자·모션

- 8pt 스케일: `--s1:8 --s2:16 --s3:24 --s4:32 --s5:48 --s6:72 --s7:112` (px)
- 컨테이너: `max-width:1200px`, 좌우 gutter **PC 40 / 모바일 20** → 콘텐츠 폭 PC 1120, 모바일 350
- 섹션 세로 패딩: **PC 112 / 모바일 72**
- 반경: sm 10 · md 16 · lg 28 · pill 999
- 그림자: `--sh-1: 0 1px 2px rgba(10,15,38,.05), 0 10px 30px rgba(10,15,38,.06)`, `--sh-2: 0 2px 6px rgba(10,15,38,.08), 0 40px 80px rgba(47,75,255,.20)`
- 모션: `--ease: cubic-bezier(.2,.7,.2,1)`, 등장 .7s, 호버 .25s, **`prefers-reduced-motion`에서 전부 해제**

---

## 5. 컴포넌트·레이아웃 지침

### 5-1. 컴포넌트

| 컴포넌트 | Figma | 규격 | 변형 |
|---|---|---|---|
| **Button** | 세트 4:12 (`Label` 텍스트 속성) | 높이 52px, 좌우 26px, pill, Bold 16 | Primary(brand + 그림자) · Ghost(surface + line 테두리) · Dark(ink) · White(surface, brand 글자 — 다크 면 위) · Disabled(surface-2, ink-3, `aria-disabled`, 클릭 불가) |
| **Badge** | 세트 4:22 (`Label` 텍스트 속성) | pill, 좌우 10·상하 4, Caption | Live(mint-50, 점 pulse) · Beta(amber-50) · Soon(surface-2) · Example(night-2) |
| 서비스 탭 | 보드 7-287 | 높이 48, 상태 점 8px | 선택=ink 배경, ARIA `role=tablist/tab/tabpanel`, ←→ 키 이동 |
| 서비스 패널 | 동일 | r28, 패딩 PC 48/모바일 24, 좌 복사·우 화면 목업 | 상태별 5종 |
| 앱 창(목업) | 4개 | night, r28, 상단 3점+URL pill | 히어로·Chart용 |
| FAQ | | `<details>`, 질문 높이 64 | 첫 항목 open |

> Figma의 Button/Badge는 컴포넌트지만, HTML에서는 `.btn`, `.badge` 클래스 하나로 구현한다. 상태 이름은 위와 같이 맞춘다.

### 5-2. 레이아웃 (브레이크포인트 **900px**)

**섹션 순서 (확정 §10-1 반영 — v3 시안에서 바뀐 부분은 ★)**

| # | 섹션 | 비고 |
|---|---|---|
| 1 | 헤더 | v3 디자인 + 기존 4개 서비스 링크·문의하기·계정 메뉴 유지(§10-2) |
| 2 | 히어로 — **AI 홈페이지 제작** (가장 중요한 서비스) | 기존 `data-buildit-*` 훅 유지 |
| 3 | 제작 과정 4단계 | |
| 4 | ★ **BuildIt 소개 — 슬로건 + 소개영상** | 기존 `#intro` 영상(YouTube `fK_BdoK8O64`, 1분 44초)과 슬로건 "숨은 영어교육자를 발굴하고, 성장시키고, 투자로 연결합니다."를 v3 톤으로 재배치. 아래 §5-4 |
| 5 | ★ 서비스 탐색기 — **탭 3개: AI 홈페이지 제작(기본) · Chart · Easy AI 장부** | v3의 DesignCraft·입시상담 AI 탭은 여기서 **제거**(아래 7번 섹션으로 이동) |
| 6 | **Chart** 다크 밴드 — 실제 공개 TOP5 위젯 | §7-1. 기존 `#chart-showcase` 지역 선택·TOP5·평가 기준 링크 유지 |
| 7 | ★ **콘텐츠 도구 — Wiki · Report · Shorts** | 기존 영상 슬롯·Wiki 3단계 영상 자산 사용(§6-2) |
| 8 | ★ **준비 중 서비스 — DesignCraft · 입시상담 AI** | 비활성 카드 2장, 링크·입력창 없음(§7-3). v3의 해당 패널 내용을 카드로 축약 |
| 9 | FAQ | 정정 문구(§3-6) |
| 10 | CTA | 기존 `#cta`(`data-buildit-start`) 유지 |
| 11 | 푸터 | 기존 `publicFooterBuildIt` + 문의 모달 + 계정 모달 유지 |

> ★ 표시한 순서·구성은 **Figma/HTML v3에서 렌더링 확인을 하지 않은 변경**이다. Muse가 구현한 뒤 390/1440 스크린샷을 Claude에게 보내면 시각 리뷰를 한다. (Figma는 MCP 한도로 수정 불가 — 이 문서가 기준)

**PC 1440** (콘텐츠 1120)
- 히어로: 좌 카피(약 460px) + 우 앱 창(나머지, 약 612px), 갭 48. *(HTML은 `1fr / 1.1fr`, Figma는 460 고정 — **HTML 비율을 따른다**)*
- 제작 과정 4열 카드, 서비스 탐색기 탭+패널, Chart 다크 밴드(좌 카피/우 창), 콘텐츠 도구 3열, 준비 중 서비스 2열 카드, FAQ(폭 820), CTA 밴드, 푸터

**모바일 390** (콘텐츠 350): 위 순서 그대로 **1열**. 서비스 탭은 HTML에서 **가로 스크롤**(Figma는 줄바꿈으로 표현 — 차이점), 히어로는 카피 위·앱 창 아래.

**미검증:** 360·430·768·1024px 전부(§14). 900px 경계에서 히어로 2열 전환 시 앱 창 폭이 좁아지는지 직접 확인한다.

### 5-4. 소개영상·슬로건 배치 (삭제 금지)

- 현재 `landing.html`의 `#intro`(`hero hero-intro`)에 슬로건 `.hero-slogan`과 `.hero-video-card[data-youtube-id]`(클릭 시 iframe 로드, `referrerPolicy`)가 있다. **마크업·`data-youtube-id`·클릭-로드 방식(`landing_promo_video.test.js`)을 유지**하고 레이아웃만 v3로 바꾼다.
- 제안 배치: 모바일은 슬로건(H2) → 영상 카드(16:9, 둥근 모서리 r28, 재생 버튼) 세로, PC는 좌 슬로건·우 영상 2열. 다크 면(night)을 쓰면 Chart 밴드와 겹쳐 보이므로 **밝은 면(surface)** 권장.
- 기존 `.flow-marquee#why`(장식, `aria-hidden`)는 계약 테스트가 id를 고정한다. 별도 지시 전까지 **삭제하지 말고** 소개 섹션 하단에 장식으로 유지하거나, 제거하려면 §6-2 "별도 검토" 목록에 올린다.

### 5-3. 인터랙션 (HTML `home.js` 기준)

- 헤더: 스크롤 8px 초과 시 `.scrolled`(하단 테두리)
- 등장: `IntersectionObserver`로 `.rv` → `.in` (threshold .1, 1회). JS 없으면 `.no-js`로 즉시 표시
- 서비스 탭 전환 + 키보드(←/→)
- 예시 칩 클릭 → 입력창 채움 (**자동 제출하지 않음**, 기존 `data-buildit-example` 동작과 동일)
- placeholder 타자 효과(3문장 순환). reduced-motion이면 정적 문장 1개
- Chart 막대 채움, 히어로 대화·미리보기 순차 등장(CSS animation)

> ⚠ 기존 `landing_buildit.js`는 **GSAP ScrollTrigger**와 `.reveal[data-fade]` 방식을 쓴다. v3의 `.rv` IO 방식과 **이중 구현이 되지 않게** 한 방식으로 통일한다. 기존 GSAP 로드 실패 폴백(정적 노출)을 유지한다.

---

## 6. 유지해야 할 동작 · 깨질 수 있는 테스트

### 6-1. 라우트·권한 (코드로 확인한 사실 — 변경 금지)

| 경로 | 접근 | 비고 |
|---|---|---|
| `/` | 공개 | 랜딩 |
| `/chart`, `/chart/educators/**`, `GET /api/chart/**` | 공개 | |
| `/wiki` | 공개(소개) | `/wiki/**` 하위는 로그인 필요 |
| `/problem-generator/**` | STUDENT·TUTOR·ADMIN | 비로그인 시 `/?login=required` 302 |
| `/builder`, `/builder/entry` | TUTOR·ADMIN (컨트롤러 가드) | `/builder`는 permitAll이지만 컨트롤러가 비허용 계정을 랜딩으로 돌려보냄 |
| `/easy`, `/easy/**`, `/api/admin/easy/**` | **ADMIN 전용** | 일반 사용자 노출 금지 |
| `/home` | 공개 | `/login`은 `/home`으로 302 |
| `/register` | 공개 | 가입 후 `returnUrl` 지원 |
| DesignCraft | **사용자 화면 없음**(API만) | 링크 금지, "준비 중" |
| 입시상담 AI | **main에 없음**(PR #646 미병합, 검증 중) | 링크·기능 노출 금지 |

SecurityConfig는 **수정하지 않는다.** 홈 개편은 SecurityConfig·마이그레이션·API 계약 변경이 필요 없어야 한다(필요해지면 중단하고 보고).

### 6-2. 깨질 수 있는 기존 테스트 (구현 전에 의도적으로 수정/확인)

| 테스트 | 고정하는 것 | v3와의 충돌 |
|---|---|---|
| `LandingTemplateContractTest` | 아래 §6-3 표 참고 | **순서 단언은 확정된 새 순서와 충돌**(별도 검토 대상) |
| `LandingTemplateRenderTest`, `LandingPageControllerTest` | 렌더·모델 속성 | #651 병합 시 `homepageSamples` 제거 반영 필요 |
| `landing_buildit_start.test.js` (11개) | 시작하기 게이트 전 시나리오 | 훅(`data-buildit-*`) 유지 시 통과 |
| `landing_promo_video.test.js` | 소개영상 | `#intro`의 YouTube 카드 **유지**(확정 §10 결정 3, 배치는 §5-4) |

규칙: **CLAUDE.md의 "함수 생성 시 unit test(given/when/then)"** 를 따른다. 새 JS 로직은 테스트를 같이 만든다. 테스트를 "녹색으로 만들려고" 삭제·완화하지 않는다.

### 6-3. 계약 테스트 vs v3 확정안 — 차이 기록 (별도 검토 대상 표시)

**서비스 노출 순서 3자 비교 (기록)**

| 출처 | 순서 |
|---|---|
| `docs/BUILDIT_PHILOSOPHY.md` (제품 흐름) | Chart(발견) → Web → Shorts/Report/Wiki → Educator Brain |
| `LandingTemplateContractTest.landing_orders_services_by_product_strategy` | hero → **Wiki → Web → Chart → Report → Shorts** (`wiki > hero`, `web > wiki`, `chart > web`, `report > chart`, `shorts > report` 인덱스 단언) |
| **사용자 확정 (이번 v3)** | **AI 홈페이지 제작(가장 중요) → Chart → Easy → 콘텐츠 도구(Wiki·Report·Shorts) → 준비 중(DesignCraft·입시상담 AI)** |

- 세 기준이 서로 다르다. 이번 확정은 **홈페이지 노출 순서**에 대한 것이며, 철학 문서의 "제품 흐름" 설명과 계약 테스트는 **자동으로 갱신되지 않는다.**
- 이 PR(문서)에서는 철학 문서와 테스트를 **수정하지 않고 차이만 기록**한다. 철학 문서 문구 갱신 여부는 사용자 결정(미결).

**테스트 항목별 상태**

| 항목 (현재 단언) | v3 확정안과의 관계 | 상태 |
|---|---|---|
| 섹션 순서 인덱스(`hero < wiki < web < chart < report < shorts`) | 충돌 — 새 순서에서는 `web < chart < wiki/report/shorts` | ⚠ **별도 검토: 변경 필요(승인 후)** |
| `class="builder" id="web"`, `class="wiki" id="wiki"`, `class="product-solo" id="shorts"/"report"`, `class="hero" id="hero"`, `class="flow-marquee" id="why"`, `class="cta-mini" id="cta"`, `class="bi-nav" id="biNav"` | **정확한 속성 문자열**을 단언 | 구현 팁: 새 마크업에도 **같은 class+id 속성을 유지**하면 순서 외 단언은 수정 불필요 |
| `<title>`, 로고 `<span class="nav-logo bi" ...>`, `<span class="ext">Web/Shorts/Report/Wiki</span>` | 유지 가능 | 변경 불필요 |
| 슬로건 `숨은 영어교육자를 발굴하고, 성장시키고, 투자로 연결합니다.` | 유지(§10 결정 3) | 변경 불필요 |
| 내비 링크 3종 문구(`/wiki` Buildit 모의고사, `/home` F2R, `/problem-generator` BUILD IT WIKI) + CHART | 유지(§10-2) | 변경 불필요 |
| `대한민국 지역별 영어교육자 TOP 100`, `BuildIt Chart 전체보기 →`, `class="chart-phone"`, `정수정영어학원 · 서울 송파구 삼전동`, CSS `.chart-showcase/.chart-phone/.chart-row/.chart-cta` | v3 Chart 밴드로 재디자인 시 문구·클래스가 바뀔 수 있음. `정수정영어학원 …` 문자열의 현재 위치는 확인하지 못했다 | ⚠ **별도 검토** — 유지하는 것을 기본으로 하고, 바꿔야 하면 PR에 사유 명시 |
| Wiki 3단계(`기출 분석/변형문제 생성/학교별 Wiki`, `data-screen` 0·1·2, `digital_twin_ex.mp4`, `how2use_digitaltwin.mp4`, `mock-dash-title`, `mws-new`) | 콘텐츠 도구의 Wiki 영역. v3 카드는 단순화된 시안 | ⚠ **별도 검토** — 기존 Wiki 3단계 영상 자산을 v3 톤으로 재배치하되 마크업·자산 경로 유지 |
| Web 영상(`buildit-web-builder-highlight.mp4`, `web-video`, `muted loop autoplay playsinline preload="metadata"`) | v3에 영상 영역 없음 | ⚠ **별도 검토** — 제작 섹션에 유지할지 결정 |
| Shorts/Report 슬롯(`th:if="${academyShortsVideo.visible}"` 등) | 유지 | 변경 불필요 |
| 문의·계정·푸터 계약(`openContactModal()`, `publicContactModal`, `publicFooterScripts`, `accountMenuControls`, `loginModal`, `publicFooterBuildIt`, 푸터 문구) | 유지(§10-2, §10-7) | 변경 불필요 |
| 시작하기 게이트(`data-buildit-start`, `href="/builder" class="btn" data-buildit-start`, JS의 `/api/auth/me`, `BUILDER_ROLES=['TUTOR','ADMIN']`, `canUseBuilder(user)`, `'/register?returnUrl=…'`) | **반드시 유지**(§10-7) | 변경 금지 |
| `LandingPageControllerTest`/`LandingTemplateRenderTest` | #651 병합 후 `homepageSamples` 제거 반영 | #651 선행 |

> **테스트를 "통과시키려고" 완화·삭제하지 않는다.** 위 ⚠ 항목은 사용자에게 변경 승인을 받은 뒤에만 수정하며, 수정한 단언은 PR 본문에 "기존 단언 → 새 단언 → 사유"로 적는다.

### 6-4. 선행 조건

- **PR #651 병합이 선행**되어야 한다. #651은 `#samples`, `homepageSamples`, 관련 CSS·테스트를 제거한다. v3 작업을 그 위에서 시작하면 충돌이 없다.
- 시작 전 `docs/BUILDIT_PHILOSOPHY.md`, `docs/INDEX.md` 확인(CLAUDE.md 필수 절차).
- 인증·역할 분기는 **`landing_buildit.js`와 `_account_menu` 로그인 모달을 그대로 유지**한다(§10-7). 이 로직을 옮기거나 다시 쓰지 말고 새 마크업에 훅만 연결한다.

---

## 7. Chart · Easy 실제 화면 활용 검토 — 예시 vs 운영 데이터

### 7-1. Chart — **실데이터를 쓰되 예시와 섞지 않는다**

**가능 여부: 가능(이미 구현돼 있음).** 현재 랜딩의 `#chart-showcase`는 `landing_chart.js`가 공개 API로 실제 TOP5와 지역 선택을 렌더링한다.
- `GET /api/chart/publication/current` → `rankedTop100[]`(`rank`, `name`, `role`, `identitySummary|academyName|activityRegion`, `previousRank`, `educatorId`), `updatedAt`
- `GET /api/chart/regions` → 지역 트리(`province`, `cityDistricts`, `educatorCount`)
- 상세는 `/chart/educators/{educatorId}` (공개)

**구현 지침**
1. v3의 Chart 우측 창은 **실제 TOP5 위젯으로 교체**한다. 시안의 "교육자 A/B/C"를 운영에 넣지 않는다.
2. 날짜 표기(`updatedAt`)와 "전국 Chart를 불러오는 중입니다 / 불러오지 못했습니다 / 곧 공개됩니다" 상태 문구를 기존 그대로 유지한다.
3. **실제 이름 옆에 지어낸 근거 행(수업 자료 공개·학생 후기 등)을 두지 않는다.** 시안의 "근거 행" 창은 *상세 화면의 구조 설명용*이므로, 쓰려면 비실명 placeholder + "화면 예시" 배지를 고정하고 실데이터 위젯과 **시각적으로 분리**한다. 혼동 우려가 있으면 삭제를 권장한다.
4. **철학 제약:** Chart 점수는 BuildIt Web 사용 여부와 무관해야 한다. "점수에 영향 없음" 문구는 그대로 유지하고, 홈에서 Chart를 *홈페이지 제작 혜택*처럼 보이게 하지 않는다. AI 해석과 근거 구분 원칙도 유지한다.

### 7-2. Easy AI 장부 — **실제 화면 사용 불가, 예시만**

- `/easy/**`는 ADMIN 전용이고 화면 내용은 **실제 학원 회계 데이터**다. 홈에 실화면·실수치·캡처를 넣지 않는다. (우리 시안은 수치 대신 "예시"를 썼다.)
- 목업이 필요하면 **더미 데이터 격리 DB**에서 만든 화면이거나 HTML 일러스트로 하되 **"예시 화면" 배지를 고정**한다. 운영 환경(prod) 캡처는 금지다.
- "일반 사용자용 Easy 연결"은 현재 코드로 불가능하다(권한·테넌트 분리·과금 문제). 이는 **별도 제품 결정과 구현**이며 홈 개편 범위가 아니다. 그동안 홈에서는 "베타·운영자 승인제 / 도입 문의"로만 표기한다.
- **`/easy`로 직접 보내지 않는다.** Easy 카드·탭·CTA 어디에도 `/easy` 링크를 두지 않는다. (ADMIN 계정은 기존 계정 메뉴 등 기존 경로로 이미 접근한다 — 홈에서 새로 노출하지 않는다.)
- **도입 문의 연결(확인 결과):** 현재 운영 코드에서 **실제로 쓰이는 문의 경로는 공용 "문의하기" 모달**이다.
  - 랜딩 `landing.html`이 이미 `publicContactModal(false)` + `publicFooterScripts`를 포함하고, 내비·모바일 메뉴의 "문의하기" 버튼이 `openContactModal()`을 호출한다.
  - 모달 내용: 제목 "상담 신청하기", 전화 `02-6324-1453`, 이메일 `admin@feel2real.co.kr` (모두 코드상 값이며 **실제 응답 여부는 확인하지 못했다** — 운영 담당 확인 필요).
  - Easy 전용 문의 폼·API는 **없다**(문의 관련 코드 탐색 결과 NewStart 상담 등 다른 도메인만 있음). 새로 만들지 않는다.
  - **지침:** Easy의 "도입 문의" 버튼은 `onclick="openContactModal()"`로 연결한다. 모달·스크립트 fragment가 페이지에 포함돼 있는지 구현 시 확인하고, **없거나 동작하지 않으면 버튼을 비활성(`aria-disabled="true"`, "준비 중")으로 표시**한다. 임의 URL·폼을 만들지 않는다.
  - Easy 전용 문의 채널(카카오 채널, 전용 폼 등)이 필요하면 별도 결정 사항이다. 기존 `exam.html`의 카카오 오픈채팅 링크는 **내신 시험지 문의용**이므로 Easy에 재사용하지 않는다.

### 7-3. 예시/운영 구분 규칙 (모든 서비스 공통)

| 표시 | 규칙 |
|---|---|
| 운영 데이터 | 출처(공개본 날짜)를 표기, 링크 가능, 빈/오류 상태 문구 필수 |
| 예시 화면 | "예시" 배지 고정, 실명·실수치 금지, 링크 걸지 않음 |
| 준비 중 | 비활성(`aria-disabled`), 링크 없음, 작동하는 것처럼 보이는 호버·포커스 효과 금지 |
| 비공개(입시상담 AI) | 상담 입력창·"체험하기" 등 기능을 암시하는 UI 금지 |

---

## 8. Figma ↔ HTML 차이 정리 (구현은 HTML 기준)

| 영역 | Figma | HTML v3 (기준) |
|---|---|---|
| 폰트 | Noto Sans KR | Pretendard → Noto Sans KR fallback (자간·줄바꿈 소폭 차이) |
| 본문 크기 | Small 14px | 카드·단계·FAQ 본문 15px |
| 히어로 컬럼 | 카피 460px 고정 + 창 fill | `1fr / 1.1fr` |
| 배경 효과 | 없음 | 방사형 글로우(`.hero::before/::after`), 헤더 `backdrop-filter` blur |
| 서비스 탭 | 줄바꿈(wrap) + 첫 패널만, 나머지는 별도 보드 | 가로 스크롤(모바일), 클릭 전환, ARIA |
| 애니메이션·전환 | 없음(정적) | 대화·미리보기 순차, 막대 채움, 스크롤 등장, 타자 placeholder, pulse |
| FAQ | 첫 항목 펼침 고정 | `<details>` 토글 |
| 체크 아이콘 | Chart 원칙 목록·히어로 보증이 **체크 없는 민트 원** | SVG 체크 포함 |
| 재생 아이콘 | 삼각형 도형 | `▶` 문자 |
| 이미지 | 없음(순수 레이어) | 없음(히어로 우측 썸네일은 그라디언트 박스) |
| Chart 막대 | 정적 | 채움 애니메이션 |
| 버튼 그림자 | Primary만 | 동일 |
| 컴포넌트 | Button/Badge 컴포넌트 세트(변수·스타일 바인딩) | `.btn`/`.badge` 클래스 |

Figma에서 **검증하지 못한 것**: 구조 점검(노드/변수 바인딩 수), 모바일 Chart·콘텐츠·FAQ 확대 렌더. 차이가 보이면 HTML을 따르고 Figma를 고치려 하지 않는다(MCP 한도).

---

## 9. Muse 브라우저 검증 항목 (필수)

### 9-1. ⚠ 검수 미완료 구간 (최우선 확인)

**모바일 390px에서 아래를 확대 렌더로 직접 확인하고 결과를 기록한다.**

| 구간 | 확인할 것 |
|---|---|
| **Chart** | 실데이터 TOP5 행(긴 이름·`identitySummary`) 줄바꿈·잘림, 로딩/실패/빈 상태 문구, 지역 선택 select 터치 크기, 다크 면 위 대비, 가로 넘침 |
| **콘텐츠(Wiki/Report/Shorts)** | 영상 슬롯 `visible` true/false 두 상태, 카드 세로 간격, 링크 대상·로그인 필요 안내, 영상 카드 비율 |
| **FAQ** | 펼침/접힘, 긴 질문 줄바꿈, 터치 영역(≥44px), 키보드 조작, 정정된 문구(§3-6) |
| (참고) CTA·푸터 | 버튼 터치 크기, 하단 여백 |

### 9-2. 공통 체크리스트

1. **뷰포트(확정 §10-9):** **360 / 390 / 430 / 768 / 1024 / 1440** — 세부 항목은 §14. 가로 스크롤 없음은 `body{overflow-x:hidden}` 없이 **요소 단위**로 확인(시안에서 이 방식으로 실제 넘침을 찾았다).
2. **시작하기 게이트(회귀):** 비로그인 / TUTOR / ADMIN / 학생 각각에서 입력 제출·예시 칩·시작하기 버튼 동작이 기존과 동일(`landing_buildit_start.test.js` 통과 + 수동).
3. **로그인:** 헤더 계정 메뉴·로그인 모달·회원가입 링크·`/?login=required` 리다이렉트 후 안내.
4. **링크:** 모든 CTA가 실제 존재하는 라우트인지(§3 정정 반영), 404 없음. 권한 없는 계정이 눌러도 안전한지(`/easy` 등).
5. **Chart 실데이터:** `/api/chart/publication/current` 정상·빈 응답·실패 3가지 상태.
6. **영상:** `parentReportVideo` / `academyShortsVideo` 노출·숨김, 소개영상(YouTube) 유지 시 동작.
7. **접근성:** axe-core 위반 0 목표, 키보드(Tab/←→/Enter/Esc), 포커스 링, 터치 영역 ≥ 44px, `prefers-reduced-motion`.
8. **성능:** Lighthouse(모바일·데스크톱), CLS 0 목표. 폰트 로딩 방식(외부 CDN 여부) 확인.
9. **JS 비활성/GSAP 로드 실패:** 내용이 가려지지 않는지(시안의 `.no-js`, 기존 정적 폴백).
10. **테스트:** `./gradlew test`(landing 관련), `node --test src/test/js/landing_*.test.js`, 변경한 계약 테스트의 사유 기록.
11. **스크린샷 제출:** 변경 전/후, 390·1440 (PR 본문).

---

## 10. 확정된 결정 (사용자 확정 — Muse는 이 기준으로 구현)

| # | 결정 | 확정 내용 | 문서 반영 |
|---|---|---|---|
| 1 | 서비스 노출 순서 | **AI 홈페이지 제작(가장 중요) → Chart → Easy → 콘텐츠 도구 → 준비 중 서비스.** 기존 제품 전략 문서·계약 테스트와의 차이는 기록하고, 변경이 필요한 테스트는 **별도 검토 대상**으로 표시 | §5-2, §6-3 |
| 2 | 상단 내비 | **v3 디자인 적용 + 기존 모의고사·F2R·CHART·WIKI 접근 경로 유지** (+ 문의하기·계정 메뉴) | §5-2, §6-3, §10-2 아래 |
| 3 | 소개영상·슬로건 | **삭제하지 않고 v3 디자인에 맞게 배치** | §5-4 |
| 4 | Easy | **관리자 전용 베타.** 일반 사용자를 `/easy`로 보내지 않음. 도입 문의는 **실제 운영 중인 문의 경로 확인 후 연결, 없으면 비활성** | §7-2 |
| 5 | 폰트 | **Pretendard 우선**, 로딩 실패 시 대체 폰트, **자체 호스팅 가능성 검토** | §13 |
| 6 | 시안 보존 | Figma 링크·HTML v3 원본·스크린샷·핸드오프 문서를 **`docs/design/homepage-v3/`에 보존(문서 PR)** | §1, 부록 C |
| 7 | 인증 | **기존 로그인 모달과 `landing_buildit.js`의 인증·역할 분기 로직 유지** | §3, §6 |
| 8 | 데이터 | **Chart 실제 공개 TOP5 위젯 유지. Easy에는 실제 회계 데이터 노출 금지** | §7 |
| 9 | 반응형 | **360·390·430·768·1024·1440** 검증을 Muse 작업 범위에 포함 | §14 |

### 10-2 상단 내비 구현 지침 (결정 2)

- 기존 `.nav-links`의 4개 링크를 **라벨·href 그대로** 유지한다: `Buildit 모의고사`(/wiki), `F2R`(/home), `BUILD IT CHART`(/chart), `BUILD IT WIKI`(/problem-generator). 모바일 메뉴(`#biMenu`, `#biBurger`)의 동일 링크와 `문의하기` 버튼(`openContactModal()`)도 유지한다. (계약 테스트가 정확한 문자열을 단언한다.)
- v3 디자인(높이 68, blur 배경, 스크롤 시 하단선, pill 버튼)은 **스타일만** 적용한다. v3 시안의 앵커 내비("제작 과정/서비스/Chart/콘텐츠 도구/FAQ")는 **링크를 추가하지 말고**, 필요하면 데스크톱에서 보조 줄이나 별도 위치에 두는 안을 Muse가 제안하되 구현 전 사용자 확인을 받는다. 기본값: **추가하지 않는다.**
- 우측은 `accountMenuControls(...)`를 그대로 사용한다. `data-auth-signup` 링크 동작을 유지한다.
- 모바일: 4개 링크 + 문의하기가 햄버거 메뉴에 모두 있어야 한다(360px에서 헤더가 두 줄로 깨지지 않을 것).

### 10-3 아직 미결인 항목 (구현 중 발견 시 사용자에게 보고)

| 항목 | 기본 처리 |
|---|---|
| `.flow-marquee#why` 유지 여부 | 유지(§5-4) |
| Web 섹션 영상(`buildit-web-builder-highlight.mp4`) 유지 여부 | 유지(제작 섹션 또는 서비스 탐색기 제작 패널) |
| `BUILDIT_PHILOSOPHY.md`의 서비스 흐름 서술과 홈 노출 순서 차이 | 문서 갱신 여부 사용자 결정 |

---

## 11. 협업·충돌 방지 규칙

- `landing.html`, `landing_buildit.css`, `landing_buildit.js`, `LandingPageController` 및 관련 테스트는 **Muse 단독 수정**. Claude는 수정하지 않는다.
- Claude는 시안(별도 파일)과 시각 검증만 담당하며, 요청 시 **스크린샷 대조 리뷰**를 한다.
- 동시 편집 금지. 작업은 단계별 PR로 나눈다(CONTRIBUTING 형식: 타입 prefix + 목표/범위/검증/향후/관련).
- 권장 단계

| 단계 | 범위 | 검증 |
|---|---|---|
| P0 | #651 병합 확인(별도 승인 후), §10-3 미결 항목 확인 | – |
| P1 | 토큰 + 헤더(계정 메뉴 유지) + 히어로(훅 유지) | 시작하기 게이트 11개 테스트, 390/1440 |
| P2 | 제작 과정 + 서비스 탐색기(정정 §3 반영, 준비 중 표기) | 링크·권한 수동 점검 |
| P3 | Chart(실데이터 유지) + 콘텐츠(영상 슬롯) + FAQ + CTA + 푸터 | §9-1 모바일 미검수 구간 |
| P4 | 계약 테스트 정리, Lighthouse·axe, 최종 스크린샷 | §9-2 전체 |

- 운영 배포·prd 변경·DB 마이그레이션·SecurityConfig 변경은 **하지 않는다**(필요 시 중단·보고). 배포는 별도 승인 후 수동 Actions로만.

---

## 12. 시안에서 "그대로 가져와도 되는 것 / 가져오면 안 되는 것"

**가져오기:** 토큰, 타이포 위계, 8pt 간격, 카드·배지·버튼 스타일, 서비스 탐색기 구조(탭→목적→사용 3단계→이런 분께→예시 화면→CTA), 상태 표기 방식(운영 중/베타/준비 중/검증 중), 모션 원칙, 접근성 처리(ARIA, 포커스, reduced-motion).

**가져오지 않기:** 시안의 `form action`, `/login`·`/report-demo`·`/shorts-demo` 링크, "교육자 A/B/C" 및 근거 행, FAQ의 Wiki 로그인 문구, Easy "로그인하고 확인" 링크, 시안 `<title>`, 시안 푸터 문구("디자인 시안 v3").

---

## 13. 폰트 — Pretendard 우선 + 대체 + 자체 호스팅 검토

### 13-1. 현재 상태 (코드 확인)
- `landing_buildit.css`의 `--font`가 이미 `-apple-system, BlinkMacSystemFont, "Pretendard Variable", Pretendard, …`로 Pretendard를 **이름만 지정**한다. 그러나 `@font-face`나 CDN `<link>`가 없다 → **사용자 기기에 Pretendard가 설치돼 있지 않으면 실제로는 시스템 폰트가 쓰인다.** 즉 "Pretendard 우선"은 현재 사실상 적용되지 않는다.
- 기존 랜딩은 이미 외부 CDN(cdnjs의 GSAP)에 의존한다. 폰트에 외부 CDN을 추가하는 것이 정책상 막히는 근거는 확인하지 못했고, 전역 CSP 헤더 설정도 코드 탐색에서 확인되지 않았다(일부 컨트롤러만 CSP 지정). **운영 응답 헤더/앞단(nginx 등) 설정은 확인하지 못했다 → 구현 시 재확인.**

### 13-2. 자체 호스팅 가능성 — **가능 (조사 결과)**
npm 패키지 `pretendard@1.3.9`를 격리 디렉터리에서 내려받아 확인했다.

| 항목 | 값 |
|---|---|
| 라이선스 | **SIL OFL 1.1** (패키지 `license: OFL-1.1`, LICENSE.txt 포함). 재배포·번들 허용, **라이선스 문서 동봉 필요**, Reserved Font Name "Pretendard"이므로 **수정 시 이름 변경 필요**(수정 없이 그대로 쓰면 무관) |
| Variable 전체 | `PretendardVariable.woff2` 약 **2.06MB** (단일 파일, 비권장) |
| Variable 동적 서브셋 | **92개** woff2, 합계 약 **3.1MB**, 파일당 최대 약 44KB, `unicode-range` 분할 + `font-display: swap` + `font-weight: 45 920` (페이지가 쓰는 글자 범위만 내려받음) |
| 제공 CSS | `pretendardvariable-dynamic-subset.css` (`@font-face` 92개) |

**권장 방식(구현 PR에서 Muse가 수행):**
1. `PretendardVariable` **동적 서브셋**을 `src/main/resources/static/fonts/pretendard/`에 두고, `@font-face`(font-display: swap, unicode-range)를 `landing_buildit.css` 또는 별도 CSS에 포함한다. `LICENSE.txt` 동봉.
2. font-family 스택: `"Pretendard Variable", Pretendard, -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Noto Sans KR", "Noto Sans CJK KR", "Malgun Gothic", sans-serif` — **로딩 실패·지연 시 대체 폰트**가 즉시 적용된다(`swap`).
3. 한글 줄바꿈 `word-break: keep-all` 유지.
4. 레이아웃 이동(CLS) 확인: 폰트 교체 시 줄바꿈·높이 변화를 360/390/1440에서 점검. 필요하면 대체 폰트 `size-adjust` 보정 검토.
5. 캐시: 정적 폰트에 장기 캐시 헤더가 붙는지 운영 설정을 확인한다(확인하지 못함).
6. 저장소 용량 +약 3.1MB(Git 이력에 영구 포함) — 수용 가능한지 사용자 확인.

**임시안(자체 호스팅 전):** 시안처럼 jsDelivr의 Pretendard `dynamic-subset` CSS를 `<link>`로 쓰되 **대체 폰트 스택이 반드시 있어야** 한다. 단, 외부 CDN 장애·개인정보(IP 노출) 관점에서 운영에는 자체 호스팅을 권장한다.

> 이 문서 PR에는 **폰트 파일을 포함하지 않는다.** 폰트 반입은 구현 PR의 범위다.

## 14. 반응형 검증 범위 (Muse 작업 범위 — 확정 §10-9)

**뷰포트:** 360 · 390 · 430 · 768 · 1024 · 1440 (px, 높이 약 800~900)

| 폭 | 확인 포인트 |
|---|---|
| **360** | 가장 좁은 안드로이드. 헤더(로고+계정 메뉴+햄버거)가 한 줄, 히어로 입력창+버튼 잘림 없음, 칩 줄바꿈, 서비스 탭 가로 스크롤(끝 도달 가능), Chart TOP5 행 긴 이름, 앱 창 목업 내부 텍스트 넘침 |
| **390** | 시안 기준 폭. Figma/HTML 시안과 1:1 비교(스크린샷 diff) |
| **430** | 대형 폰. 콘텐츠 폭이 늘어날 때 카드·영상 비율, 버튼 폭 |
| **768** | 태블릿 세로. **900px 미만이라 모바일 레이아웃**이 쓰인다 — 콘텐츠가 과하게 넓어지지 않도록 `max-width` 또는 2열 전환 여부 판단(시안에 정의 없음 → Muse가 제안, Claude 리뷰) |
| **1024** | **900px 초과 → PC 2열 레이아웃**. 히어로 컬럼(카피 vs 앱 창) 비율, 서비스 패널 2열, Chart 밴드 2열이 좁은 폭에서 깨지지 않는지(PC 시안은 1440 기준만 있음) |
| **1440** | PC 시안 기준 폭. Figma/HTML 시안과 비교 |

**각 폭 공통:** 요소 단위 가로 넘침 검사(`getBoundingClientRect().right > innerWidth`), axe-core 위반 0, 터치 영역 ≥ 44px, 키보드 포커스 링 가림 없음, 텍스트 확대(브라우저 200%)에서 레이아웃 유지, `prefers-reduced-motion`.
**시안에서 정의되지 않은 구간:** 360, 430, 768, 1024는 **Figma/HTML 시안이 없다.** Muse가 구현 후 스크린샷을 제출하고, 시안과 어긋나는 곳은 사용자에게 보고한다.
**기록 방식:** 폭별 스크린샷 + 발견/수정 내역을 PR 본문 "검증" 섹션에 표로 남긴다.

## 부록 A. 보존 위치

이 문서, Figma 링크, HTML v3 원본, 스크린샷은 `docs/design/homepage-v3/`에 보존한다(문서 PR). 구성은 같은 폴더의 `README.md` 참고.

## 부록 B. 확인된 코드 근거 (origin/main)

- `LandingPageController` — 모델: `academyShortsVideo`, `parentReportVideo`, `homepageSamples`(#651에서 제거)
- `landing_buildit.js` — `BUILDER_URL='/builder'`, `REGISTER_URL='/register?returnUrl=…'`, `/api/auth/me`로 역할 확인, `data-buildit-form|prompt|example|start`
- `landing_chart.js` — `/api/chart/regions`, `/api/chart/publication/current`
- `SecurityConfig` — 위 §6-1 표의 규칙
- `WebMvcConfig` — `/login`→`/home` 302, `/register` 뷰
- 계약 테스트 — `LandingTemplateContractTest` 등 (§6-2)

## 부록 C. 이 문서가 다루지 않는 것

실제 홈페이지 수정, PR, 병합, 배포는 이 단계에서 하지 않았다. 입시상담 AI(#646)·DesignCraft 사용자 화면 구현은 범위 밖이다.
