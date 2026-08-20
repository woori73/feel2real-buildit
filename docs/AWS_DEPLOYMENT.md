# AWS 배포·운영 Runbook

> **Canonical infrastructure source of truth.** EC2/systemd, RDS/Flyway, S3, IAM, CORS,
> lifecycle, ALB, 임시 디스크, ffmpeg, 관찰성과 수동 브라우저 검증은 이 문서에서만 관리한다.
> `docs/WORKFLOW.md`는 배포 흐름 포인터만 유지한다.

## 1. 배포 전 중단 조건

아래 중 하나라도 확인되지 않으면 배포·트래픽 전환을 중단한다.

- `SPRING_PROFILES_ACTIVE=prd`, RDS 접속 정보, `JWT_SECRET`, `S3_BUCKET`, `S3_REGION`,
  환경별 `S3_KEY_PREFIX`가 `/etc/f2r/env`에 있다. 값 자체는 문서·로그에 복사하지 않는다.
- ALB 요청 통계를 켤 때는 `ALB_ACCESS_STATISTICS_ENABLED=true`, `ALB_ACCESS_LOG_BUCKET`,
  `ALB_ACCESS_LOG_PREFIX`, `ALB_ACCESS_LOG_REGION`이 있고 §5의 access log delivery/IAM 검증을 통과한다.
- EC2 instance profile에 §3의 최소 S3 권한이 있고 정적 access key는 없다.
- Academy 영상 업로드가 same-origin multipart endpoint를 사용하며 ALB/application body 상한이
  200MiB 영상을 수용한다. S3 CORS는 현재 사용자 업로드 경로의 선행 조건이 아니다.
- incomplete multipart lifecycle rule이 활성화돼 있다.
- ALB idle timeout이 **180초 이상**이다. 이는 inactivity gate이며 application의 총 workflow
  deadline을 대신하지 않는다.
- `/var/tmp/f2r`가 디스크 filesystem이고 app user 전용 `0700`이며, §6의 여유 공간 gate를 통과한다.
- `ffmpeg`, `ffprobe`, `libx264`, AAC encoder 실검증이 통과한다.
- 배포 대상 migration 번호/checksum이 dev → stg 순서로 검증됐다.
- Actuator/Micrometer 지표와 DB backlog query를 내부 운영 경로에서 확인할 수 있다.

## 2. 확정 구조와 시간 계약

```text
Browser ── multipart video ──> Spring Boot ── ffprobe/ffmpeg ──> private S3 + RDS
Public GET stable URL ──302 (<=30s, no-store)──> signed private S3 GET/Range
Background worker ── materialize ── ffmpeg/ffprobe ── upload ── source switch
Cleanup worker ── abort/delete/head-confirm ── durable cleanup outbox
```

- 브라우저는 application의 `POST /api/tutor/academy/videos`에 multipart `video`를 한 번 보낸다.
- 원본/정규화 산출물 `<=209715200` bytes, duration `<=120000ms`, Academy당 최대 10개다.
- 요청 안에서 정규화와 thumbnail 생성까지 끝낸 뒤 `READY + NORMALIZED`를 저장한다.
- 최적화 attempt budget은 600초, stall은 90초, 최대 attempt는 2, worker permit은 1이다.
- stable public URL은 active source의 실제 media type을 사용한다. S3 GET 서명은 30초 이하이며
  `Cache-Control: no-store`다.

Spring `spring.servlet.multipart.*`는 문서/이미지/랜딩 영상과 Academy 영상의 servlet multipart
하드컷이다. Academy 원본 200MiB와 multipart 경계 오버헤드를 수용하도록 file 200MB/request 205MB를
유지한다. 실제 업무 상한은 `app.video.max-upload-bytes`가 다시 판정한다.

## 3. EC2 instance profile — S3 최소 권한

정책의 bucket/prefix는 실제 `/etc/f2r/env`의 `S3_BUCKET`과 `S3_KEY_PREFIX`로 치환한다.
한 bucket을 공유하면 환경마다 prefix를 분리하고 다른 환경 prefix를 허용하지 않는다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ListOnlyApplicationPrefix",
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": "arn:aws:s3:::<bucket>",
      "Condition": {
        "StringLike": {"s3:prefix": ["<prefix>/*"]}
      }
    },
    {
      "Sid": "AcademyVideoObjectsAndMultipart",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:AbortMultipartUpload",
        "s3:ListMultipartUploadParts"
      ],
      "Resource": "arn:aws:s3:::<bucket>/<prefix>/*"
    }
  ]
}
```

필요 동작은 create/upload/complete multipart, ListParts, Head/Get, attempt upload, Delete,
orphan prefix list다. `s3:*`, bucket public access, reusable access key는 허용하지 않는다.
SSE-KMS를 별도로 켰다면 해당 CMK에 이 role의 encrypt/decrypt/data-key 권한을 추가하되 다른 key로
확장하지 않는다.

검증:

```bash
aws sts get-caller-identity
aws s3api head-bucket --bucket "$S3_BUCKET"
aws s3api list-objects-v2 --bucket "$S3_BUCKET" \
  --prefix "$S3_KEY_PREFIX/academy-video/" --max-keys 1
```

출력은 보안 저장소에만 보관하고 object key, account id, signed URL을 이슈·애플리케이션 로그에 붙이지 않는다.

## 4. S3 보안, CORS, lifecycle

### 4.1 Bucket 기본 보안

- Block Public Access 전체 ON, ACL 사용 안 함, bucket은 private.
- HTTPS만 허용하는 bucket policy를 유지한다.
- signed URL을 DB/DTO/browser storage에 저장하지 않는다. DB에는 server-owned opaque key만 저장한다.
- application은 instance profile/default credential chain을 사용한다.

### 4.2 Browser UploadPart CORS (호환성 endpoint 전용)

현재 Academy 영상 UI는 S3에 직접 PUT하지 않으므로 이 설정에 의존하지 않는다. 호환성 direct-upload
endpoint를 별도 클라이언트가 다시 사용할 때만 실제 production origin과 ETag 노출을 설정한다.

```json
[
  {
    "AllowedOrigins": ["https://feel2real.co.kr", "https://*.feel2real.co.kr"],
    "AllowedMethods": ["PUT"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 300
  }
]
```

브라우저는 initiate가 준 URL을 opaque bearer capability로 메모리에서만 사용한다. URL을 parse,
local/session storage 저장, analytics/error payload 전송, console 출력하지 않는다. complete에는
오름차순 `partNumber`와 `ETag`만 보낸다.

검증:

```bash
aws s3api get-bucket-cors --bucket "$S3_BUCKET"
```

Chrome Network에서 UploadPart 응답의 `ETag`가 JavaScript에 보이는지 실제로 확인한다.

### 4.3 Incomplete multipart lifecycle

최소한 다음 rule을 활성화한다. application abort/reaper/cleanup이 1차 책임이며 lifecycle은 crash와
장기 장애에 대한 최후 방어선이다.

```json
{
  "Rules": [
    {
      "ID": "abort-incomplete-academy-video-multipart",
      "Status": "Enabled",
      "Filter": {"Prefix": "<prefix>/academy-video/staging/"},
      "AbortIncompleteMultipartUpload": {"DaysAfterInitiation": 1}
    }
  ]
}
```

`academy-video/attempts/` 또는 active raw/normalized를 시간만으로 삭제하는 lifecycle은 금지한다.
그 자산은 DB source-version과 cleanup outbox가 소유한다.

## 5. ALB

- HTTPS 443 listener → application target group, HTTP 80은 HTTPS redirect.
- target health path는 `/actuator/health`; target port는 8080.
- `idle_timeout.timeout_seconds >= 180`을 배포 전에 기록한다.
- ALB timeout은 연결 **무활동** 제한일 뿐 browser/DB의 절대 180초 승인 deadline이 아니다.
- Academy 영상 multipart body와 동기 변환 응답은 ALB를 통과한다. idle timeout은 180초 이상이어야 한다.

```bash
aws elbv2 describe-load-balancer-attributes --load-balancer-arn "$ALB_ARN" \
  --query 'Attributes[?Key==`idle_timeout.timeout_seconds`]'
```

ALB 504가 보이면 deadline을 늘리기 전에 target response time, DB deadline, finalization stage,
S3 latency를 함께 확인한다. client retry deadline을 리셋하는 방식으로 우회하지 않는다.

### 5.1 요청 기반 학원 접근 통계

ALB access logging을 private S3 bucket/prefix에 활성화한다. 앱의 EC2 instance profile에는 해당 로그
prefix에 대해서만 `s3:ListBucket`과 `s3:GetObject`를 추가한다. 애플리케이션 자산 prefix 권한과
로그 prefix 권한을 한 wildcard로 합치지 않는다.

```bash
aws elbv2 describe-load-balancer-attributes --load-balancer-arn "$ALB_ARN" \
  --query 'Attributes[?starts_with(Key, `access_logs.s3`)]'
aws s3api list-objects-v2 --bucket "$ALB_ACCESS_LOG_BUCKET" \
  --prefix "$ALB_ACCESS_LOG_PREFIX" --max-keys 1
```

`/etc/f2r/env`에는 값을 출력하지 않고 다음 변수만 설정한다.

```text
ALB_ACCESS_STATISTICS_ENABLED=true
ALB_ACCESS_LOG_BUCKET=<private-log-bucket>
ALB_ACCESS_LOG_PREFIX=<configured-prefix>/AWSLogs/<account>/elasticloadbalancing/ap-northeast-2/
ALB_ACCESS_LOG_REGION=ap-northeast-2
```

worker는 `.log.gz` 객체를 읽고 성공한 `GET https://{handle}.feel2real.co.kr/`만 KST 일별·누적으로
가산한다. `alb_access_log_imports`가 객체 단위 idempotency를 보장한다. 배포 후 최근 object의
`imported_request_count`, 학원별 total/daily 증가, 재poll 무증가를 확인한다. 이 값은 요청 수이며
사람·순방문자·세션 수가 아니다. 원본 IP와 user-agent는 DB에 저장하지 않는다.

ALB poller는 `albAccessStatisticsScheduler` 전용 단일 thread를 사용한다. 영상 upload recovery,
optimization heartbeat, cleanup scheduler와 thread pool을 공유하지 않으므로 로그 다운로드나 gzip
파싱이 느려져도 기존 영상 작업의 poll/heartbeat를 점유하지 않는다.
`ALB_ACCESS_LOG_PREFIX`에는 `AWSLogs/` 구간이 반드시 있어야 하며, 없으면 애플리케이션이 활성화를
거부한다. 따라서 같은 bucket을 쓰더라도 영상·이미지 application prefix 전체를 listing하지 않는다.

## 6. EC2 temp disk와 systemd

### 6.1 용량 gate

validation과 optimization은 서로 다른 single permit이므로 동시에 실행될 수 있다. 최소 여유 공간은
다음 합보다 커야 한다.

- validation materialization raw: 200MiB + filesystem overhead
- optimization input raw 200MiB + normalized 최대 100MiB + thumbnail/attempt overhead
- unrelated servlet multipart spool과 배포/로그 여유

운영 gate는 **최소 1GiB free**이며, 2GiB 미만이면 경고하고 동시 작업·로그 증가를 고려해 증설한다.
`/tmp`가 tmpfs면 사용하지 않는다.

```bash
df -h /var/tmp/f2r /tmp
findmnt -no TARGET,FSTYPE,SIZE,AVAIL /var/tmp/f2r /tmp
free -h
```

### 6.2 디렉터리와 service override

```bash
sudo install -d -o ec2-user -g ec2-user -m 0700 \
  /var/tmp/f2r/academy-video-validation \
  /var/tmp/f2r/academy-video-optimization

sudo systemctl edit f2r-app.service
```

```ini
[Service]
Environment=ACADEMY_VIDEO_VALIDATION_TEMP_DIR=/var/tmp/f2r/academy-video-validation
Environment=ACADEMY_VIDEO_OPTIMIZATION_TEMP_DIR=/var/tmp/f2r/academy-video-optimization
ReadWritePaths=/var/tmp/f2r
```

```bash
sudo systemctl daemon-reload
sudo systemctl cat f2r-app.service
sudo -u ec2-user test -w /var/tmp/f2r/academy-video-validation
sudo -u ec2-user test -w /var/tmp/f2r/academy-video-optimization
```

코드는 작업별 디렉터리 `0700`, 파일 `0600`을 강제하고 종료 시 삭제한다. 비정상 종료 후 stale temp는
startup sweep/recovery가 소유한다. 운영자가 실행 중인 process의 디렉터리를 임의 삭제하지 않는다.

## 7. ffmpeg/ffprobe provisioning

이 앱은 OS binary를 subprocess로 호출한다. 설치한 build에는 H.264 `libx264`와 AAC encoder가 모두
있어야 한다. Amazon Linux의 `ffmpeg-free`처럼 libx264가 빠진 build는 사용하지 않는다.

```bash
uname -m
ffmpeg -version | head -2
ffprobe -version | head -1
ffmpeg -hide_banner -encoders 2>/dev/null | grep -E 'libx264| aac '
sudo systemd-run --pipe --wait /usr/bin/env sh -c \
  'command -v ffmpeg; command -v ffprobe; ffmpeg -hide_banner -encoders 2>/dev/null | grep libx264'
```

실제 encode/probe gate:

```bash
work=$(mktemp -d /var/tmp/f2r/ffmpeg-smoke.XXXXXX)
ffmpeg -v error -f lavfi -i testsrc=duration=1:size=320x240:rate=30 \
  -f lavfi -i sine=frequency=1000:duration=1 \
  -c:v libx264 -pix_fmt yuv420p -c:a aac -y "$work/out.mp4"
ffprobe -v error -show_entries stream=codec_type,codec_name \
  -of csv=p=0 "$work/out.mp4"
rm -rf "$work"
```

`h264,video`와 `aac,audio`가 모두 나와야 한다. systemd PATH에서 못 찾으면 `/etc/f2r/env`에
`APP_VIDEO_FFMPEG_PATH=/usr/local/bin/ffmpeg`,
`APP_VIDEO_FFPROBE_PATH=/usr/local/bin/ffprobe`를 넣는다.

## 8. `/etc/f2r/env` 최소 항목

```dotenv
SPRING_PROFILES_ACTIVE=prd
DB_HOST=<rds-host>
DB_PORT=3306
DB_NAME=f2r_db
DB_USER=<secret>
DB_PASSWORD=<secret>
JWT_SECRET=<secret>
S3_BUCKET=<private-bucket>
S3_REGION=ap-northeast-2
S3_KEY_PREFIX=prd
ACADEMY_VIDEO_VALIDATION_TEMP_DIR=/var/tmp/f2r/academy-video-validation
ACADEMY_VIDEO_OPTIMIZATION_TEMP_DIR=/var/tmp/f2r/academy-video-optimization
APP_VIDEO_FFMPEG_PATH=/usr/local/bin/ffmpeg
APP_VIDEO_FFPROBE_PATH=/usr/local/bin/ffprobe
```

권한은 `root:root 0600`으로 유지하고 값은 source control, shell history, CI log에 넣지 않는다.

## 9. 배포 순서와 Flyway

1. feature worktree 전체 build와 migration compatibility test.
2. `dev` merge 후 dev DB clean boot/Flyway validate.
3. `stg` merge 후 **production migration set만** stg DB에 적용·validate.
4. §1 인프라 gate와 DB backup/복구 지점을 확인.
5. GitHub Actions `deploy-ec2.yml`을 수동 실행한다.
6. service restart 후 health, logs, metrics, DB backlog, 수동 browser/curl gate를 수행한다.

적용된 V936~V938 파일은 수정·재번호하지 않는다. checksum/out-of-order/validation 실패 시 배포를
중단하고 새 migration으로 보정한다. 상세 브랜치·DB 명령은 `docs/WORKFLOW.md`를 따른다.

## 10. 관찰성·알람

Actuator health와 JVM/process/disk 지표 외에 다음 Micrometer 이름을 기준으로 대시보드를 만든다.

- `academy_video_upload_sessions_total{outcome}` / `academy_video_upload_duration_seconds`
- `academy_video_upload_expired_total` / `academy_video_multipart_incomplete`
- `academy_video_jobs{status}` / `academy_video_job_attempts_total{outcome,reason}`
- `academy_video_job_duration_seconds{stage}` / `academy_video_job_stalls_total`
- `academy_video_job_timeouts_total` / `academy_video_job_lease_reclaims_total`
- `academy_video_source_publish_conflicts_total`
- `academy_video_cleanup_pending` / `academy_video_cleanup_failures_total{asset_type}`

지표 tag에는 session/video/job ID처럼 cardinality가 큰 값, storage key, bucket, signed URL, credential,
filename을 넣지 않는다. 로그에는 correlation ID, attempt, stage, elapsed, safe reason만 넣고 URL/key,
원본 auth, 전체 ffmpeg stderr를 남기지 않는다.

DB 교차 확인:

```sql
SELECT state, COUNT(*) FROM academy_video_upload_sessions GROUP BY state;
SELECT status, COUNT(*) FROM academy_video_optimization_jobs GROUP BY status;
SELECT state, COUNT(*) FROM academy_video_asset_cleanups GROUP BY state;
SELECT COUNT(*) AS overdue_cleanup
FROM academy_video_asset_cleanups
WHERE state <> 'DONE' AND created_at < CURRENT_TIMESTAMP(6) - INTERVAL 30 MINUTE;
SELECT COUNT(*) AS expired_processing
FROM academy_video_optimization_jobs
WHERE status = 'PROCESSING' AND lease_expires_at < CURRENT_TIMESTAMP(6);
```

알람 기준: cleanup 30분 초과 1건 이상, expired PROCESSING 반복, incomplete multipart 지속 증가,
최종 FAILED 비율 증가, `/var/tmp/f2r` free <1GiB, ffmpeg/libx264 smoke 실패.

## 11. 장애 복구

- **INITIATED/COMPLETING 누적:** scheduler/DB clock/lease와 S3 CORS부터 확인한다. 세션 row나 S3 upload를
  수동 삭제하지 말고 reaper와 cleanup outbox가 terminal CAS 후 처리하게 한다.
- **PROCESSING lease 만료:** process 유무와 heartbeat/progress를 확인한다. 새 worker가 expired lease를
  reclaim하며 stale token은 publish할 수 없다.
- **FAILED:** raw는 계속 READY/public이다. 원인을 고친 뒤 임의 attempt_count 초기화보다 명시적 운영
  절차/새 job 정책을 사용한다.
- **cleanup PENDING:** last error와 S3 IAM/network를 고친다. object 부재 Head 또는 NoSuchUpload가 확인될
  때까지 DONE으로 수동 변경하지 않는다.
- **disk pressure:** 새 배포/업로드를 중단하고 process와 DB lease를 대조한다. active 작업 경로를 지우지
  말고 stale sweep 대상만 정리한다.
- **signed GET 문제:** app stable URL의 302/no-store와 Location TTL을 확인한다. 서명 URL을 티켓에 붙이지 않는다.

## 12. 수동 Chrome/curl preproduction evidence gate

새 browser dependency를 추가하지 않고 stg에서 수행한다. 증거에는 signed URL query와 cookie를 redaction한다.

1. Chrome Incognito → DevTools Network → **Preserve log**, **Disable cache**.
2. TUTOR 로그인 후 `/tutor/academy/videos`에서 제한 이하 MP4/MOV를 선택한다.
3. Network에서 `POST /api/tutor/academy/videos` multipart 요청 하나만 발생하고 S3 PUT이 없는지 확인한다.
4. 응답 후 input이 비워지고 목록에 `Ready` 및 thumbnail이 표시되는지 확인한다.
5. 새로고침 후 같은 video가 중복 없이 유지되는지 확인한다.
6. 공개 Academy에서 정규화 MP4를 재생한다.
7. Network에서 app stable request `302`, S3 request `206`, request `Range`, response `Content-Range`,
   실제 media type, `Cache-Control: no-store`를 기록한다.
8. thumbnail 조회 실패 시 원본 video preview로 대체되는지 확인한다.

signed Location을 노출하지 않는 curl 확인:

```bash
curl -sS -D /tmp/video-head.txt -o /dev/null \
  "https://<academy-host>/api/academies/<slug>/videos/<id>/stream"
grep -Ei '^(HTTP/|cache-control:|content-type:|location:)' /tmp/video-head.txt \
  | sed -E 's#(location: https://[^?]+)\?.*#\1?<redacted>#I'

curl -sS -I \
  "https://<academy-host>/api/academies/<slug>/videos/<id>/stream" \
  | grep -Ei '^(HTTP/|cache-control:|content-type:|content-length:|accept-ranges:)'
```

최종 evidence에는 시간, commit SHA, 환경, 파일 크기/duration, terminal state, video/job/cleanup 집계,
redacted HTTP header, Chrome screenshot을 남긴다. signed capability와 사용자 인증정보는 남기지 않는다.

## 13. 이전 운영 메모

2026-07-26 작성된 임시 `docs/temp/ec2-video-deploy-guide.md`는 동기 request-thread ffmpeg 구조,
100MiB/60초/25개 제한, nginx 가정을 포함한 역사 자료다. 현재 실행 지침으로 사용하지 않는다.
이 문서가 해당 내용을 대체한다.
