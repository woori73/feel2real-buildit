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

- #729 기출분석 사진·HTML 업로드(`app.exam-insight.enabled` 기본 false라 켜기 전에는 동작 없음)
- #730 홈 내신위키 링크 복구 · #731 수업 인사이트 카드(날짜 세로 깨짐)
- #732 새 홈페이지 만들기·수정 초기화·변경 기록 접기 (`POST /api/tutor/academy/assistant/restart`)
- #733 학원 상담신청 접수·원장 조회 (**마이그레이션 V1086 `academy_inquiries` 신규 테이블 — 운영 DB에 적용됨**)
- #734 빌더 미리보기 로고 클릭 오류 + **저장 전 초기화 시 작업본(디자인)이 사라지는 데이터 손실 수정(우선 포함 대상)**

### 다음 배포 후보 체크리스트 (승인 전 — 이 문서는 배포 승인이 아니다)

배포 SHA는 승인 시점의 main 최신 SHA로 다시 고정한다(작성 시점 main: `83015546`, #734 병합 커밋).

1. RDS 복구 지점(자동 백업/스냅샷) 확인 — V1086 적용 전 필수. (배포 IAM 역할은 RDS 조회 권한이 없다: 담당자가 콘솔/별도 권한으로 확인)
2. DesignCraft job 1: 작업 폴더 보존 복사 완료(`/var/backups/dc-job1-preserve-20261017/1`, 9개 파일·체크섬 일치). gate는 `CLOSED` 유지.
3. 메모리 MemAvailable ≥ 400MB, 최근 OOM 0건 재확인.
4. 승인 시 SHA == main 최신 SHA 재확인, CI(build·mysql-integration-test) 성공 확인.
5. 신규 환경 설정 없음(상담신청 한도는 기본값 사용: `app.academy-inquiry.rate-limit.*`). 기출분석은 켜지지 않음.
6. 배포 후 확인: `/builder`(초기화·새 홈페이지·로고 클릭), 공개 학원 페이지 상담 접수 → `/tutor/academy/inquiries`, Flyway V1086 적용, 기존 공개 홈페이지 불변.

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
