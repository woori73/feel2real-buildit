# 프로젝트 작업 규칙 (OMX / Codex 진입점)

> 이 파일은 **얇은 진입점**이다. 실제 규칙·스펙은 `docs/`에 있다.
> ⚠ 이 파일에 일회성 팀 워커 지침(claim-task, worker-N 등)을 커밋하지 말 것. 팀 실행이 덮어쓰면 머지 전 복원한다.
> CLAUDE.md(OMC용)와 이 파일의 핵심 규칙은 동일하게 유지한다.

## 작업 착수 전 (필수)
1. **BuildIt 관련 기능을 수정하기 전에 `docs/BUILDIT_PHILOSOPHY.md`를 먼저 읽는다.**
2. **`docs/INDEX.md`를 읽고** 해당 작업 문서를 로드한다.
3. 현재 git 상태 / 브랜치 확인. **ChatGPT Work / Codex 세션은 [docs/WORK_GIT_WORKSPACE_WORKFLOW.md](docs/WORK_GIT_WORKSPACE_WORKFLOW.md)를 먼저 읽는다** (세션별 stale checkout·`origin/main` 오판 방지, GitHub 최신 main이 authoritative).
4. DB/마이그레이션을 건드리면 **`docs/WORKFLOW.md` §4 번호정책**을 반드시 확인한다.

## 핵심 규칙 (요약 — 상세는 docs/)
- **main 단일 로컬 개발**: 기본 작업은 `main`에서 수행하고 `./scripts/run-local.sh`가 로컬 MySQL(`localhost:3308/f2r_db`)을 자동 생성·재사용한다. 검증 후 PR로만 병합하고, 운영 배포는 수동 GitHub Actions로만 수행한다. → [docs/WORKFLOW.md](docs/WORKFLOW.md)
- **마이그레이션 번호**: `db/migration`=운영 순차(다음 V909+), `db/dev-seed`=`R__` repeatable(버전번호 미사용), 운영 위치 버전 중복·번호 역행 금지(= prd 502 원인). 적용된 V_파일 불변. → [docs/WORKFLOW.md](docs/WORKFLOW.md) §4
- **스키마 동기화**: `db/migration` V_파일 + Entity + [docs/DOMAIN.md](docs/DOMAIN.md) 셋 동시 수정.
- **API 계약**: endpoint 변경 시 [docs/API.md](docs/API.md) 동시 수정.
- **CRUD 범위 확인**: 사용자가 객체를 생성하게 만들 때, 구현 전 "Update/Delete까지 포함한 CRUD인가?"를 묻는다. Delete면 물리/soft/참조차단/cascade 중 정책 + 권한(생성자/Admin/role)을 확인. Create-only로 끝내면 사유를 보고에 남긴다.
- **테스트**: 함수 작성 시 unit test, given/when/then 구조.

## 이슈 / PR 작성
GitHub 이슈·PR은 **[docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) 형식 그대로** 작성한다 (제목 타입 prefix + 목표/범위/검증/향후/관련 섹션, 한글, 간결).
