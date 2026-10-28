# private_tutor_solution_back

Spring Boot + Thymeleaf 기반 F2R/BUILD IT 교육 플랫폼 백엔드/SSR 앱.
BUILD IT은 AI CHART로 숨은 영어교육자를 발견하고, 홈페이지·홍보 Shorts·학교별 Wiki·학부모 Report·AI 교육도구로 성장시키며, 오프라인 매출 검증과 성장자본 연결까지 지향하는 Education Talent Accelerator다.

## 빠른 문서 진입점

- 제품·개발 최상위 원칙: [`docs/BUILDIT_PHILOSOPHY.md`](docs/BUILDIT_PHILOSOPHY.md)
- 프로젝트 전체 인수인계: [`docs/PROJECT_HANDOFF.md`](docs/PROJECT_HANDOFF.md)
- 작업별 문서 라우팅: [`docs/INDEX.md`](docs/INDEX.md)
- 개발 워크플로/Flyway 정책: [`docs/WORKFLOW.md`](docs/WORKFLOW.md)
- API 계약: [`docs/API.md`](docs/API.md)
- DB/도메인 모델: [`docs/DOMAIN.md`](docs/DOMAIN.md)
- 화면·상태 플로우: [`docs/FLOWS.md`](docs/FLOWS.md)
- 다음 할 일: [`docs/ROADMAP.md`](docs/ROADMAP.md)

## 로컬 실행

```bash
./scripts/run-local.sh
```

처음 실행하면 스크립트가 로컬 MySQL 컨테이너(`f2r_mysql_main`)를 자동으로 만들고 재사용한다.
이후 Spring Boot는 항상 `local` profile로 `localhost:3308/f2r_db`에 연결한다.

- Docker Desktop이 켜져 있어야 한다.
- 빈 DB에는 첫 부팅 때 Flyway가 `db/migration`과 `db/dev-seed`를 자동 적용한다.
- `ANTHROPIC_API_KEY`는 기본 부팅에 필수가 아니다(`anthropic.enabled: false`). 필요하면 `~/.config/f2r/env`에 둔다.
- 운영(prd) 영향 없음 — prd는 EC2 systemd `EnvironmentFile=/etc/f2r/env`의 `SPRING_PROFILES_ACTIVE=prd`와 DB 환경변수로 별도 실행된다.

로컬 DB만 초기화하고 싶을 때:

```bash
docker compose -f docker-compose.main.yaml down -v
./scripts/run-local.sh
```

## 개발 흐름

기본 작업은 `main`에서 진행하고, 검증 후 PR로 `main`에 병합한다. 배포는 GitHub Actions의 수동 EC2 배포 workflow로만 수행한다. 상세 규칙은 `docs/WORKFLOW.md`를 따른다.

## 영어 기말고사 현재 상품

현재 판매 조건은 **2주 · 주 2회 · 총 4회 · 회당 40문항 · 총 160문항 · 20,000원**입니다. 서버 기준은 `HomeExamOffer.CURRENT`이며 과거 계약에는 소급 적용하지 않습니다. [개편 범위와 검증 안내](docs/home-exam/P0-20261028.md).
