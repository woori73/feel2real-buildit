# 운영 DB 백업 보존 2일 → 7일 실행 계획 (승인 요청)

대상: `f2r-db-202610181331`(운영, MySQL 8.4.8, db.t4g.micro, 20GB). **아직 실행하지 않았다.**

## 1. 왜 바꾸나
- 현재 보존이 2일이라 이틀이 지난 시점으로는 되돌릴 수 없다. 잔여 DB 2개는 7일이다.
- 10-18 최종 전환 스크립트(`cut-new-db`)에 "백업 보존 설정은 전환 후 별도 적용"이라는 주석이 있지만, 그 적용 단계가 없다.

## 2. 재시작 가능성
- AWS 문서는 **0↔0이 아닌 값으로 바꿀 때만** 중단(outage)이 생긴다고 경고한다. 2→7처럼 0이 아닌 값끼리 바꾸면 "가능한 한 빨리 비동기로 적용"된다(ModifyDBInstance `BackupRetentionPeriod` 설명). → **재부팅·중단 없음이 예상된다.**
- 다만 이 동작을 같은 엔진에서 직접 시험한 적은 없다. 미리 확인하려면 잔여 DB로 먼저 해 볼 수 있지만, 이것도 DB 변경이라 별도 승인이 필요하다(선택 사항).
- **주의 — 대기 중인 다른 변경도 함께 적용될 수 있다.** `--apply-immediately`를 쓰면 대기 중인 변경(`PendingModifiedValues`, 예: 스토리지 유형 변경)이 같이 즉시 적용된다. 그래서 실행 직전에 대기 목록이 비어 있는지 확인하고, 비어 있지 않으면 멈춘다.
- 스토리지 유형(gp2→gp3)은 **이번에 바꾸지 않는다.** 스토리지 최적화 상태가 길게 이어질 수 있어 별도로 다룬다.

## 3. 적용 시점
- 저트래픽 시간대에 실행하고, 결과를 확인할 때까지 배포와 겹치지 않게 한다.
- `--apply-immediately`로 적용하면 상태가 잠시 `modifying`이 됐다가 `available`로 돌아온다. 이 동안에도 연결은 유지될 것으로 예상한다.
- 다음 자동 백업 창(현재 18:52 UTC 무렵)부터 7일 보존이 쌓이기 시작한다. 이미 지난 2일 밖의 시점은 되살아나지 않는다.

## 4. 추가 비용
- RDS 자동 백업과 수동 스냅샷 저장 공간은 리전 안에서 **프로비저닝된 DB 저장 용량 합계까지 무료**다(현재 3개 × 20GB = 60GB).
- 7일 보존으로 늘어나는 것은 그 기간의 증분 변경분이다. 이 DB는 20GB를 할당했지만 실제 데이터는 그보다 작아서, 무료 범위 안일 가능성이 높다. → **추가 비용은 거의 없을 것으로 예상한다.**
- 무료 범위를 넘으면 GB-월 단위로 과금된다(서울 기준 약 $0.095/GB-월, 공개 요금 기준 추정이며 Billing에서 확인 필요).
- 잔여 DB 2개를 나중에 지우면 무료 범위가 20GB로 줄어든다. 그때 수동 스냅샷 4개를 남겨 두면 과금될 수 있다. 정리할 때 함께 판단한다.

## 5. 실행 절차 (승인 후)
실행 경로는 둘 중 하나로 한다. 어느 쪽이든 쓰기 권한이 필요하다.
- A. 소유자가 CloudShell에서 아래 명령을 실행한다(권장, 변경 1건).
- B. `migration-infra-build`에 확인 문자열을 요구하는 단계를 추가하는 PR을 만들고 승인받은 뒤 실행한다.

```bash
DB=f2r-db-202610181331
# 1) 사전 확인: 상태 available, 보존 2, 대기 변경 없음
aws rds describe-db-instances --db-instance-identifier $DB \
  --query 'DBInstances[0].{Status:DBInstanceStatus,Backup:BackupRetentionPeriod,Window:PreferredBackupWindow,Pending:PendingModifiedValues}'
# Pending이 {} 가 아니면 중단하고 보고한다
# 2) 변경
aws rds modify-db-instance --db-instance-identifier $DB --backup-retention-period 7 --apply-immediately \
  --query 'DBInstance.{Status:DBInstanceStatus,Pending:PendingModifiedValues}'
# 3) 확인: available + BackupRetentionPeriod 7
aws rds wait db-instance-available --db-instance-identifier $DB
aws rds describe-db-instances --db-instance-identifier $DB --query 'DBInstances[0].{Status:DBInstanceStatus,Backup:BackupRetentionPeriod,LatestRestorable:LatestRestorableTime}'
```

- 확인 항목: `/actuator/health` 200 유지, ALB 대상 healthy, 앱 로그에 DB 연결 오류 없음, 다음 날 자동 스냅샷 생성. `Prod read-only status`(#875)로 모두 조회할 수 있다.
- 되돌리기: 같은 명령에 `--backup-retention-period 2`를 넣으면 된다. 0이 아닌 값이라 중단은 없다.

## 6. 승인 요청
위 절차의 `modify-db-instance` 1건(보존 기간만 변경, 스토리지·인스턴스 클래스 변경 없음)을 승인해 달라. 실행 경로(A 또는 B)도 함께 정해 달라.
