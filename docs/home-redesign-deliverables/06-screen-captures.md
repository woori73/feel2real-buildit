# 06. 화면 캡처 및 주석 이미지 매니페스트

> 산출물 6/6 · 캡처 위치: `./captures/` · 2026-10-16 (KST)
> 주석 이미지는 실제 렌더된 DOM의 `getBoundingClientRect()` 좌표에 맞춰 번호 상자를 삽입한 뒤 뷰포트를 캡처한 것. 좌표는 각 문서(01·02)에 px로 기록되어 있어 재현 가능.

## 1. 주석 이미지 (지시문 §2 "주요 영역에 번호를 표시")

| 파일 | 뷰포트 | 대상 | 번호 영역 | 근거 문서 |
|---|---|---|---|---|
| `annot-megastudy-pc-1440-fold.png` | 1440×900 | `megastudy.net/megastudy.asp` | 1 상단 롱배너 · 2 학부탭 · 3 유틸(D-day/로그인/회원가입/학원바로가기) · 4 로고밴드 · 5 **GNB 7개直达** · 6 관통배너 · 7 중앙 배너 스와이퍼 · 8 **HOT PICK** · 9 **아이콘 바로가기 10종** · 10 과목탭+강사 · 11 좌측 고정레일 | 01 §1–3 |
| `annot-megastudy-mb-390-fold.png` | 390×844 | `m.megastudy.net/mobile/smart/main.asp` | 1 앱설치배너 · 2 헤더(햄버거·로고·AI통합검색·알림) · 3 **카테고리 가로탭 5종** · 4 메인 배너 스와이퍼(9장) · 5 공지strip+타이틀 · 6 추천 섹션 · 7 **이벤트 칩 가로 캐러셀 22개** · 8 **고정 바텀 내비+FAB** | 01 §5 |
| `annot-buildit-pc-1440-fold.png` | 1440×900 | `feel2real.co.kr/` | 1 HEADER(`#biNav`) · 2–5 헤더 4개 알약과 **실제 목적지** · 6 계정메뉴 · 7 영웅 헤드라인 · 8 지도+목업 그래픽(링크 아님) · 9 **지역칩 button/href 없음** | 02 §1 |
| `annot-buildit-pc-1440-services.png` | 1440×900 (y=3545~) | `#services` | 1 eyebrow · 2 h2 · 3 앵커칩 5 · 4~7 `.svc-card` 4장(y=3759/4115/4471/4827) · 8 `서비스 입장하기` | 02 §3 |
| `annot-buildit-mb-390-fold.png` | 390×844 | `feel2real.co.kr/` | 1 헤더(로고/계정/햄버거) · 2 타이틀 · 3 AI AGENT 서브카피 · 4 **지역칩 href 없음** · 5 학원 카드 목업 · 6 노트북·폰 목업(폴드에서 잘림) · **파란 점선 = 폴드 경계 844px, 그 안에서 서비스 진입 링크 = 로고 1개뿐** | 02 §6 |
| `annot-buildit-mb-390-menu-open.png` | 390×844 | `#biMenu` 드로어 | 1 `Buildit 모의고사`→**/wiki**(카드는 "내신위키") · 2 `F2R`→**/home**(두 번째 홈) · 3 `BUILD IT CHART`→/chart ✓ · 4 `BUILD IT WIKI`→**/problem-generator** ✕✕ · 5 `문의하기`=버튼 · **주황 상자**=드로어 헤더(인증 행 없음, 로그인은 헤더 `.auth-actions`에만) · 하단 빨간 블록 = 링크 4+버튼 1, `/builder`·`/testprep`·`/ledger`·`/designcraft`·`/admissions/guide` 미노출, 414px 이하 공백 | 02 §4·§6 |
| `buildit-mb-390-anon-header.png` | 390×844 | `feel2real.co.kr/` 헤더 (비로그인 상태 렌더 재현) | **정증 이미지**: `.auth-actions[hidden]`을 제거해 확인 — 로고(x20) · **로그인(x156,65×39)** · **회원가입(x230,73×40)** · 햄버거(x311,44×44)가 한 행에 들어간다. → "드로어에 인증 행 없음"은 로그인 불가 결함이 아님 (02 §6 정정) | 02 §6 |

## 2. 원본 캡처 (주석 없음 — 비교·재검증용)

> **세션 상태 주의**: BuildIt 캡처 대부분은 Playwright 프로필이 **STUDENT로 로그인된 상태**에서 찍혔으므로 헤더가 `👤` 트리거로 렌더된다. **비로그인 헤더**(로그인·회원가입 버튼)는 `buildit-mb-390-anon-header.png`와 `curl` HTML(`.auth-actions[hidden]`)로 별도 확인했다. 두 상태를 혼동하지 말 것.
> `annot-buildit-mb-390-fold.png`의 4번 상자 우측에 보이는 세로 스크롤바는 페이지 것이 아니라 `ul.nation-regions` 내부 가로 스크롤러(scrollWidth 334 / clientWidth 280)의 것이다 — 02 §6.
> **메가스터디 캡처의 팝업**: `annot-megastudy-pc-1440-fold.png`에 좌측 `MEXX 2027 윈터스쿨`, 우측 `미션 클리어하고 포토 키링 받자!`, 좌하단 `휘학 고석용 1등급 실전 훈련` 레이어가 보인다. 이들은 **접속 시점에 뜬 광고 팝업**으로 IA 구성 요소가 아니다(주석 번호에 포함하지 않음). 11번 라벨이 1번 라벨과 좌상단에서 겹치는 것은 표시상 문제일 뿐 실측 좌표와 무관.

### 2-1. 메가스터디

| 파일 | 내용 |
|---|---|
| `megastudy-pc-1440-fold.png` | 첫 화면 (스크롤 0) |
| `megastudy-pc-1440-s900.png` | scrollY=900 — **GNB 상단 고정 + 배경색 변화** 검증 |
| `megastudy-pc-1440-s1500.png` | scrollY=1500 — `.bx--subcontent--info` |
| `megastudy-pc-1440-s2100.png` | scrollY=2100 — 앱 서비스 관모음 |
| `megastudy-pc-1440-full.png` | 전체 (docH 3346) |
| `megastudy-pc-1440-detail-entinfo.png` | **GNB `입시정보` 클릭 후** — 1클릭 MPA 이동 검증 (`메가스터디 :: 입시정보`) |
| `megastudy-mb-390-fold.png` | 모바일 첫 화면 |
| `megastudy-mb-390-menu-open.png` | 드로어 **2단 마스터–디테일** (좌 180px 대분류 / 우 소분류) |
| `megastudy-mb-390-full.jpeg` | 모바일 전체 (docH 4325) |

### 2-2. BuildIt

| 파일 | 빌드 | 내용 |
|---|---|---|
| `buildit-pc-1440-fold.png` | **구 빌드** (10:01, docH 6998) | `#services` **없음** — 배포 전 상태 기록 |
| `buildit-pc-1440-fold-v2.png` | **신 빌드** (10:05, docH 8732) | 첫 화면 (전후 비교의 "후") |
| `buildit-pc-1440-services.png` | 신 | `#services` 섹션 |
| `buildit-pc-1440-full-v2.jpeg` | 신 | 전체 |
| `buildit-mb-390-fold.png` | 신 | 모바일 첫 화면 (docH 11005) |
| `buildit-mb-390-menu-open.png` | 신 | 드로어 원본 |
| `buildit-mb-390-services.png` | 신 | 모바일 `#services` (y=4523) |
| `buildit-wiki-pc.png` | — | `/wiki` 실측 (타이틀 "BuildIt Wiki · 고1·고2·고3 영어 모의고사 자료를 함께", docH 3119) |
| `buildit-home-f2r-pc.png` | — | `/home` 실측 — **자체 5개 내비**(학원 찾기/시험 분석/모의고사 Wiki/블로그/커뮤니티) = 두 번째 홈 증거 |

## 3. 캡처 방법 (재현 절차)

```
1. 뷰포트 설정      browser_resize({width, height})
2. 이동             browser_navigate({url})
3. (스크롤 영역)    browser_evaluate: document.documentElement.scrollTop = N
                    → 별도 evaluate 호출에서 window.scrollY 재확인 (1회 호출로 확인 불가)
4. 좌표 수집        browser_evaluate: getBoundingClientRect() + scrollY, 정수 반올림
5. 주석 레이어      browser_evaluate:
                    div#__anno { position:absolute; inset:0; pointer-events:none; z-index:2147483647 }
                    박스 = border:2px solid #ff2d55, 라벨 = background:#ff2d55;color:#fff;font:700 10px/13px
                    ※ position:fixed 레이어(드로어 등)는 좌표를 뷰포트 기준으로 측정
6. 캡처             browser_take_screenshot({filename, scale:"css", type:"png"})
                    ※ scale 필수. "css" = 뷰포트 크기 그대로
7. 검증             Read 도구로 PNG를 직접 열어 라벨 겹침·누락 확인
```

**알아두면 좋은 함정**
- `body{overflow-x:hidden}` (02 §6) — 페이지 가로 스크롤은 발생하지 않음. `scrollWidth`가 `innerWidth`보다 15px 작은 것은 **스크롤바 폭**이지 결함이 아님.
- 메가스터디 PC는 `meta viewport width=1480` 고정 → 1440 뷰포트에서 우측 40px 잘림. 캡처에 우측 강사 프로모(x=1265)는 보이되 일부 잘림.
- 모바일은 **별도 호스트**(`m.megastudy.net`)가 302 → `/mobile/smart/main.asp`. 같은 URL을 좁은 뷰포트로 보는 것과 결과가 다름.
- Playwright 프로필이 STUDENT로 로그인된 상태 → **비로그인 게이트는 `curl`로 별도 검증** (02 §9). 헤더 렌더 차이도 `curl` HTML로 확인(`.auth-actions[hidden]` + JS 토글).
