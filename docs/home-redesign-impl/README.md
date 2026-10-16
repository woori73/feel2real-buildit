# BuildIt 홈 첫 화면 개편(안 C) 구현 검증 기록

설계 기준: `docs/home-redesign-deliverables/` (Qoder, 커밋 `0d20467c`) — 04 개편 명세 · 05 와이어프레임 · 07 구현 인수인계. 설계 문서는 수정하지 않았다.
구현 기준 main: `69fe4bd356cf561a536ded77367f8f79bc6c7720`. 검증은 로컬(MySQL 8 + bootRun) 실제 브라우저(Playwright/Chromium)로 했다.

## 변경 파일
| 파일 | 내용 |
|---|---|
| `src/main/resources/templates/public/landing.html` | 헤더 라벨/href 교체·F2R 제거, 드로어 8행+회원가입, 첫 화면(`#homeFirst`: 칩·입력·소식·카드 8) 신설, `#hero` 폼 이관, `#services` 상단 앵커 |
| `src/main/resources/static/css/landing_home.css` (신규) | 첫 화면 레이아웃·카드·칩·드로어 배지. 기존 토큰만 재사용 |
| `LandingV3TemplateContractTest`, `LandingTemplateContractTest` | 바뀐 순서·라벨에 맞춰 단언 갱신 + 신규 계약 5건(카드 8장 링크, 폼 1개, 정적 소식, 드로어, 금지 경로) |

수정하지 않은 것: `*.java`(main), `application*.yaml`, `.github/**`, `static/js/**`, 각 서비스 템플릿·라우트·권한.

## 수용 기준(07 §3) 결과
| # | 결과 | 근거 |
|---|---|---|
| AC1 | 통과 | PC 1440×900: 카드 8장 `top` 395/645, `bottom` ≤878 (전부 폴드 안) |
| AC2 | 통과 | 모바일 390×844: 칩 스트립 + 카드 4장(top 407/604, bottom 772) |
| AC3 | 통과 | 내신위키 /wiki · 문제지 제작기 /problem-generator · CHART /chart · TestPrep /testprep |
| AC4 | 통과 | 카드 8개 href가 07 표와 일치, 로컬 GET 전부 200(+ /home /exam /register) |
| AC5 | 소유자 확인 필요 | 비로그인 제출 시 개편 전후 동일 동작(입력 보관 + 로그인 창)을 확인. 실제 제작 요청은 데이터 생성이라 실행하지 않음 |
| AC6 | 통과 | 비로그인 헤더 로그인·회원가입 / 로그인 시 계정 메뉴로 교체(PC·모바일) |
| AC7 | 통과 | `#nation #agent #hero #wiki #why #services #soon #chart-showcase #faq #cta` + `.svc-card` 4 + `.svc-anchors` 존재 |
| AC8 | 통과 | 카드 7·8 `준비 중` + `소개 보기`/`가이드 보기`, "이용 가능" 없음 |
| AC9 | 통과 | `form.v3-ask`·`data-buildit-prompt` 문서당 1개 |
| AC10 | 통과 | `landing.html`에 /report /shorts /consult /agent /lms /login 링크 없음 (`/signin/reset/password`는 기존 계정 메뉴 fragment) |
| AC11 | 통과(주석) | main 코드·yaml·workflow·js 변경 없음. 테스트 `.java` 2개는 새 순서·라벨 반영을 위해 갱신 |
| AC12 | 통과 | 드로어 서비스 8행 + 회원가입 + 문의하기, `data-auth-login` 중복 0, 행 높이 56 |

반응형: 1280/1100 4열, 1024/900 3열, 861 2열(내비 유지), 860 이하 햄버거+칩+카드 4장. 가로 넘침 없음.

## 설계 대비 판단한 점
- 모바일 카드 5~8번은 설계대로 숨기고(칩·드로어로 진입), "전체 서비스 8개 보기"는 `#services`로 연결했다.
- PC 입력/소식 열은 `1fr / 380px`(설계 620/380은 고정폭 예시)로 구현해 카드 영역 폭 1200과 맞췄다.
- 모바일 순서(칩→입력→카드→소식)를 위해 `#homeTop`/`.home-top-wrap`에 `display: contents`를 쓴다.

## 알려진 한계
- 문서 길이는 줄지 않고 늘었다: PC 8670→9626px, 모바일 11199→12441px, `#services` 시작 3455→4373px (길이 압축은 설계상 2단계).
- `LandingTemplateContractTest`(pin 스타일 1건)·`TemplateBrandingTest`(2건) 3건은 개편 전 main에서도 실패하는 기존 실패이며 CI 허용 목록 밖이다.
- `#soon`의 "Easy 장부(곧 준비중)" 카드는 설계대로 손대지 않았다. 첫 화면 이지장부 카드(/ledger)와 문구가 어긋나 보일 수 있어 소유자 판단이 필요하다.

캡처: `captures/` (before/after 첫 화면 PC·모바일, 모바일 드로어, 로그인 상태 헤더, 861/860/1024 폭)
