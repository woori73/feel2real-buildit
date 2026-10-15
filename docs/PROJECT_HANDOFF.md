# 프로젝트 인수인계 / 현황 요약

> 기준 브랜치: `origin/main` (`c1c5712`, 2026-08-09 최신 원격 기준)
> 목적: 새 작업자가 프로젝트를 끊기지 않고 이어가기 위한 “현재 상태·진입점·주의사항” 단일 요약.
> 상세 계약의 단일 출처는 여전히 `docs/INDEX.md`가 연결하는 개별 문서다.

## 1. 한 줄 요약

`private_tutor_solution_back`은 F2R(Feel to Real)의 핵심 서비스 **BUILD IT**을 구현하는 Spring Boot + Thymeleaf 기반 백엔드/SSR 앱이다. 현재 제품은 학생 자료 업로드·LLM 분석에서 출발했지만, `origin/main` 기준으로는 AI로 숨은 교육자를 발견하고, 홈페이지·Shorts·학교별 Wiki·학부모 Report·AI 교육도구로 성장시키며, 오프라인 매출 검증과 성장자본 연결까지 지향하는 **Education Talent Accelerator** 인프라로 확장된 상태다.

## 1.1 F2R · BUILD IT 사업 정의

### F2R (Feel to Real)

F2R은 “느낌을 현실로”라는 의미처럼, 교육자가 가진 수업 철학·노하우·가능성을 기술·브랜딩·자본을 통해 실제 교육 브랜드와 사업으로 만드는 회사다.

### BUILD IT

BUILD IT은 **AI로 숨은 교육자를 발견하고, 기술과 브랜딩으로 성장시키고, 성장자본과 연결해 교육 브랜드로 만드는 Education Talent Accelerator**다.

해결하려는 문제는 두 가지다.

- 전국에는 실력과 수업 노하우를 갖고도 브랜딩·홍보·기술·자본이 부족해 지역에 머무르는 학원 원장과 강사가 많다.
- 투자자는 수많은 교육자 중 누가 실제 교육 경쟁력과 사업 성장성을 가지고 있는지 사전에 발견하고 검증하기 어렵다.

BUILD IT은 이 사이를 연결한다. 홈페이지·Wiki·Shorts·AI 도구는 각각 별개의 SaaS가 아니라, 교육자를 발굴하고 성장시키기 위한 하나의 성장 인프라다.

### 성장 구조

| 단계 | 이름 | 의미 | 현재 코드/문서 연결 |
|---|---|---|---|
| 1 | DISCOVER — AI CHART | 웹상의 수업, 내신분석, 교육칼럼, 교재, 영상 등 실제 교육활동을 AI로 분석해 전국의 숨은 영어교육자를 발굴 | `domain/chart`, `/chart`, `/admin/chart`, `docs/API.md` § BUILD IT CHART |
| 2 | BUILD — 자체 성장 인프라 | 홈페이지, 홍보 Shorts, 학교별 기출 Wiki, 학부모 Report, AI 교육도구로 교육자의 온라인 활동과 개인 브랜드 강화 | Academy/Builder/LMS/Problem Generator/landing 관련 도메인 |
| 3 | GROW — 오프라인 매출 성장 | 조회수·팔로워가 아니라 지역 학부모 상담 → 학생 모집 → 오프라인 학원 매출 성장으로 실제 사업성 검증 | Academy 방문 통계, 상담 CTA, 학원별 공개 페이지 |
| 4 | INVEST — 성장자본 연결 | 성장 데이터가 확인된 원장·강사를 투자자와 연결하고 BuildIt은 에이전시/성장 파트너로 참여 | 아직 사업/운영 설계 중심, 코드화 전 |
| 5 | SCALE — 교육 브랜드화 | 지역 학원 원장을 지역 대표 교육자 → 교육자 브랜드 → 교재·온라인 강의·교육 IP 사업으로 확장 | 장기 로드맵 |

### 선순환

AI가 교육자를 발견 → BuildIt이 교육자를 성장 → 교육자가 BuildIt AI를 실제 수업에서 사용 → 현장 피드백과 데이터로 BuildIt 제품 개선 → 더 좋은 교육자 유입 → 성장성이 검증된 교육자에 자본 연결 → 새로운 교육 브랜드 탄생.

### 사업계획서용 한 문단

F2R(Feel to Real)은 ‘느낌을 현실로’라는 의미처럼 교육자가 가진 가능성을 실제 교육 브랜드와 사업으로 만드는 회사입니다. 핵심 서비스 BUILD IT은 AI CHART를 통해 전국의 숨은 영어학원 원장과 강사를 발굴하고, 홈페이지·홍보 Shorts·학교별 기출 Wiki·AI 교육도구 등 자체 인프라를 활용해 교육자의 브랜딩과 학생 모집을 지원합니다. 이를 통해 실제 오프라인 매출 성장을 검증하고, 성장성이 확인된 교육자를 투자자와 연결하여 지역의 선생님을 경쟁력 있는 교육 브랜드로 성장시키는 AI 기반 Education Talent Accelerator입니다.

## 2. 먼저 읽을 문서 순서

1. `docs/INDEX.md` — 작업별 문서 라우팅.
2. `docs/WORKFLOW.md` — worktree, PR, dev/stg/prd, Flyway 번호 정책.
3. 이 문서(`docs/PROJECT_HANDOFF.md`) — 현재 제품/코드 구조 빠른 파악.
4. 작업 주제별 상세 문서:
   - API/endpoint: `docs/API.md`
   - DB/Entity: `docs/DOMAIN.md`
   - 화면·상태 흐름: `docs/FLOWS.md`
   - 다음 할 일: `docs/ROADMAP.md`
   - 배포/EC2/S3/영상 운영: `docs/AWS_DEPLOYMENT.md`
   - Academy Builder: `docs/academy-builder-page.md`
   - LMS: `docs/academy-lms-design.md`

## 3. 현재 제품 표면

### Public / 비로그인

- `/` — Build It 브랜드 랜딩.
- `/home` — F2R 학생 대상 랜딩/홈 허브.
- `/academies`, `/academies/{slug}` — 공개 Academy 목록/상세.
- `/academy-posts`, `/academies/{slug}/blog-posts/**` — Academy 블로그 공개 허브/상세.
- `/community` — 공개 Q&A 커뮤니티.
- `/chart` — BUILD IT CHART 공개 화면.
- `/lms/{handle}` 및 `/lms/{handle}/**` — 학원별 온라인 LMS 공개/접속 경로.
- `/manual/index.html` — 사용 설명서 정적 페이지.

### Student

- `/register` 학생 가입, 공통 로그인 모달 기반 로그인.
- `/stdt_dashboard` — 학생 대시보드.
- `/conversation/**` — 학생 자료/리포트/오답노트 중심 화면. AI Chat 화면은 학생 직접 접근이 막혀 있고 ADMIN/TUTOR 중심으로 제한된다.
- `/lessons` — 수업 검색 및 신청.
- 학습자료 풀이 업로드, 피드백 PDF 조회, 점수 타임라인, 오답노트 조회.

### Tutor

- `/tutor/academy` — 강사 Academy 관리 홈.
- `/builder` — Academy Builder. 기존 Academy 수정 및 공개 페이지 모양 편집.
- `/tutor/academy/lessons/editor` — 수업상품 생성/수정 전용 편집기.
- `/tutor/lesson/{lessonId}` — 수업 운영 상세, 신청/수강생/커뮤니티 진입.
- `/tutor/conversation_dashboard` — 강사 개인 대화 기록.
- `/problem-generator/**` — 자료 업로드 기반 문제 생성/저장 기능.
- Academy 홍보 영상 업로드, 히어로/수업 미디어 관리, 블로그 작성.

### Admin

- `/admin/students` — 학생 목록/임퍼소네이트 진입.
- `/admin/lesson-communities` — 수업 커뮤니티 검색/관리.
- `/admin/landing` — 랜딩 고정 영상 슬롯 관리.
- `/admin/chart` — BUILD IT CHART 후보/설정/LLM 키 관리.
- `/api/admin/**` — 관리자 API. 세부 계약은 `docs/API.md` 참조.

## 4. 주요 도메인과 코드 위치

기본 패키지: `src/main/java/f2r/tutor_solution/private_tutor_solution_back/`

| 영역 | 주요 위치 | 역할 |
|---|---|---|
| 인증/보안 | `config/security`, `domain/user` | JWT HTTP-only cookie, role guard, IDOR/ownership resolver, 내 프로필/탈퇴/비밀번호 재설정 |
| 학생 자료/LLM | `domain/source`, `domain/conversation`, `domain/exam`, `domain/question`, `domain/score`, `domain/dashboard` | 자료 업로드, 시험지 분석, 대화, 점수/오답/리포트 데이터 |
| Academy/수업 | `domain/academy`, `domain/lessonapplication` | 강사 Academy, Builder layout, 수업상품, 신청, LMS handle/route |
| 학습자료/피드백 | `domain/learningmaterial`, `domain/consultation`, `domain/wrongnote` | 강사/Admin 자료 부여, 학생 풀이 업로드, 피드백/상담자료/오답노트 |
| 블로그/커뮤니티 | `domain/academyblog`, `domain/community`, `domain/lessoncommunity` | 공개 블로그, 공개 Q&A, 수업별 커뮤니티 |
| 랜딩/통계 | `domain/landing`, `domain/academy/statistics`, `domain/betacomment` | 랜딩 영상 슬롯, 학원 방문 통계, 베타 의견 |
| 문제생성기 | `domain/problemgenerator` | Gemini 기반 문제 생성, 자료/문제세트 저장 |
| Build It Chart | `domain/chart` | Google educator discovery 후보/활동/승인 관리 |
| 파일/이미지/영상 | `infrastructure/storage`, `infrastructure/image`, `infrastructure/video` | local/S3 저장소, 이미지 무해화, ffmpeg/ffprobe 영상 검증·정규화 |

프론트는 `src/main/resources/templates`(Thymeleaf)와 `src/main/resources/static/{css,js,img,video}`에 있다.

## 5. 실행/검증 기본

### 로컬 단일 실행

```bash
./scripts/run-local.sh
```

- `~/.config/f2r/env`에서 `ANTHROPIC_API_KEY`를 읽는다.
- `anthropic.enabled` 기본값은 `false`라, 실제 LLM 호출을 켤 때는 설정 확인이 필요하다.

### worktree 전용 DB 실행

```bash
./scripts/worktree-db.sh up
direnv allow .
direnv exec . ./gradlew bootRun
```

- worktree별 MySQL 컨테이너/포트/볼륨을 분리한다.
- `.envrc.local`은 생성되지만 커밋하지 않는다.

### 테스트

```bash
direnv exec . ./gradlew test
direnv exec . ./gradlew build
```

- Java 테스트는 `src/test/java`에 있고, Academy Builder 등 순수 JS 테스트는 `src/test/js`에 있다.
- 문서만 바꾸는 작업은 최소한 `git diff --check`로 whitespace/patch sanity를 확인한다.

## 6. DB/Flyway 현재선

- 운영 마이그레이션 위치: `src/main/resources/db/migration`
- dev seed 위치: `src/main/resources/db/dev-seed`
- `origin/main` 파일 기준 현재 최고 운영 버전: `V947__add_buildit_chart.sql`
- 다음 운영 마이그레이션 후보: `V948__...sql`
- 실제 dev/stg/prd DB의 적용 max는 작업 전 `flyway_schema_history`에서 다시 확인한다.
- dev seed는 `R__*.sql` repeatable만 사용한다. 새 seed에 `V9000` 같은 versioned 파일을 만들지 않는다.

자세한 정책과 dev/stg 검증 순서는 `docs/WORKFLOW.md` §4를 우선한다.

## 7. 현재 중요한 작업 규칙

- 루트 `main` 직접 수정 금지. 항상 `origin/main` 기준 별도 feature worktree/branch에서 작업한다.
- endpoint 변경은 `docs/API.md`, 플로우 변경은 `docs/FLOWS.md`, DB/Entity 변경은 `docs/DOMAIN.md`까지 같은 작업 단위에서 맞춘다.
- Flyway 변경이 없으면 feature worktree에서 검증 후 PR로 간다.
- Flyway 변경이 있으면 현재 작업 worktree에서 feature 변경을 커밋한 뒤 dev → stg 순서로 checkout/merge/검증한다.
- `dev`/`stg` 검증 시 `SPRING_PROFILES_ACTIVE`/`SPRING_DATASOURCE_URL`을 손으로 주지 않는다. 브랜치별 `application.yaml` active 값과 config 파일을 검증해야 한다.
- GitHub Issue/PR 본문은 `docs/CONTRIBUTING.md` 형식을 그대로 따른다.

## 8. 이어서 볼 우선순위

`docs/ROADMAP.md` 기준으로 큰 축은 다음과 같다.

1. **제품 방향 정리** — Academy UX(직접 입력형/템플릿 자동 생성형/유료 제작 대행형), LMS/Builder 공개 라우트 정책 확정.
2. **DISCOVER — BUILD IT CHART** — 공개 `/chart`/`/admin/chart`의 발굴 근거, LLM key 운영, 개인정보·저작권·검증 정책 확정.
3. **BUILD — Academy Builder / LMS / 콘텐츠 인프라** — `/builder`, `/academies/{slug}`, `/lms/{handle}` 역할과 직접 입력형/템플릿 자동 생성형/유료 제작 대행형 UX 결정.
4. **GROW — 상담/학생모집/오프라인 매출 검증** — Academy 방문 통계, 상담 CTA, 전환 이벤트, 학부모 리포트가 상담→등록→매출 funnel로 이어지는 기준 정의.
5. **INVEST / SCALE** — 성장 데이터가 확인된 교육자를 위한 데이터룸/리포트 항목과 교육 IP 확장 모델을 별도 스펙화.
6. **Platform / Security Backlog** — PR test CI, dev/stg 동기화, CSRF 정책, 권한 guard, 공개 API DTO, 업로드 정책, storage orphan, PII 로그 점검.

## 9. 위험한 지점

- `docs/WORKFLOW.md`의 Flyway 번호 정책을 건너뛰면 prd 부팅 실패로 이어질 수 있다.
- `origin/main`에 PR test CI가 없어, 텍스트 충돌이 없어도 순차 머지 후 compile/test가 깨질 수 있다.
- 인증은 cookie 기반이라 상태변경 API의 CSRF/Origin 정책 변경은 신중해야 한다.
- `AuthenticatedUserResolver`와 service-layer ownership guard를 우회하는 API를 만들면 IDOR 위험이 생긴다.
- 업로드 파일은 storage write가 DB transaction보다 먼저/밖에서 일어나는 경로가 있어 orphan 가능성이 있다.
- Academy 영상 업로드는 ffmpeg/ffprobe, S3, ALB timeout, `/var/tmp` 용량에 의존한다. 운영 값은 `docs/AWS_DEPLOYMENT.md`를 따른다.

## 10. 문서 유지 원칙

- 이 문서는 “빠른 지도”다. 상세 계약을 복사하지 말고 링크한다.
- API 표는 `docs/API.md`, DB 표는 `docs/DOMAIN.md`, 플로우 세부는 `docs/FLOWS.md`에 둔다.
- 새 대형 기능이 들어오면 이 문서에는 다음만 갱신한다:
  - 제품 표면에 추가된 route/API 묶음
  - 코드 위치
  - 마이그레이션 현재선
  - 새 작업자가 반드시 알아야 할 위험/운영 주의점
