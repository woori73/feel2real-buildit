# EASY 개발 — ChatGPT 클라우드 Work 인수인계

기준일: 2026-10-12 (KST)  
저장소: `woori73/feel2real-buildit`  
기준 브랜치: `main`  
기준 SHA: `e4bec313b2d533e2117e30eedf17a1595a80143d`  
인계 브랜치: `chore/easy-cloud-work-handoff-20261012`

## 1. 이번 인계의 범위

- 최신 GitHub `main`을 원격 기준으로 확인했다.
- 로컬 후보 체크아웃을 조사했지만 최신 EASY 전용 미커밋 변경은 없었다. 발견된 더티 체크아웃은 과거 Wiki/진단 작업이므로 손대지 않았다.
- 애플리케이션 코드, DB, Flyway, 운영 환경은 변경하지 않는다.
- REA 조사 결과와 설정 절차, EASY/이지샵 실사 문서만 작업 브랜치에 추가한다.
- `main` 병합, PR 생성, 운영 배포, 운영 DB 쓰기는 이 인계에 포함하지 않는다.

## 2. 반드시 유지할 운영 원칙

작업 전 아래 문서를 순서대로 읽는다.

1. `AGENTS.md`
2. `docs/operating-principles/MASTER_OPERATING_PRINCIPLES.md`
3. `docs/operating-principles/EASY_LEDGER_OPERATING_PRINCIPLES.md`
4. `docs/WORK_GIT_WORKSPACE_WORKFLOW.md`
5. `docs/INDEX.md`와 작업별 연결 문서

EASY의 고정 원칙은 다음과 같다.

```text
COLLECT → LEDGER SAVE → CLASSIFY
```

- 불확실한 거래도 누락하지 않고 장부에 저장한 뒤 `REVIEW_REQUIRED` 등으로 검토한다.
- EasyShop의 과거 실제 분류를 우선 기준으로 사용한다.
- 학원(`ACADEMY`)과 출판사(`PUBLISHING`) 장부를 분리한다.
- AI는 제안만 하며 사용자 승인 전에는 확정 분개·세무판정·신고 제출을 하지 않는다.
- 실제 자격정보, 로그인 세션, 쿠키, 금융 원문, 사업자 개인정보를 GitHub에 커밋하지 않는다.

## 3. 합의된 개발 순서

기존 로드맵의 번호를 유지하되 실제 실행 순서는 아래와 같이 고정한다.

1. **2단계 — 장부 MVP**: 수입·비용, 파일/CSV, 실제 복식부기, 검토함, 증빙 상태, 월 대시보드
2. **3단계 — AI 승인형 분류**: 계정 추천, 반복거래 학습, 이상 탐지, 근거와 감사로그, 사용자 승인
3. **4단계 — 외부수집 1차**: 정식 공급자 1~2개, 동의·토큰·재시도·대사
4. **1단계 — SaaS 기반 전환**: 사용자-사업체 membership, 사업자 온보딩, tenant 권한·격리

`2 → 3 → 4 → 1` 순서를 임의로 재배치하지 않는다. 다만 보안·법률 검토는 각 단계의 출시 게이트로 계속 적용한다.

## 4. 현재 구현 상태

### 코드에서 확인된 기반

- Java 21, Spring Boot 4.0.6, MySQL, Flyway, Gradle Wrapper
- `easy_businesses` 중심의 학원/출판사 사업체 분리
- Admin EASY API 14개 Controller, 조사 시점 기준 96개 endpoint
- 거래·계정·분류규칙·import staging·검토 큐·수정로그·분개·원장·시산표·결산 기반
- EasyShop import pipeline과 원본/외부계정/fingerprint 보존 구조
- 카드/은행/카드매출 수집용 adapter·connection·run 기반
- 실계정 복식부기 A1, 총계정원장/시산표/마감 A2, 세무 reference 비교 A3 기반
- 거래 분류 변경은 원본과 기존 분개를 보존하는 정정 경로 사용

세부 근거는 `docs/easy/research/2026-10-12/02_easy_code_db_audit.md`를 따른다. 코드에서 확인되지 않은 기능은 완료로 간주하지 않는다.

### 미완료 또는 제한된 영역

- 일반 사업자용 가입·사업체 membership·tenant 권한 모델: 신규 설계 필요
- 외부 금융 자동수집: adapter 기반은 있으나 공식 계약·동의·토큰·실운영 검증이 필요
- CREFIA 실연동: 승인된 공식 endpoint와 회계 lifecycle 없이는 fail-closed
- AI: 규칙·과거이력·검토 흐름은 있으나 독립 AI 제안/결정/근거 버전 계층은 확장 필요
- 증빙 파일 업로드·OCR·장기보존: 신규 개발 필요
- 부가세·원천세·종합소득세 실제 신고서 생성·제출: 미구현, 법률·세무 검토 선행
- 급여·직원·4대보험: 신규 개발 필요
- 운영 사용자 화면: 관리자 중심이므로 초보 사업자용 UX 신규 설계 필요

## 5. 클라우드 Work 재개 절차

### Git 상태 확인

```bash
git fetch origin --prune
git switch chore/easy-cloud-work-handoff-20261012
git status
git rev-parse HEAD
git log --oneline -5
```

GitHub의 브랜치 tip과 로컬 `HEAD`가 같은지 확인한 뒤 작업한다. stale한 `origin/main`을 최신이라고 가정하지 않는다.

### 개발 환경

- JDK 21
- Docker / Docker Compose
- Git
- Node.js는 REA를 사용할 때만 필요하며 22.19+, 24.11+ 또는 26+ 계열을 사용한다.

로컬 앱 실행:

```bash
./scripts/run-local.sh
```

스크립트는 로컬 MySQL `localhost:3308/f2r_db`를 생성·재사용한다. 비밀값은 저장소에 넣지 말고 실행 환경의 secret 기능 또는 로컬 전용 환경파일을 사용한다.

### 빌드·테스트

문서만 변경한 이번 인계는 코드 테스트 대상이 아니다. 후속 코드 변경 전후에는 최소 아래 명령을 실행한다.

```bash
./gradlew compileJava compileTestJava --no-daemon
./gradlew test --tests "f2r.tutor_solution.domain.easy.**" --no-daemon
./gradlew test --no-daemon
./gradlew bootJar --no-daemon
```

Windows PowerShell에서는 `./gradlew` 대신 `./gradlew.bat`를 사용할 수 있다. MySQL/Flyway 통합 검증이 필요한 변경은 disposable DB에서 수행하고, 적용된 migration 파일을 수정하거나 재번호화하지 않는다.

## 6. REA와 브라우저 조사

- 상세 절차: `docs/easy/research/2026-10-12/REA_ANALYSIS_AND_SETUP.md`
- 조사 환경은 Node `v26.5.0`, npm `11.17.0`으로 요구조건을 충족했다.
- 조사 시점에 `rea` 명령은 설치되어 있지 않았고 실제 설치·MCP 등록은 수행하지 않았다.
- 설치가 승인되면 공식 README를 다시 확인한 뒤 별도 Chrome 프로필과 loopback CDP만 사용한다.
- 계정 ID/비밀번호, 쿠키, local/session storage 값, Authorization 헤더, 실제 사업자·금융 데이터는 Evidence에 넣지 않는다.
- 클라우드 Work에서 사용자 PC의 `localhost` Chrome에 직접 연결할 수 있다고 가정하지 않는다. 브라우저 관찰은 REA가 실행되는 동일 환경에서만 검증한다.

## 7. 전달 산출물

`docs/easy/research/2026-10-12/` 아래에 다음을 둔다.

1. `01_easyshop_feature_flow_analysis.md`
2. `02_easy_code_db_audit.md`
3. `03_easyshop_vs_easy_comparison.md`
4. `04_ai_accounting_tax_extension_design.md`
5. `05_development_priority_schedule_cost.md`
6. `REA_ANALYSIS_AND_SETUP.md`
7. `README.md`

산출물에는 로그인 정보, 세션, 실제 금융·사업자 데이터가 없다. 이지샵 인증 후 확인한 내용도 화면 사실과 공식 설명 수준으로만 정리했으며 자격정보는 이전하지 않는다.

## 8. 다음 작업

합의 순서에 따라 **2단계 장부 MVP의 일반 사업자 화면 요구사항을 코드 변경 없이 먼저 확정**한다.

1. Admin API 중 사업자용으로 재사용 가능한 읽기/쓰기 계약 목록 확정
2. 사용자 승인 전/후 transaction·journal 상태 전이 확정
3. 초보 사업자용 월 장부·검토함·증빙 상태·대시보드 화면 명세
4. 테스트 골든셋과 사업체 격리 테스트 계획
5. 승인 후 별도 구현 브랜치에서 코드·DB 변경

외부수집 계약, 신고 제출, 급여, 운영 배포는 이 다음 작업에 포함하지 않는다.

## 9. 금지사항

- `main` 직접 병합 또는 운영 배포
- 운영 DB 수정
- 비밀번호·API 키·OAuth token·쿠키·세션·인증서 커밋
- 실제 사업자등록번호·주민번호·계좌·카드·거래 원문 커밋
- REA/Playwright를 이용한 인증 우회, 비공개 코드·데이터 추출
- AI의 승인 없는 확정 분개 또는 신고 제출


