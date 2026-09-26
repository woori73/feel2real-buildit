# 개발 워크플로 & 마이그레이션 정책 (Dev Workflow)

> 로컬 실행, PR, 배포, Flyway 마이그레이션 번호 규칙. **DB/마이그레이션/머지 작업 전 반드시 로드.**
> 이 문서가 OMC(CLAUDE.md) / OMX(AGENTS.md) 양쪽의 워크플로 단일 출처다.
> ⚠ **tie-breaker**: 이 문서가 구두 지시·에이전트 메모리(CLAUDE/OMX 기억)와 충돌하면 **이 문서가 우선**한다. 프로세스를 바꾸려면 말로 끝내지 말고 **반드시 이 문서부터** 고친다.

## 1. 환경 2티어

기본 개발 흐름은 기존 `worktree → dev → stg → PR → main`이 아니라 **main 단일 로컬 개발 → PR → main 병합 → 수동 prd 배포**다.

| 티어 | git | 프로필 | DB | 실행 | flyway | 역할 |
|---|---|---|---|---|---|---|
| **local** | `main` | `local` | Docker MySQL `localhost:3308` / `f2r_db` | `./scripts/run-local.sh` | `db/migration` + `db/dev-seed` | 개발·테스트 기본 환경 |
| **prd** | remote `main` | `prd` | EC2/RDS 환경변수 | GitHub Actions 수동 배포 | `db/migration`만 | 운영 |

### 로컬 DB 자동 생성/재사용

> Windows에서 처음 세팅한다면 [LOCAL_SETUP_WINDOWS.md](LOCAL_SETUP_WINDOWS.md)를 먼저 본다.
> WSL2/Docker Desktop/JDK 21이 준비돼 있지 않으면 아래 스크립트는 1단계에서 멈춘다.

`./scripts/run-local.sh`는 다음을 자동 수행한다.

1. Docker daemon 접근 확인
2. `docker-compose.main.yaml`로 `f2r_mysql_main` 컨테이너 생성/기동
3. healthcheck가 `healthy`가 될 때까지 대기
4. `SPRING_PROFILES_ACTIVE=local` 강제
5. `SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3308/f2r_db...` 강제
6. `./gradlew bootRun` 실행

로컬 DB는 volume(`mysql_data_main`)에 보존되므로 이후 실행은 같은 DB에 재연결한다. DB만 초기화하려면:

```bash
docker compose -f docker-compose.main.yaml down -v
./scripts/run-local.sh
```

### prd 영향 차단

- 로컬 실행 스크립트는 항상 `local` profile과 `localhost:3308/f2r_db`를 사용한다.
- 운영은 EC2 systemd `EnvironmentFile=/etc/f2r/env`의 `SPRING_PROFILES_ACTIVE=prd`, `DB_HOST`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `S3_BUCKET` 등이 주입될 때만 `prd` profile로 실행된다.
- `local`은 파일 저장소를 로컬 디스크(`./raw_datas`)로 사용하고, `prd`만 S3를 사용한다.
- `db/dev-seed`는 `local`에만 적용된다. `prd` profile은 `db/migration`만 적용한다.

## 2. 개발·병합·배포 플로우

```text
main에서 작업
  → ./scripts/run-local.sh 로 앱 부팅 확인
  → ./gradlew test 또는 ./gradlew build
  → feature branch push / PR 생성
  → PR 리뷰·검증 후 main merge
  → main push Build Check SUCCESS → Deploy to EC2 자동 실행 (HEAD = CI가 검증한 그 SHA)
  → (필요 시) Deploy to EC2 workflow_dispatch로 수동 재배포
```

- **자동 배포 조건**: `workflow_run`은 "main push로 돈 `Build Check`가 success"일 때만 발화한다.
  PR 브랜치 push, 실패/취소된 CI는 대상에서 제외되고, 배포는 CI가 통과한 SHA를 명시적으로
  checkout해 빌드 대상과 검증 대상을 일치시킨다(머지 후 main이 밀려나도 다른 SHA를 배포하지 않는다).
- **배포는 schema migration(Flyway)만 수행한다.** NewStart seed/repair/approval, EASY 운영 데이터
  write 등은 이 워크플로의 범위가 아니며 어떤 경로로도 자동 실행되지 않는다.

- **main 직접 push 금지** — 배포 기준 브랜치가 `main`이므로 변경은 PR로 직렬화한다.
- 기존 `dev`/`stg` 브랜치 승격 플로우는 기본 워크플로에서 사용하지 않는다.
- 필요 시 고위험 운영 마이그레이션은 별도 일회성 prd-like DB에서 `db/migration`만 적용해 검증할 수 있지만, 기본 작업 절차로 강제하지 않는다.
- PR 단위 자동 검증이 없으면 semantic conflict가 main에 들어갈 수 있으므로, 후속으로 PR CI를 반드시 추가한다.

## 3. PR 전 체크리스트

1. `./scripts/run-local.sh`로 local 앱 부팅 확인
2. `./gradlew test` 통과 (가능하면 `./gradlew build`)
3. 분기 후 `origin/main`이 움직였으면 `origin/main`을 병합/rebase한 뒤 재검증
4. 보안 취약점 없음 (민감 로그·평문 토큰·운영 DB/S3 직접 연결 금지)
5. Flyway 변경이 있으면 마이그레이션 번호 규칙 준수 (§4)
6. 문서 동기화: 스키마→[DOMAIN.md](DOMAIN.md), 플로우→[FLOWS.md](FLOWS.md), 계약→[API.md](API.md)

### (후속) CI 강제

GitHub Actions PR CI를 추가해 최소 다음을 자동 검증한다.

1. MySQL 8.4 service 기동
2. `SPRING_PROFILES_ACTIVE=local ./gradlew test` 또는 `./gradlew build`
3. `db/migration`은 운영 번호선보다 낮은 새 V_파일 금지
4. `db/dev-seed`는 `R__` repeatable만 허용(versioned 금지)
5. `db/migration`과 `db/dev-seed` 간 version 충돌 금지
6. 데이터 마이그레이션 3단계룰 준수 여부 리뷰/정적 검사

## 4. Flyway 마이그레이션 번호 정책 (필독 — 위반 시 prd 502)

Flyway 단일 history 테이블이지만 두 위치의 역할이 다르다. `db/migration`은 **versioned**(`V_`), `db/dev-seed`는 **repeatable**(`R__`, 버전번호 없음)이다. versioned는 순서·중복 규칙을 따르고, repeatable은 versioned 전부 적용 후 마지막에 실행된다.

- **`db/migration` = 운영(prd) 스키마.** versioned, 순차 번호, 새 파일 = **적용된 max + 1**. 현재 `origin/main` 파일 기준 최고 = **V947** → 다음 운영 마이그레이션 후보 **V948+**. 실제 prd 적용 max는 작업 전 `flyway_schema_history`에서 다시 확인한다.
  - ⚠ 과거 실수로 운영 마이그레이션 일부가 **V903+ (900대)**에 들어가 있다. prd에 적용됨 → **불변**(체크섬). 보기 싫어도 되돌리지 않는다. 운영 번호선은 900대에서 계속 위로.
- **`db/dev-seed` = 로컬 전용 데이터, Flyway repeatable(`R__`).** 버전번호를 쓰지 않는다. repeatable은 항상 versioned 뒤에 실행된다.
  - ⚠ **R__ 시드는 최종 스키마 기준으로 실행된다.** 나중 마이그레이션이 추가한 NOT NULL/제약을 시드 INSERT가 모두 만족해야 한다.
  - ⚠ **R__는 파일 checksum이 바뀔 때 재실행된다.** unique 키 없는 테이블은 재실행 시 중복될 수 있으니, 시드를 수정하면 로컬 DB를 1회 reset한다.
- **두 위치에 같은 버전 번호 금지** → `Found more than one migration with version N`로 clean DB에서도 부팅 거부.
- **운영 번호가 적용된 max보다 낮으면** out-of-order → `Detected resolved migration not applied` → 부팅 거부.
- **prd `flyway_schema_history` 직접 수정 금지.** 미관용 renumber 금지. 잘못된 적용은 새 V_파일로 보정한다.
- **타임스탬프 버전 미사용** — 명문 규칙 + CI로 충분하다.

### 데이터를 건드리는 마이그레이션 — 3단계로 쪼개기

빈 DB에서는 통과하나 prd 기존 데이터에서 깨질 수 있는 마이그레이션(`ADD COLUMN NOT NULL`, `ALTER NOT NULL`, `ADD UNIQUE`, `ADD FOREIGN KEY`)은 단일 파일로 처리하지 않는다.

1. `V{N}__add_xxx_nullable.sql` — nullable/제약없이 추가
2. `V{N+1}__backfill_xxx.sql` — `UPDATE`로 기존 row 정리 (Entity 아직 안 바꿈)
3. `V{N+2}__enforce_xxx.sql` — `NOT NULL`/`UNIQUE`/`FK` 제약 + **이 단계에서 Entity 동시 수정**

신규 테이블/컬럼삭제/인덱스만 추가 등 기존 row 무관 변경은 1단계로 끝낼 수 있다.

### 잘못된 마이그레이션 복구

- 적용 전(`flyway_schema_history` 기록 전)이면 V_파일 직접 수정 가능.
- 이미 적용됐으면 **새 V_파일로 보정**한다.

## 5. 스키마 동기화 3원칙

`db/migration` V_파일 ↔ Entity ↔ [DOMAIN.md](DOMAIN.md)는 항상 동일 현실을 가리킨다. 하나만 수정 금지. 모든 profile `spring.jpa.hibernate.ddl-auto: validate` 고정 (스키마 변경은 Flyway만).

## 6. Worktree/dev/stg 사용 정책

- 기본 작업에는 worktree, dev 브랜치, stg 브랜치 승격을 사용하지 않는다.
- 병렬 실험이나 장기 작업 격리가 꼭 필요할 때만 Git worktree를 선택적으로 쓸 수 있다. 이 경우에도 최종 검증·PR 기준은 `main` 최신 상태다.
- `scripts/worktree-db.sh`는 과거 병렬 worktree용 도구로 남겨두되, 새 담당자의 기본 실행 경로는 `./scripts/run-local.sh`다.

## 7. EC2 배포

- 워크플로: `.github/workflows/deploy-ec2.yml` — **수동** 트리거 (Actions → Run workflow)
- Secrets: `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`
- 과정: bootJar 빌드 → SCP → `systemctl restart f2r-app.service`
- prd 설정은 EC2 systemd `EnvironmentFile=/etc/f2r/env`에서 주입한다. 로컬 `.envrc`, `docker-compose.main.yaml`, `./scripts/run-local.sh`는 운영에 사용되지 않는다.

## 8. Academy 영상 운영

Academy 영상 UI는 `POST /api/tutor/academy/videos` multipart 요청으로 application server에 업로드한다.
서버가 request thread에서 ffprobe 검증, H.264/AAC MP4 정규화, thumbnail 생성과 private S3 저장을 마친 뒤 `READY + NORMALIZED` row를 저장한다. 브라우저는 S3에 직접 PUT하지 않는다.

기존 direct-upload session API와 DB-backed worker/cleanup 모델은 배포 전 세션 호환과 기존 데이터 정리를 위해 유지하지만 신규 UI 업로드 경로로 사용하지 않는다.

EC2/systemd, IAM, S3 CORS/lifecycle, ALB 180초 inactivity gate, `/var/tmp` 용량, ffmpeg/libx264, Actuator 지표와 Chrome/curl 증거 절차의 **단일 실행 문서**는 [AWS_DEPLOYMENT.md](AWS_DEPLOYMENT.md)다. 이 문서에 운영 명령이나 timeout 값을 복제하지 않는다.
