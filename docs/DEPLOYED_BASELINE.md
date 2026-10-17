# 운영 배포 기준 (Production baseline)

> 가장 최근 운영 배포를 한 곳에 기록한다. 새 배포가 성공하면 맨 위 표를 갱신하고 이전 행은 이력으로 내린다.
> 롤백 기준 SHA를 찾을 때 먼저 이 문서를 본다. (배포 절차는 [AWS_DEPLOYMENT.md](AWS_DEPLOYMENT.md))

> **주의**: main의 최신 커밋은 운영에 배포된 SHA보다 앞서 있을 수 있다. 운영 SHA는 아래 표의 `e034492f`뿐이며,
> main 최신 SHA를 운영 SHA로 읽지 않는다. 배포 승인 때는 "배포 SHA"를 명시해 고정한다.

## 현재 운영 기준

| 항목 | 값 |
|---|---|
| 배포 SHA | `e034492fc26f943181869379d3b26646fc713dd7` |
| 배포 시각 (UTC) | 2026-10-17 02:57 (Spring 재시작 02:57:01) |
| 배포 run | Deploy to EC2 #993 (Actions run 38996686563), 브랜치 `deploy/main-e034492f`에서 dispatch |
| 입력 | `assistant_switch=unchanged`, `hwpx_worker=unchanged` |
| 운영 jar | sha256 앞 16자 `09dc7626112e1e77` (215,628,490 B) |
| 직전 jar | sha256 앞 16자 `a6c409cceb78eb98` → 서버 `app.jar.previous` (+ 사전 백업 `/var/backups/dc-deploy-pre-bbbd9222/`) |
| 롤백 여부 | 없음 (health 첫 세대 통과, 재시작 0회) |

포함된 변경 (직전 운영 jar `a6c409cc` 이후. 그 배포 SHA는 `0ac6fe7b`로 추정 — 이전 배포 브랜치 이름 기준이며 확인하지 못함):

- #721 DesignCraft gate OPEN TTL(기본 1800초) · #722 PDF 인쇄 마크 제목 ASCII
- #723 EASY 대시보드 위젯 API v1 (`GET /api/admin/easy/dashboard/widgets`, ADMIN 전용)
- #724 배포 env 백업 보장 + 배포 실패 시 env 자동 복원 (첫 운영 적용)

## main에는 있으나 아직 운영에 배포되지 않은 변경

- #725 Hub 통합 대시보드 UI (`/admin/hub`)
- #726 EASY 위젯 응답 캐시 상한·만료 제거
- #727 이 문서

다음 운영 배포는 별도 승인 후 진행하며, 성공하면 위 표와 이 목록을 갱신한다.

## 배포 직후 상태 (기록용)

- DesignCraft gate: `CLOSED` 유지, 정식 개방 전. job 1은 `RUNNING` 그대로 보존.
- 메모리: MemAvailable 약 540 MiB, swap 약 775 MiB, 최근 60분 OOM 0건 (인스턴스 t2.small, 2 GiB).
- env 백업: `/var/backups/f2r-deploy-env/<시각>-<pid>/` (0700, 배포마다 생성, 성공 시 최신 5개 유지).

## 이력

| SHA | 시각 (UTC) | 비고 |
|---|---|---|
| `e034492f` | 2026-10-17 02:57 | 위 표 |
| `0ac6fe7b`(추정) | 확인 못 함 | 직전 운영 jar `a6c409cc`의 배포 SHA로 추정 (미확인) |
