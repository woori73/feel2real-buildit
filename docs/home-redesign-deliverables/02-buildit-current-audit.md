# 02. BuildIt 현재 홈페이지 감사 (feel2real.co.kr)

> 산출물 2/6 · REA(실브라우저 실측) + 코드 대조 기반 · 작성 2026-10-16
> 대상: `https://feel2real.co.kr/` (public landing) · 코드: `woori73/feel2real-buildit` @ `69fe4bd3` (2026-10-16 09:54 KST)

---

## 0. 조사 조건

| 항목 | 값 |
|---|---|
| 도구 | Playwright MCP (실제 렌더링·스크롤·클릭), `curl` (비로그인 게이트 검증) |
| PC 뷰포트 | 1440 × 900 |
| 모바일 뷰포트 | 390 × 844 |
| 코드 | depth-1 read-only clone (`_work/repo`), 템플릿·라우팅·보안 설정 대조 |
| 주석 이미지 | `annot-buildit-pc-1440-fold.png`, `annot-buildit-pc-1440-services.png`, `annot-buildit-mb-390-fold.png`, `annot-buildit-mb-390-menu-open.png` |

### ⚠️ 조사 중 발생한 배포 이벤트 (수치를 정확히 구분해야 함)

| 시각(KST) | 상태 |
|---|---|
| ~10:01 | 구 빌드 측정: `/` 문서 높이 **6998px**, `#services` 섹션 **없음** |
| 10:03–10:05 | **전 경로 502** (`/`, `/chart`, `/wiki`, `/testprep`, `/home`, `/actuator/health` 포함) — 운영 배포 윈도우 |
| ~10:05 이후 | 신 빌드 복구: `/` 문서 높이 **8732px**, `#services` 섹션 **생김** |

**이 문서의 모든 수치는 10:05 이후 신 빌드 기준**이며, 구 빌드 수치는 §7 비교용으로만 사용한다.
git 기록과 일치: `c02aa0a3` (2026-10-15 16:37) "[FEAT] 홈 섹션 순서 확정: Agent → 문제지 제작 → 오픈 서비스(앵커) → 출시 예정 → CHART (#705)" — 즉 **서비스 카드 섹션은 이미 main에 존재했고 조사 도중 배포된 것**.

---

## 1. PC 첫 화면 (0–900px) 실측 — 인터랙티브 요소 전수

문서 높이 **8732px**. 뷰포트 900px = 전체의 **10.3%**.

| # | 요소 | 좌표 (x, y, w, h) | 종류 | 목적지 |
|---|---|---|---|---|
| 1 | `a.nav-brand` "Build It" | 40, 12, 55, 44 | 링크 | `/` (자기 자신) |
| 2 | `.btn.nav-cta` **"Buildit 모의고사"** | 832, 13, 113, 40 | 링크 | **`/wiki`** ← 라벨 불일치 |
| 3 | `.btn.nav-cta` **"F2R"** | 953, 13, 52, 40 | 링크 | **`/home`** |
| 4 | `.btn.nav-cta` "BUILD IT CHART" | 1013, 13, 125, 40 | 링크 | `/chart` ✅ 일치 |
| 5 | `.btn.nav-cta` **"BUILD IT WIKI"** | 1145, 13, 109, 40 | 링크 | **`/problem-generator`** ← 라벨 불일치 |
| 6 | `button.nav-cta` "문의하기" | 1262, 15, 75, 38 | 버튼 | `openContactModal()` — 링크 없음 |
| 7 | `button.account-menu-trigger` 👤 | 1345, 14, 40, 40 | 버튼 | 계정 드롭다운 토글 |
| 8 | `button.nation-region` "서울" (`.on`) | 364, 397, 89, 55 | 버튼 | **href 없음** |
| 9 | "부산 / 대구 / 광주" | 366, 465·531·597, 84, 52 | 버튼 | **href 없음** |
| — | `section#nation` 타이틀·에이전트 카피·학원 카드 목업·노트북/폰 목업 | y=0–900 | 그래픽 | 링크 아님 |

**결론 (PC 첫 화면)**
- 인터랙티브 요소 **11개**, 그중 실제 페이지 이동 링크 **5개** (로고 + 헤더 4개).
- 헤더 4개 링크 중 **2개가 라벨과 다른 서비스로 이동**(§4).
- **AI 홈페이지 제작(빌더) 진입구는 첫 화면에 0개.** `#hero`의 "만들기"는 `<form>` submit이지 링크가 아니고, `#hero` 자체도 y=1046으로 첫 화면 밖.
- 첫 화면 900px 전체를 `section#nation`("전국 무대로 확장하세요") 하나가 점유. 이 섹션은 **지역 칩 → 카드 전환 데모**일 뿐 아무 데도 이동하지 않는다.

## 2. 전체 섹션 지도 (PC 1440, 실측)

| 섹션 | y | 높이 | 누적 화면수(900px) | 내용 |
|---|---|---|---|---|
| `nav#biNav` | 0 | 68 | 0 | 헤더 |
| `section#nation` | 0 | **900** | 0–1.0 | 전국 무대 (지역 칩 + 목업) — **링크 0** |
| `section#agent` | 900 | 146 | 1.0 | AI Agent 헤드 |
| `section#hero` | 1046 | 586 | 1.2–1.8 | **AI 홈페이지 제작** 입력 폼 (submit) |
| `section#wiki` | 1631 | **1693** | 1.8–3.7 | 내신위키 소개 (카카오톡·이메일 문의 링크 y=2220, 블로그 y=2862) |
| `section#why` | 3324 | 129 | 3.7 | 플로우 마퀴 (`ul.fm-track` 가로 애니메이션) |
| **`section#services`** | **3453** | **1734** | **3.8–5.7** | **"현재 오픈한 서비스"** ← §3 |
| `section#soon` | 5187 | 889 | 5.8–6.7 | "출시 예정 · 곧 만나요" 5카드 |
| `section#chart-showcase` | 6076 | 1324 | 6.8–8.2 | BUILD IT CHART (Billboard형, 강사 1~5위 y=6411~7035, `전국 TOP100 보기 →` y=7192) |
| `section#faq` | 7400 | 662 | 8.2–8.9 | FAQ |
| `section#cta` | 8062 | 443 | 9.0–9.5 | 하단 CTA "만들기" → `/builder` (y=8391) |
| `footer.public-footer` | 8505 | 228 | 9.5 | 개인정보/사업자/전화/이메일 |

**핵심 수치**: 서비스 목록(`#services`)까지 **3.8스크롤 화면**. 메가스터디는 동등 기능이 **0.78화면**(y=705/900).

## 3. `#services` 섹션 상세 (신 빌드, 실측 + 소스 대조)

```
.eyebrow "현재 오픈한 서비스"  /  h2 "지금 바로 이용하세요"
nav.svc-anchors  (y=3683)  → #svc-builder / #svc-problem / #svc-wiki / #svc-testprep / #chart-showcase
article.svc-card × 4  (전폭 1200px, 1열 스택, 각 h=336)
  y=3759  #svc-builder        → /builder            배지 "이용 가능"
  y=4115  #svc-problem        → /problem-generator  배지 "이용 가능"
  y=4471  #svc-wiki           → /wiki               배지 "이용 가능"
  y=4827  #svc-testprep       → /testprep           배지 "이용 가능"
각 카드 = 배지 + h3(+small) + 한 줄 설명 + <주요 기능 ul> + <사용 방법 ol> + `서비스 입장하기` 버튼
```

**평가**: 구조는 좋다(라벨·설명·기능·사용법·CTA). 문제는 **콘텐츠가 아니라 위치와 폭** — 1열 전폭 카드 4장 = 1344px(1.5화면)를 쓰면서 4개 서비스만 노출하고, 그 자체가 3.8화면 아래 있다.

**`#services`에 없는 것**: BUILD IT CHART(앵커만 있고 카드는 없음), DesignCraft, Easy 장부, 입시상담, LMS, TestPrep 외 나머지.

## 4. 라벨 ↔ 실제 목적지 불일치 (가장 심각한 IA 결함)

`landing.html:24-45` 원문 그대로:

| 헤더 라벨 | `href` | 실제 페이지 타이틀 | 판정 |
|---|---|---|---|
| `Buildit 모의고사` | `/wiki` | "BuildIt Wiki · 고1·고2·고3 영어 모의고사 자료를 함께" | △ 라벨은/wiki인데 `#services` 카드에서는 같은 `/wiki`를 **"내신위키"** 로 표기 → **같은 URL에 두 가지 이름** |
| `F2R` | `/home` | "F2R · Feel to Real — AI 기반 내신 영어 학습 플랫폼" | ✖ 첫 화면 메뉴에 정체를 알 수 없는 약어 |
| `BUILD IT CHART` | `/chart` | "BuildIt Chart \| BuildIt이 발견한 동네 실력있는 영어쌤" | ✅ |
| `BUILD IT WIKI` | `/problem-generator` | "내신 영어 문제지 제작기 · feel2real" | ✕✕ **라벨과 무관한 서비스**. 실제 위키(`/wiki`)는 "내신위키"인데, "BUILD IT WIKI" 메뉴는 제작기로 감 |

**모바일 드로어도 동일** (`annot-buildit-mb-390-menu-open.png` 실측: 4개 행이 PC와 같은 href):
`Buildit 모의고사→/wiki` (y=88), `F2R→/home` (y=152), `BUILD IT CHART→/chart` (y=216), `BUILD IT WIKI→/problem-generator` (y=280), `문의하기` (y=344, 버튼).

→ 사용자가 "위키"를 찾아 클릭하면 **문제지 제작기**에 도착하고, "모의고사"를 찾아 클릭하면 **위키**에 도착한다. 상호 교차 오류.

## 5. "두 개의 홈" 문제

| 경로 | 타이틀 | 자체 내비 |
|---|---|---|
| `/` | Build It \| 원장과 강사가 브랜드가 되는 시대 | Buildit 모의고사 · F2R · CHART · WIKI · 문의하기 |
| `/home` | F2R · Feel to Real — AI 기반 내신 영어 학습 플랫폼 | **학원 찾기`/academies` · 시험 분석`/analyzes` · 모의고사 Wiki`/wiki` · 블로그`/academy-posts` · 커뮤니티`/community`** |

`/home`은 높이가 2025px로 작지만 **완전히 다른 5개 메뉴를 가진 두 번째 진입점**이다. `/`의 "F2R" 메뉴가 여기로 연결되므로, 첫 화면에서 사용자는 아무 설명 없이 다른 IA의 사이트로 이동한다. (`/eduholic` → `/home` 301 리다이렉트도 `WebMvcConfig.java:40`에 존재)

## 6. 모바일 (390 × 844) 실측

문서 높이 **11005px** = **약 13화면**.

| 섹션 | y | 높이 | 화면수(844) |
|---|---|---|---|
| `nav#biNav` | 0 | 68 | — |
| `section#nation` | 0 | **1114** | 0–1.32 |
| `section#agent` | 1114 | 146 | 1.32 |
| `section#hero` | 1260 | 624 | 1.49–2.2 |
| `section#wiki` | 1884 | **2510** | 2.2–5.2 |
| `section#why` | 4394 | 129 | 5.2 |
| **`section#services`** | **4523** | **2551** | **5.36–8.4** |
| `section#soon` | 7073 | 1443 | 8.4–10.1 |
| `section#chart-showcase` | 8516 | 1043 | 10.1–11.3 |
| `section#faq` | 9559 | 712 | 11.3–12.2 |
| `section#cta` | 10271 | 455 | 12.2–12.7 |

**첫 화면(0–844) 인터랙티브 요소**: 로고(`/`), `button.account-menu-trigger`👤(263,14,40,40), `button.nav-burger`(311,12,44,44), `h2.nation-title`(20,92), `.nation-agent`(20,140), `button.nation-region` 서울/부산/대구/광주(y=254~256), 학원 카드 목업(y=345–662), 노트북·폰 목업(y=738–1038, 폴드에서 잘림).

→ **서비스 진입 링크 = 로고 1개(자기 자신) 제외 0개.** 헤더 4개 링크는 드로어 안에 숨어 있고(햄버거 1클릭 + 행 1클릭 = **2클릭**), 그마저 §4의 라벨 오류.

**드로어(`#biMenu`, 0,0,375,844 풀스크린)**: 5행만 존재 — 링크 4 + `문의하기` 버튼 1. 드로어 안에 로그인/회원가입 행은 없다.

**정정 (초기 결론 대비, 실측으로 확인)**: 그것이 곧 "모바일에서 로그인 불가"는 **아니다**. `landing_buildit.css:1473-1489` 실측 결과 실제 내비 브레이크포인트는 **860px**이고(`.nav-links{display:none}`, `.nav-burger{display:block}`), `.auth-actions`는 어떤 미디어 쿼리에서도 숨겨지지 않는다(`account_menu.css:27`, `:28`은 `[hidden]`일 때만 `display:none`).
서버 HTML(`curl`, 비로그인)에는 `<div class="auth-actions" data-auth-actions hidden>` + `button.btn-login`(로그인, 모달 오픈) + `a.btn-signup href="/register"`(회원가입)이 존재하고 JS가 세션에 따라 토글한다.
**390px에서 `hidden`을 제거해 렌더 확인** (`buildit-mb-390-anon-header.png`): 로고(x20) · **로그인(x156, 65×39)** · **회원가입(x230, 73×40)** · 햄버거(x311, 44×44) — 전부 한 행에 들어간다.
→ 따라서 드로어에 인증 행이 없는 것은 **P1 결함이 아니라 P3 개선 항목**(로그인 상태 사용자에게 👤 메뉴는 헤더에만 있다는 점을 드로어에서 재확인시켜 주는 편의성)으로 강등한다.

**수정된 관찰 (이전 의심 항목 정정)**: 페이지 단위 가로 스크롤 결함은 **없다**. `body{overflow-x:hidden}` 실측 확인, `documentElement.scrollWidth` 375 = 뷰포트 390 − 15px 스크롤바. 다만 `ul.nation-regions`가 `scrollWidth 334 / clientWidth 280`의 **의도된 가로 스크롤 컨테이너**이고, 4번째 칩(광주)이 잘려 보인다. 캡처에서 보이던 스크롤바는 이 내부 컨테이너의 것이다. → 개편 시 결함 수정 대상 아님.

## 7. 구 빌드 ↔ 신 빌드 비교 (배포 전후)

| 항목 | 구 빌드 (10:01) | 신 빌드 (10:05 이후) |
|---|---|---|
| `/` 문서 높이 PC | 6998 | **8732** (+1734) |
| `#services` | **없음** | 있음 (y=3453, h=1734) |
| 섹션 순서 | nation→agent→hero→wiki→why→**chart**→soon→faq→cta | nation→agent→hero→wiki→why→**services→soon→chart**→faq→cta |
| 서비스 진입 링크 | `/builder`(하단 CTA), `/testprep`뿐 | `/builder`, `/problem-generator`, `/wiki`, `/testprep`, `/designcraft` |

→ **"서비스 카드 섹션이 없다"는 초기 가정은 기각.** 실제 문제는 ① 첫 화면 노출 0개 ② 라벨↔URL 불일치 ③ 카드 1열 전폭으로 인한 스크롤 비용 ④ 모바일 13화면.

## 8. 링크 인벤토리 — `/`에서 도달 가능한 것 / 불가능한 것

`/`의 `a[href]` 고유 목적지 **30개** 실측.

### 8-1. 홈에서 도달 가능 (y 좌표 = 최초 등장)

| 목적지 | 최초 노출 y | 화면수 | 라벨 |
|---|---|---|---|
| `/wiki` | 13 (헤더) | 0 | "Buildit 모의고사" ⚠️ |
| `/home` | 13 (헤더) | 0 | "F2R" ⚠️ |
| `/chart` | 13 (헤더) | 0 | "BUILD IT CHART" ✅ |
| `/problem-generator` | 13 (헤더) | 0 | "BUILD IT WIKI" ⚠️ |
| `/builder` | **4014** (`#services` 카드) · 8391(`#cta`) | **4.5 / 9.3** | "서비스 입장하기" / "만들기" |
| `/testprep` | **5082** (`#services` 카드) | **5.65** | "서비스 입장하기" |
| `/designcraft` | **5705** (`#soon` 카드) | 6.3 | "DesignCraft 열어보기" (배지는 `곧 준비중`) |
| `/chart/educators/{89,80,88,92,95}` | 6411–7035 | 7.1–7.8 | CHART 순위 1~5 |
| `/chart#criteria` | 7253 | 8.06 | 평가 기준 보기 |
| `/privacy`, `tel:02-6324-1453`, `mailto:admin@feel2real.co.kr`, ftc.go.kr | 8546–8634 (푸터) | 9.5 | |
| `https://open.kakao.com/o/sMf4ifRi`, `mailto:woorinaesin@naver.com` | 2220 (`#wiki` 안) | 2.47 | 카톡/이메일 문의 |
| `https://blog.naver.com/apgujungeduholic/224435388122` | 2862 | 3.2 | 블로그 |
| `/register?returnUrl=%2F`, `/stdt_dashboard`, `/my/profile`, `/signin/reset/password` | 드롭다운/드로어 내부 (숨김) | — | 회원가입·학생 홈·프로필·계정 찾기 |

### 8-2. 홈에서 **어디서도 링크되지 않는** 운영/준비 경로

`/ledger` · `/easy` · `/newstart` · `/choonggi` · `/exam` · `/admissions/guide` · `/conversation/*` · `/lms/{handle}` · `/builder/entry` · `/wiki/originals` · `/wiki/analysis` · `/wiki/questions` · `/testprep/{practice,vocab,scores,learning}` · `/problem-generator/wiki/*` · `/chart/admin`

→ **Easy 장부·입시상담은 `#soon` 카드에 이름만 있고 링크는 없음**(`aria-disabled="true"`, 실측 y=5441/5798). AcademyCraft/StudyCraft도 동일.

## 9. 비로그인(익명) 접근성 실측 — `curl` 기준

### 9-1. 신 빌드 (배포 복구 후)

| 경로 | 상태 | 실제 화면 |
|---|---|---|
| `/` | 200 | 공개 랜딩 |
| `/wiki` | 200 | 공개 (타이틀 §4) |
| `/home` | 200 | 공개 |
| `/chart` | 200 | 공개 |
| `/testprep` | 200 | 공개 소개 |
| `/builder` | 200 | **공개 소개 페이지** ("BuildIt 홈페이지 제작 · feel2real"), CTA = "네이버 로그인하고 시작하기" / "아이디로 로그인하기" |
| `/problem-generator` | 200 | 공개 소개 ("내신 영어 문제지 제작기 · feel2real") |
| `/ledger` | 200 | 공개 소개 ("이지장부 · feel2real") |
| `/easy` | 200 | 공개 소개 ("Easy 회계·학생원장 · feel2real") |
| `/designcraft` | 200 | 공개 소개 ("DesignCraft · feel2real") |
| `/newstart` | 200 | 공개 ("새출발기금 부실차주 \| …") |
| `/exam` | 200 | 공개 ("내신 영어 문제지 제작 \| 내신위키") |
| `/choonggi` | 200 | 공개 |
| `/admissions/guide` | 200 | 공개 ("입시상담 가이드") |
| `/wiki/analysis` | **302** | `/?login=required&returnUrl=…` |
| `/wiki/questions` | **302** | `/?login=required&returnUrl=…` |
| `/conversation/report` | 200 | F2R 셸 렌더, 본문 "성장 추세 리포트 곧 제공돼요" |
| `/conversation/dashboard` | 302 | `/stdt_dashboard` |
| `/wemade` | 302 | `/wemade/login` |
| `/lms/demo` | 404 | |
| `/report` | **404** | |
| `/shorts` | **404** | |
| `/signin` | **404** | (로그인은 페이지가 아니라 **모달**. `button[data-auth-login]` → `#login-modal`) |
| `/login` | 200 | `WebMvcConfig:42-45`이 `/home`으로 처리 → F2R 셸 렌더 |
| `/consult` `/agent` `/admin` | **404** | |

**최종 재확인 (2026-10-16 11:3x KST, `curl` 전수)**: `/signin` `/report` `/shorts` `/consult` `/agent` = **404** · `/login` `/register` `/exam` `/admissions/guide` `/designcraft` `/ledger` `/builder` `/testprep` `/wiki` `/chart` `/problem-generator` `/home` `/newstart` `/choonggi` = **200**

### 9-2. 구 빌드 (배포 직전) — 대비 기록

`/problem-generator` 302→`/?login=required` · `/builder` 302→`/` · `/ledger` 302→`/?login=required&returnUrl=%2Fledger` · `/easy` **401** · `/report` `/shorts` `/consult` `/agent` `/admin` `/signin` 404

→ **배포 사이에 공개 정책이 바뀌었다**: 이제 보호 경로는 401/302 대신 **역할별 "프로그램 소개 페이지"(Program.java)를 200으로 렌더**한다. 즉 **개편안에서 "로그인 필요" 배지를 붙일지 말지 판단이 필요** — 소개 화면 자체는 누구나 볼 수 있으므로 "로그인 필요"가 아니라 **"로그인 후 이용"** 이 정확한 표현이다.

## 10. `#soon` 카드 실측 (5장)

| 카드 | 배지 | 링크 | `aria-disabled` | y |
|---|---|---|---|---|
| DesignCraft AI 문서 디자인 | 곧 준비중 | **`/designcraft` "DesignCraft 열어보기"** | null | 5441 |
| 입시상담 AI | 곧 준비중 | 없음 | true | 5441 |
| Easy 장부 | 곧 준비중 | 없음 | true | 5441 |
| AcademyCraft (LMS) | 곧 준비중 | 없음 | true | 5798 |
| StudyCraft (LMS) | 곧 준비중 | 없음 | true | 5798 |

→ DesignCraft만 예외: "준비중" 배지 + 실링크. 근거 커밋 `f8535b6f` (2026-10-15 17:38) "[FEAT] 홈페이지 DesignCraft 카드: 곧 준비중 뱃지 유지 + 공개 소개(/designcraft) 연결 (#712)" — **의도된 상태**이므로 개편 시 유지.

## 11. 결함 우선순위 요약

| 순위 | 결함 | 근거 | 사용자 피해 |
|---|---|---|---|
| **P0** | 첫 화면에 서비스 진입구 0개 (PC/모바일 모두) | §1, §6 | 스크롤 3.8~5.4화면 전까지 "무엇을 하는 사이트인지" 알 수 없음 |
| **P0** | 헤더 라벨↔목적지 교차 오류 (`BUILD IT WIKI`→제작기, `Buildit 모의고사`→위키) | §4 | 오답 페이지 도달, 신뢰 상실 |
| **P1** | 두 개의 홈 (`/` vs `/home`), 설명 없는 약어 "F2R" | §5 | 사이트 경계 상실 |
| **P2** | 모바일 드로어에 로그인/회원가입 재확인 없음 (단, **비로그인 헤더에는 노출됨** — §6 정정 참조) | §6 | 로그인 상태 사용자에게만 불편 |
| **P1** | 문서 길이 PC 8732 / 모바일 11005px | §2, §6 | 목적 정보 도달 비용 |
| **P2** | `#services` 1열 전폭 카드 → 4개에 1734px 소모 | §3 | 서비스 8개 동시 비교 불가 |
| **P2** | `/ledger`·`/easy`·`/admissions/guide`·`/newstart` 등 운영 경로가 홈에 링크 없음 | §8-2 | 존재하는 기능 미노출 |
| **P2** | `#hero` "만들기"가 form submit (링크 아님 → 우클릭/중클릭/새탭 불가) | §1 | 접근성·의도적 탐색 방해 |
| **P3** | `ul.nation-regions` 4번째 칩 잘림 (가로 스크롤 힌트 없음) | §6 | 지역 4종 노출 불명 |

## 12. 미검증 항목

| 항목 | 상태 |
|---|---|
| `/builder`, `/problem-generator` 등 소개 페이지 **로그인 후** 실제 기능 화면 | **미검증** — 실계정 로그인 후 흐름 미추적 (운영 데이터 변경 우려로 정지) |
| 헤더 4개 링크의 목적지별 내부 동선 | **부분** — 타이틀·문서높이만 실측, 내부 클릭 경로 미추적 |
| `#hero` 폼 submit 시 실제 동작 (빌더 생성 여부) | **미검증** — 제출하면 데이터가 생성될 수 있어 실행 안 함 |
| `문의하기` 모달(`#contactModal`) 내부 구성 | **미검증** — `getBoundingClientRect()` 0×0(숨김) 확인만 |
| 학년/역할별 헤더 노출 차이 (STUDENT vs TUTOR vs ADMIN) | **미검증** — STUDENT 세션 + 익명만 확인 |
| `{handle}.feel2real.co.kr` 서브도메인 학원 사이트 | **미검증** — 코드(`SubdomainAcademyFilter`)로만 존재 확인, 실도메인 미접속 |
| `/chart` 순위 데이터의 갱신 주기·알고리즘 | **미검증** |
| LMS(AcademyCraft/StudyCraft) 시범 계정 유무 | **미검증** — `/lms/demo` 404만 확인 |
