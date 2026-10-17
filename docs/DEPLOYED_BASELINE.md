# 운영 배포 기준 (Production baseline)

> 가장 최근 운영 배포를 한 곳에 기록한다. 새 배포가 성공하면 맨 위 표를 갱신하고 이전 행은 이력으로 내린다.
> 롤백 기준 SHA를 찾을 때 먼저 이 문서를 본다. (배포 절차는 [AWS_DEPLOYMENT.md](AWS_DEPLOYMENT.md))

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

포함된 변경 (직전 운영 기준 `0ac6fe7b` 이후):

- #721 DesignCraft gate OPEN TTL(기본 1800초) · #722 PDF 인쇄 마크 제목 ASCII
- #723 EASY 대시보드 위젯 API v1 (`GET /api/admin/easy/dashboard/widgets`, ADMIN 전용)
- #724 배포 env 백업 보장 + 배포 실패 시 env 자동 복원 (첫 운영 적용)

## 배포 직후 상태 (기록용)

- DesignCraft gate: `CLOSED` 유지, 정식 개방 전. job 1은 `RUNNING` 그대로 보존.
- 메모리: MemAvailable 약 540 MiB, swap 약 775 MiB, 최근 60분 OOM 0건 (인스턴스 t2.small, 2 GiB).
- env 백업: `/var/backups/f2r-deploy-env/<시각>-<pid>/` (0700, 배포마다 생성, 성공 시 최신 5개 유지).

## 이력

| SHA | 시각 (UTC) | 비고 |
|---|---|---|
| `e034492f` | 2026-10-17 02:57 | 위 표 |
| `0ac6fe7b` | 2026-10-16 | 직전 운영 기준 (DesignCraft CLI 0.8, gate 도입 이후) |
