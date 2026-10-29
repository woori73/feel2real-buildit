# 운영 배포 기준 (Production baseline)

운영에서 읽은 JAR과 배포 보관 증거를 기준으로 기록한다. main 최신 커밋은 배포 완료를 의미하지 않는다. 새 배포가 성공하면 관측 시각·전체 해시·실행 기록을 함께 갱신한다.

## 현재 관측 기준 — 2026-10-29 02:21:39 UTC

| 항목 | 확인된 값과 근거 |
|---|---|
| 운영 JAR과 일치하는 배포 커밋 | `38f40090e49f6cd2641865ce538aebea033546ef` |
| 식별 방법 | 현재 JAR 전체 SHA-256이 아래 배포 보관 영수증과 일치. `app.jar.meta`는 없으므로 실행 마커로 확인한 커밋은 아님 |
| 현재 JAR | `/home/ec2-user/app.jar`, 234,270,368 bytes |
| 현재 JAR SHA-256 | `a28100f0bdbc419ea52adc3c76ffcc536a4157705b28838297eb0551907f7ca2` |
| 기존 배포 기록 | [Deploy to EC2 40656618382](https://github.com/woori73/feel2real-buildit/actions/runs/40656618382), 2026-10-28, attempt 1 |
| 읽기 전용 관측 | [40308339478 / attempt 4](https://github.com/woori73/feel2real-buildit/actions/runs/40308339478/attempts/4), job `123224544583`; 고정 SSM 문서 `F2R-ReadOnlyStatus` |
| 서비스 | active/running, NRestarts 0, 시작 2026-10-28 08:29:17 UTC, health HTTP 200, ALB 8080·8082 healthy |
| 연결 DB | `f2r-db-202610181331`, MySQL 8.4.8; Flyway 191건, 최대 V1096, 실패 0 |
| DB 백업 조회 | 7일 자동 백업, 암호화·삭제 보호 ON, latest restorable 2026-10-29 02:16:55 UTC; 2026-10-28 18:52:20.570 UTC 자동 스냅샷 available |

관측 이후 상태가 계속 동일하다는 보장은 아니다. 다음 배포 직전에 현재 JAR/서비스/백업을 다시 읽어 비교해야 한다. DB 연결·백업 메타데이터 PASS는 전체 데이터 무결성이나 실제 복원 시험 PASS와 다르다.

## 현재 JAR, 이전 JAR, 보관본 구분

| 대상 | 상태 |
|---|---|
| `app.jar.previous` | 234,264,626 bytes, SHA-256 `18ca78cb7743fbd9e94e055fa5c6a4cd6fb5ca1011885b63d866f96eaa1dcdc0`; df425588 복구본과 일치. **현재 38f40090의 백업이 아님** |
| 현재 버전 배포 보관본 | `s3://f2r-deploy-425805163960/releases/38f40090e49f6cd2641865ce538aebea033546ef/40656618382-1/app.jar`; 영수증의 크기·해시가 현재 JAR과 일치 |
| 보관본 검증 범위 | 2026-10-28 08:28:06 UTC 생성 영수증 재사용. S3 객체의 현재 존재·읽기 권한·새 격리 복원은 미검증 |
| PR914 고정 백업 | `/var/backups/f2r-pr914-df425588`; 2026-10-28 MediaAudit 검증 성공 기록 있음. 현재 버전 백업으로 혼용 금지 |
| 실제 복구 권한 | 현재 실행 주체의 S3 읽기·JAR 복원·서비스 재시작 권한은 미검증. 읽기 전용 SSM 성공으로 대체하지 않음 |

일반 배포는 [AWS_DEPLOYMENT.md](AWS_DEPLOYMENT.md)와 `.github/workflows/deploy-ec2.yml`을 따른다. PR914 전용 자동화는 df425588 및 승인 UI 파일 3개에 고정되어 있으며, PR923 전체 JAR 배포나 38f40090 복구 경로로 사용할 수 없다. 고정 검사값을 바꾸거나 예전 배포 작업을 재실행하여 우회하지 않는다.

## 미디어 관측 범위

2026-10-28 [PR914 실행 40625991893](https://github.com/woori73/feel2real-buildit/actions/runs/40625991893)의 보존된 MediaAudit 출력에서 경로 `/home/ec2-user/agent-media`, 세븐·서수니 이미지/MP4 6개와 백업 검증을 확인했다. 파일별 해시는 [증거 JSON](operations/homepage-gates-20261029/evidence.json)의 `historicalMedia`에 보존한다.

이는 **과거 확인**이다. 현재 실제 경로·원본 해시·백업 해시, AI AGENT Studio 전체 자료의 백업 범위는 이번 연결에서 재조회하지 못했다. 기존 미디어 파일을 수정·삭제·복원하지 않았다.

## 다음 홈페이지 후보 — 아직 운영 미배포

- main 확인값: `71e33cdfc2f054dcf290c0731a24ecb923af4440` (PR923 병합, PR922 정책 연결 포함).
- [main Build Check 40805496826](https://github.com/woori73/feel2real-buildit/actions/runs/40805496826): build·MySQL PASS. 같은 소스 트리의 PR 모바일/비서 CI 48/48 PASS 재사용.
- [승인 미디어](home-exam/APPROVED-MEDIA-20261029.md): 새 MP4·PNG는 업로드 원본과 해시 일치. 운영에서 표시됐다고 판정하지 않는다.
- 신청·고객 계좌 공개는 OFF 유지가 요구된다. 코드 기본값은 false이나 **실제 운영 설정값은 미검증**이다.
- 현재 버전 대비 migration 파일 변경 없음. 이것만으로 DB 쓰기나 모든 데이터 무결성이 검증된 것은 아니다.
- 일반 홈페이지 배포와 신청 활성화의 조건을 분리한 [검증/차단 목록](operations/homepage-gates-20261029/README.md)을 따른다. 현재 둘 다 보류다.
- 일반 배포 workflow는 `workflow_dispatch` 전용이다. 이번 문서 변경·PR 생성·CI 실행으로 운영을 배포하지 않는다.

## 이전 문서 보존

2026-10-17의 e034492f 기준 문서는 [당시 원문](DEPLOYED_BASELINE-20261017.md)으로 바이트 그대로 보존했다. 그 문서의 ‘현재 운영’, ‘미배포 PR 목록’, 메모리/인스턴스 값은 **당시 기록이며 현재 상태가 아니다**. 후속 변경 이력을 모두 재구성했다고 주장하지 않는다.
