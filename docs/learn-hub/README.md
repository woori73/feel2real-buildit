# 학습 콘텐츠 모음 (`/learn`)

> 2026-11-22 대표 지시: "feel2real.co.kr에 있는 학습 콘텐츠를 새로운 페이지로 연결하여 모으기".
> 새 기능·새 DB 표 없이, 이미 있는 학습 화면으로 가는 **링크 모음** 한 장이다. 각 화면의 로그인 규칙은 바꾸지 않는다.

## 찾은 학습 콘텐츠 (코드에서 확인한 것만)

분량은 2026-11-22 `origin/main`의 마이그레이션·자료 파일에서 센 값이다. 화면의 숫자는 글로 적어 두지 않고 지금 올라와 있는 자료에서 센다(`LearnHubCatalog`).

| 묶음 | 화면 | 주소 | 대상 | 로그인 | 분량 |
|---|---|---|---|---|---|
| 내신 대비 | 내신 실전 연습(학교 고르기) | `/naesintest` | 중1·중2 | 목록은 누구나, 풀이는 로그인 | 3개 학교·학년 34회차(회차마다 40문항) |
| 내신 대비 | 압구정중 2학년 | `/naesintest/school/apgujeong-m2` | 중2 | 같음(1회는 로그인 회원 누구나, 2회부터 재원생) | 16회차 |
| 내신 대비 | 신구중 2학년 | `/naesintest/school/singu-m2` | 중2 | 같음 | 11회차 |
| 내신 대비 | 압구정중 1학년 | `/naesintest/school/apgujeong-m1` | 중1 | 같음 | 7회차 |
| 내신 대비 | 재원생 앱 | `/naesintest/app` | 재원생 | 원장이 발급한 접속 코드 | 배정된 시험 |
| 내신 대비 | 내신 문제 생성기 | `/problem-generator` | 중등 내신 | 소개는 누구나, 하위 화면은 로그인 | — |
| 수능·모의고사 | 2027 수능 영어 해설 | `/csat/2027-english` | 고3·수능 | 누구나(필기본 PDF는 로그인) | 45문항 |
| 수능·모의고사 | 모의고사 위키 | `/wiki` | 고1·고2·고3 | 첫 화면은 누구나, 자료는 로그인 | — |
| 예비고1 | 예비고1 디지털 모의고사 1~4부 | `/naesintest/pre-high` | 예비고1 | 목록은 누구나, 응시는 로그인 | 1부 기출 35회 · 2부 실전 독해 22회 · 3부 어법 10단원(단원마다 20문항 2부)+마무리 10회 · 4부 어휘 50회 |
| 예비고1 | 같은 화면의 5~7부 | `/naesintest/pre-high` | 고2 | 같음 | 5부 기출 36회 · 6부 실전 독해 1회 · 7부 어휘 40회 |
| iBT·SAT·토익 | TestPrep 홈 | `/testprep` | iBT·SAT·토익 | 누구나 | — |
| iBT·SAT·토익 | iBT 모의고사 | `/testprep/practice` | iBT | 로그인 | 9세트 |
| iBT·SAT·토익 | 디지털 SAT | `/testprep/sat` | SAT | 로그인 | 4세트 |
| iBT·SAT·토익 | 토익 | `/testprep/toeic` | 토익 | 로그인 | 3세트 |
| iBT·SAT·토익 | 어휘 퀴즈 | `/testprep/vocab` | iBT | 로그인 | 3세트 |
| iBT·SAT·토익 | 영역별 가이드 | `/testprep/guide` | iBT | 로그인 | — |
| 입시 정보 | 「바뀌는 입시」 글 | `/admissions/articles` | 예비고1 이상 | 누구나 | 등록된 글 9편(3편은 11월 26일 공개 예정) |
| 입시 정보 | 입시상담 AI | `/admissions/guide` | 예비고1 이상 | 누구나 | — |

- 1부·5부(기출) 자료는 저장소가 아니라 서버의 바깥 폴더에 있다. 자료가 없는 곳(로컬 등)에서는 그 숫자를 화면에 적지 않는다.
- 홈 화면 표가 예비고1 2부를 "10회"로 적고 있던 것을 올라와 있는 자료에 맞춰 **22회**로 고쳤다(대표 지시 2026-11-22). 예비고1 화면 자체는 원래 자료에서 세어 22회로 보인다.

### 모음에 넣지 않은 화면

| 화면 | 주소 | 넣지 않은 까닭 |
|---|---|---|
| 내신Wiki 승인 완성본 | `/naesin-wiki/published` | 로그인 회원 전용 + 서비스 안 확인, 검색 제외(noindex) 화면 |
| 시험 분석 | `/exam-analysis` | 화면은 공개지만 자료는 로그인. 학습 콘텐츠보다 분석 도구에 가까움 — 넣을지 대표 결정 |
| 문법 문항 | `/grammar-items` | 로그인 + 별도 권한 확인 |
| 학원 LMS·수업 커뮤니티 | `/lms/{handle}`, `/lessons/{id}/community` | 학원·수업마다 주소가 달라 한 장의 링크로 묶을 수 없음 |
| TestPrep 내 점수·내 학습·풀이·해설 | `/testprep/scores` 등 | 개인 기록 화면 |
| 수업 준비 › 학습지 | `/dashboard/worksheets` | 선생님용(PR #1173) |

## 화면

- 묶음 5개(내신 대비 / 수능·모의고사 / 예비고1 / iBT·SAT·토익 / 입시 정보)의 카드. 카드마다 대상 학년, 분량, 로그인 안내(누구나 = 초록, 로그인·접속 코드 = 노랑).
- 거르기: 학년(전체·중1·중2·예비고1·고2·고3·수능·iBT·SAT·토익), 학교(전체·압구정중학교·신구중학교). 서버 요청 없이 화면의 카드만 숨긴다. 맞는 카드가 없으면 안내 문장.
- 공개 layout(`layouts/public`) 사용, 사이트맵에 `/learn` 추가.
- 홈(`public/landing.html`): 상단 「학습 콘텐츠」 펼침 메뉴와 전체 메뉴 「③ 학습 콘텐츠」에 링크 한 줄씩만 추가.
- 캡처: [captures/](captures/) (PC 1280px, 휴대폰 375px, 휴대폰에서 거른 모습).

## 검증

- `LearnHubCatalogTest`(모든 카드 링크가 실제 등록된 GET 주소인지 소스와 대조 포함) · `LearnHubPageControllerTest` · `LearnHubTemplateContractTest` · `src/test/js/learn_hub.test.js`
- 기존 홈 계약: `LandingEdunovaTemplateContractTest`, `src/test/js/home_*.test.js` 59개, `PublicAnalyticsLayoutContractTest`, `SitemapControllerTest` 통과
- 로컬 부팅 뒤 카드 링크 17개 주소 응답 확인(공개 12개 200, 로그인 필요 5개는 로그인 안내로 302), PC·휴대폰 폭 가로 넘침 없음
