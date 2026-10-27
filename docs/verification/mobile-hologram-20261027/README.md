# Feel2Real 모바일 UI 수정·AI 비서 회귀 검증

작업 기준일: 2026-10-27 (한국 시간). 기준 `main`: `b8242e87cf9fee596c2a1122f9b9f88144caa45c` (#911).
브랜치: `fix/mobile-hologram-assistant-20261027`. 제품·테스트 수정 커밋: `83d1819`.

## 1. 완료 범위와 근거

이름표 잘림과 신청 타일 4개의 잘못된 안내 연결을 최소 수정했다. 승인된 대시보드 흐름에 맞춰 기존 AI 비서 E2E를 수정했고, 기존 12개 및 신규 반응형 8개가 로컬 실제 앱에서 모두 통과했다. 유료 AI 모델·영상 생성, 운영 배포·운영 DB 변경·main 병합은 실행하지 않았다.

[이전 검증 보고서](https://chatgpt.com/space/page_5877ca1f0c148191b13cac2ca1a4106d)의 본문·오류 분석·권고 및 첨부 이미지 7장을 읽었다. 원본 PNG 7장의 바이트와 SHA-256을 별도 로컬 증거 폴더에 보존했다. 해당 Page의 ZIP 원본 로그 묶음은 연결 도구가 ZIP 바이트 읽기를 지원하지 않아 열람하지 못했다. 이를 확인한 것으로 표시하지 않고, 동일 main의 실제 앱을 재실행한 원본 로그·결과를 이 보고서와 함께 보존했다.

## 2. 원인·수정 및 영향 범위

### 이름표

`.si-home-stage`의 모바일 `height:clamp(320px,44vh,420px)`와 `overflow:hidden`, `auto 1fr auto` 그리드가 함께 작동한다. 360/390px에서 타일 문구가 여러 줄이 되면 가운데 `1fr` 행의 자동 최소 크기가 커지고, 상단 행+타일 행+이름표 행 합계가 지정 높이를 넘어 마지막 행이 잘린다. perspective transform·이름표 자체의 잘못된 위치 지정이 원인은 아니었다.

모바일 규칙만 `height:auto; min-height:clamp(320px,44vh,420px)`로 바꿔 기존 최소 무대 높이를 유지하면서 내용이 더 크면 늘어나게 했다. overflow, 열 비율, 이미지/video의 object-fit, 홀로그램 transform·glow·주사선·애니메이션, 글자 크기·버튼 터치 크기를 유지했다. 430px·데스크톱의 무대 높이는 그대로다.

| 화면 / 대체 글꼴 | 수정 전 텍스트 하단 | 수정 후 | 무대 높이 전 → 후 | 가로 넘침 |
|---|---|---|---|---|
| 360×800 | 무대 밖 11.12px | 안쪽 여유 13px | 352 → 376.13px | 0px |
| 390×844 | 무대 밖 9.39px | 안쪽 여유 13px | 371.36 → 393.75px | 0px |
| 430×932 | 안쪽 여유 13px | 안쪽 여유 13px | 410.08 → 410.08px | 0px |
| 1440×1050 | 안쪽 여유 13px | 안쪽 여유 13px | 470 → 470px | 0px |

이 수치는 이번 Chromium·글꼴 환경의 직접 측정값이다. 이전 보고서의 13.12/11.39px와 동일 환경이라고 가정하지 않았다. 실제 Pretendard 로드 상태도 별도 4개 케이스에서 검사했고, 수정 후 이름표·타일 겹침·가로 넘침이 없었다. 각 JSON에 그리드 행 높이·문자 Range 좌표·타일 변환/터치 가능 여부와 메뉴별 실제 응답을 담았다.

### 홀로그램 메뉴 비교표

신청 중심 문구는 #910/#911 및 [주문·자료 제출 명세](../../exam-pass-order-intake-spec-20261027.md)에서 확인했다. 명세의 새 주문 폼/API는 제안 단계여서 임의로 연결하지 않았다. 현재 실제 홈페이지가 사용하는 카톡·이메일 접수 경로에 연결한다.

| 표시 문구 | 수정 전 intent / 안내·링크 | 수정 후 intent / 실제 안내·링크 | 판단 |
|---|---|---|---|
| 기말고사 이용권 | english / 문법 문제은행 `/grammar-items`, 위키 `/wiki` | exam / 기존 20,000원·3주·6회·240문항 및 학교·학년·범위 안내, 카톡·이메일 | 승인 목적 명확, 수정 |
| 시험 범위 제출 | wiki / 모의고사위키 `/wiki` | exam / 기출·교사 힌트 등 제출 자료 안내, 카톡·이메일 | 승인 목적 명확, 수정 |
| 학부모·학생 신청 | studio / 관리자용 `/ai-agent-studio` | exam / 기존 신청·자료 안내, 카톡·이메일 | 승인 대상·접수 방식 명확, 수정 |
| 접수 방법 | builder / 학원 홈페이지 `/builder` | exam / 카톡·이메일 접수 및 별도 결제 확인 | 승인 목적 명확, 수정 |

카톡 실제 URL은 `https://open.kakao.com/o/sMf4ifRi`, 이메일은 `mailto:woorinaesin@naver.com`이다. 기존 `맞춤 문제 신청`·`개별 신청`은 exam을 유지했고 같은 이메일 선택지를 추가했다. 공개 서비스의 영어/위키/홈페이지/Studio 규칙과 기존 메뉴·대표 배너·신청 CTA·적중 사례 링크를 유지한다. 네 타일 자체는 페이지를 즉시 이동시키는 링크가 아니라 **AI 비서의 안내와 접수 링크를 여는 버튼**이라는 기존 구조를 보존했다. 목적지 판단 보류 항목은 없다.

### AI 비서 E2E

동일 기준 코드에서 기존 결과 **10 통과·1 실패·1 미실행**을 재현했다. 실패는 `#uaTour` 자동 표시 기대였다. [대시보드 승인 설계](../../design/agent-dashboard/README.md)와 설계 커밋 `4826657`은 첫 방문에 대화로 안내하고 사용법은 메뉴에서 연다고 명시한다. `unified_assistant.js`도 tour=false이며 `uaResume`은 언제나 1단계부터 여는 구현이다. 자동 팝업을 다시 추가할 근거가 없으므로 제품 코드를 바꾸지 않았다.

기존 테스트를 다음 실제 조작으로 갱신했다.

- 초기 안내창 숨김 → 사용법 버튼 → 다음 → 실제 학생·반 메뉴 강조·표시 확인
- 새로고침 후 안내창 숨김과 서버 저장 단계 유지 확인 → 버튼으로 1단계부터 다시 열기 → 닫기·재열기
- 자료 첨부는 전체 대시보드, 대화와 초안 승인은 AI 대화 화면으로 실제 메뉴 전환
- 기존 CSV 오류 행·증빙·승인 전 불변·승인 후 재조회·중복 방지 조건을 유지
- ADMIN 모바일 승인·저장 검사를 기존 390px에서 360/430px까지 확장, TUTOR 390/1440px 검사 유지
- serial 실패 때문에 미실행됐던 마지막 `/home` 환영 링크까지 실행

신규 반응형 검사의 첫 수정 후 실행은 19 통과·1 실패였다. 이전 안내에도 같은 `240문항`이 있어 입력 직후의 응답 대기가 먼저 통과하는 테스트 경쟁 조건이었다. 새 응답 개수 증가를 먼저 기다리도록 강화해 모두 재실행했다. 기존 테스트를 삭제하거나 숨은 요소 강제 클릭·조건 완화로 통과시키지 않았다.

## 3. 화면 증거

| 폭 | 수정 전 | 수정 후 |
|---|---|---|
| 360px 이름표 | [무대](before/360-fallback-stage.png) · [첫 화면](before/360-fallback-home.png) | [무대](after/360-fallback-stage.png) · [첫 화면](after/360-fallback-home.png) |
| 390px 이름표 | [무대](before/390-fallback-stage.png) · [첫 화면](before/390-fallback-home.png) | [무대](after/390-fallback-stage.png) · [첫 화면](after/390-fallback-home.png) |
| 430px 전체 | [전체 화면](before/430-fallback-full.png) | [전체 화면](after/430-fallback-full.png) |
| 데스크톱 | [첫 화면](before/1440-fallback-home.png) | [첫 화면](after/1440-fallback-home.png) |
| 실제 웹폰트 390px | [첫 화면](before/390-pretendard-home.png) | [첫 화면](after/390-pretendard-home.png) |
| 신청 메뉴 안내 | [잘못된 접수 방법 응답](before/390-fallback-intake.png) | [수정된 응답](after/390-fallback-intake.png) |

| AI 비서 | 저장 결과 캡처 |
|---|---|
| 데스크톱 | [동일 대화의 자료·장부 결과](assistant/admin-desktop-unified-chat-1440.png) |
| 360px | [승인·저장 결과](assistant/admin-mobile-chat-360.png) |
| 390px | [승인·저장 결과](assistant/admin-mobile-chat-390.png) |
| 430px | [승인·저장 결과](assistant/admin-mobile-chat-430.png) |

## 4. 실행 환경과 데이터 보존

BuildIt Spring Boot 4.0.6은 위 기준 main/수정 브랜치, AcademyCraft는 기존 E2E가 지정한 `115477f7ea0afa27dd69d9ae12318261c47a5a82`를 별도 checkout에서 빌드했다. 기존 프로젝트·Studio 자료를 수정하지 않았다.

신규 `unified-ai-mysql` MySQL 8.4 컨테이너의 `127.0.0.1:13308`, 전용 `f2r_unified_test`·`ac_unified_test`만 사용했다. 두 앱은 127.0.0.1:8080/8082에 바인딩했다. 기존 개발 DB 3308·운영 DB는 사용하지 않았다. 마이그레이션·fixture·학생/반/출결/상담/장부 테스트 쓰기는 이 신규 합성 DB에만 수행했다.

Playwright 1.64.1 Chromium, 360×800·390×844·430×932·1440×1050, DPR 1, ko-KR, Asia/Seoul이다. 모바일 UA·터치를 사용하고 글꼴 대체/실제 Pretendard Variable v1.3.9를 분리했다. 웹폰트는 버전 고정 공개 WOFF2를 로컬 요청으로 제공하고 FontFace의 loaded 상태를 검사했다. Russo One은 대체 글꼴이다. 브라우저 외부 문서·통계 POST를 막고 신청 제출·메일 전송·결제는 실행하지 않았다.

`buildit.unified-assistant.model-analysis-enabled=false`, `anthropic.enabled=false`를 명시했다. 홈은 실제 클라이언트 안내 규칙, 대시보드는 실제 두 로컬 앱/API/DB의 규칙 분석과 저장 흐름을 검증했다. **실제 외부 모델의 응답 품질·음성·립싱크를 검증한 결과가 아니다.**

## 5. 테스트·빌드·정적 검사

| 구분 | 기존 / 신규 | 결과 |
|---|---|---|
| 지정 홈·단계 안내·계약 단위 | 기존 22 | 22 통과 |
| 사용법 안내 규칙 단위 | 기존 5 | 5 통과 |
| 메뉴 4개 의미·접수 경로 | 신규 4 | 4 통과 |
| 영상 전환·대시보드 표현 | 기존 25 | 25 통과 |
| CI JS allowlist | 기존 220 + 신규 4 | 224 통과 (위 관련 단위를 포함한 집계, 중복 합산하지 않음) |
| 통합 비서·DashboardWidgetController JUnit | 기존 90 | 90 통과, 실패·skip 0 |
| 통합 비서 Playwright | 기존 12 | 12 통과, 실패·미실행·flaky 0 |
| 홈 반응형 Playwright | 신규 8 | 8 통과, 실패·미실행·flaky 0 |
| bootJar 및 `gradlew build -x test` | 빌드 | 성공. 전체 JUnit suite를 실행했다는 의미가 아님 |
| 정적 확인 | JS `node --check`, 두 CI YAML 파싱, `git diff --check` | 통과 |

12개+8개 전체 20개를 한 번에 통과했다. 이후 웹폰트의 실제 loaded 상태 검사를 강화한 신규 8개를 추가로 재실행했다. 저장소의 로그는 ANSI 색상·행 끝 공백만 정리했고 원본 출력은 별도 검증 폴더에 유지했다. 실행별 통과·실패·미실행은 [results-summary.json](results-summary.json), 로그는 [logs](logs/)에 보존했다. 수정 전 새 회귀 검사의 8개 실패와 중간 실행의 1개 실패도 숨기지 않았다.

전용 GitHub workflow `Mobile UI and assistant regression`은 같은 두 로컬 앱·별도 MySQL·폰트·20개 E2E를 실행하고 캡처·좌표·결과 JSON을 artifact로 보존한다. 기존 Build Check에도 신규 단위 4개를 추가했다. CI 최종 성공/실패와 실행 링크는 이 브랜치 PR 본문·Checks 및 최종 제출 보고에 기록한다. CI가 끝나기 전에 완료로 표시하지 않는다.

## 6. 검증 구분과 남은 제한

| 검증 | 상태 |
|---|---|
| 로컬 실제 코드·빌드·두 앱·격리 DB | 실행·통과 |
| Chromium 모바일/데스크톱 에뮬레이션 | 실행·통과 |
| 운영 사이트 현재 화면·배포 SHA | 실행하지 않음. 로컬 수정이 운영에 적용됐다고 주장하지 않음 |
| 실제 iOS/Android 기기·화상 키보드 | 실행하지 않음 |
| 실제 공개/비공개 캐릭터 영상·사진 | 자료 미연결 표식 조건. 실제 영상 비교는 미검증, 공용 전환·상태 규칙 단위는 통과 |
| 실제 외부 AI·음성·영상 생성 | 실행하지 않음 |

남은 제품 변경 판단 보류는 없으며, 운영 반영 전 실기기·실제 캐릭터 자료·실제 배포 환경 글꼴/캐시 확인이 필요하다. JS/CSS query 버전을 올려 이번 두 정적 자원이 기존 캐시와 구분되게 했다.

## 7. 수정 파일

- 제품: `src/main/resources/static/css/f2r_home_agent.css`, `static/js/f2r_home_agent.js`, `templates/public/landing.html` (위 경로는 resources 기준)
- 단위: `src/test/js/f2r_home_agent.test.js`, 신규 `home_hologram_intake.test.js`
- 브라우저: `e2e/unified-assistant/unified.spec.js`, 신규 `home-responsive.spec.js`, 재현 README
- CI: 기존 `build-check.yml`, 신규 `mobile-assistant-regression.yml`
- 증거: 이 디렉터리의 보고서·원본 PNG·좌표/응답 JSON·요약/로그

## 8. 승인·재개

허용된 로컬 수정·격리 테스트·브랜치 푸시·PR 생성까지만 수행한다. 운영 배포·main 병합·운영 DB 변경은 별도 승인 대상이다. PR을 자동 병합하지 않는다.

재현은 [E2E README](../../../e2e/unified-assistant/README.md)를 따른다. 재개 시 최신 원격 main/브랜치와 PR Checks를 먼저 확인하고, 실제 운영 DB 대신 새 전용 컨테이너를 사용한다. 유료 AI 분석 비활성 인자를 유지하고 원본 증거·Studio 자료를 덮어쓰지 않는다. 로그인 쿠키·storageState·인증된 실패 trace는 공개 보고서/CI artifact에 포함하지 않는다.
