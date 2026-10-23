# 운영 DB 보호 변경 — CloudShell 실행 안내 (대표님 실행용)

대상은 **`f2r-db-202610181331` 한 개뿐**이다. `f2r-db`, `f2r-db-202610181240`은 건드리지 않는다.

변경 내용은 두 가지를 한 요청으로 보낸다.
- 자동 백업 보존 2일 → 7일
- 삭제 보호 꺼짐 → 켜짐

AWS 콘솔에서 **서울 리전(ap-northeast-2)** 으로 CloudShell을 연다. 아래 블록을 **한 블록씩** 붙여 넣고, 각 블록의 "확인"과 결과가 맞을 때만 다음 블록으로 넘어간다.

---

## 1단계 — 조회 (아무것도 바꾸지 않음)

### 1-1. 계정·리전 확인
```bash
export AWS_DEFAULT_REGION=ap-northeast-2
DB=f2r-db-202610181331
aws sts get-caller-identity --query Account --output text
```
**확인:** `425805163960`이 나와야 한다. 다른 숫자가 나오면 **멈춘다**(다른 계정에 로그인된 상태).

### 1-2. 대상 DB 현재 상태
```bash
aws rds describe-db-instances --db-instance-identifier "$DB" \
  --query 'DBInstances[0].{Id:DBInstanceIdentifier,Status:DBInstanceStatus,BackupDays:BackupRetentionPeriod,DeletionProtection:DeletionProtection,BackupWindow:PreferredBackupWindow,MaintWindow:PreferredMaintenanceWindow,Pending:PendingModifiedValues}' \
  --output json
```
**확인:**
- `"Status": "available"`
- `"BackupDays": 2`
- `"DeletionProtection": false`
- **`"Pending": {}`** (비어 있음)

`Pending`에 무엇이든 들어 있으면 **멈추고 그 내용을 그대로 전달해 달라.** 예: `StorageType`, `DBInstanceClass`, `AllocatedStorage`. 대기 중인 변경이 함께 적용되면 재시작이나 성능 저하가 생길 수 있다.

### 1-3. 예정된 유지 관리 작업 확인 (참고)
```bash
aws rds describe-pending-maintenance-actions \
  --resource-identifier "arn:aws:rds:ap-northeast-2:425805163960:db:$DB" --output json
```
**확인:** `"PendingMaintenanceActions": []`이면 좋다. 항목이 있어도 이번 명령은 그것을 적용하지 않는다(아래 2단계는 즉시 적용 옵션을 쓰지 않는다). 내용은 기록만 해 둔다.

---

## 2단계 — 변경 (1단계가 모두 맞을 때만)

```bash
aws rds modify-db-instance --db-instance-identifier "$DB" \
  --backup-retention-period 7 \
  --deletion-protection \
  --no-apply-immediately \
  --query 'DBInstance.{Id:DBInstanceIdentifier,Status:DBInstanceStatus,BackupDays:BackupRetentionPeriod,DeletionProtection:DeletionProtection,Pending:PendingModifiedValues}' \
  --output json
```

**왜 `--no-apply-immediately`인가:**
- 즉시 적용(`--apply-immediately`)을 쓰면 대기 중인 다른 변경까지 함께 즉시 적용된다. 이 옵션을 빼서 그 위험을 없앤다.
- 0이 아닌 값끼리 바꾸는 백업 보존 변경(2→7)은 AWS가 "가능한 한 빨리 비동기로" 적용한다.
- 삭제 보호 설정은 재시작 없이 바로 반영된다.
- 따라서 재부팅·중단은 없을 것으로 예상한다.

**확인:** 오류 없이 JSON이 나오면 된다. 이 시점에 `Status`가 잠시 `modifying`으로 보일 수 있다.

---

## 3단계 — 사후 검증

### 3-1. 적용 대기 (보통 몇 분)
```bash
aws rds wait db-instance-available --db-instance-identifier "$DB" && echo available
```

### 3-2. 최종 값 확인
```bash
aws rds describe-db-instances --db-instance-identifier "$DB" \
  --query 'DBInstances[0].{Id:DBInstanceIdentifier,Status:DBInstanceStatus,BackupDays:BackupRetentionPeriod,DeletionProtection:DeletionProtection,LatestRestorable:LatestRestorableTime,Pending:PendingModifiedValues}' \
  --output json
```
**확인:** `"Status": "available"`, **`"BackupDays": 7`**, **`"DeletionProtection": true`**, `"Pending": {}`.

`BackupDays`가 10분이 지나도 2이고 `Pending`에 `BackupRetentionPeriod: 7`만 보이면, 대기 변경이 그것 하나뿐인 상태다. 이때만 아래 명령으로 즉시 적용한다. `Pending`에 다른 항목이 함께 있으면 실행하지 말고 전달해 달라.
```bash
aws rds modify-db-instance --db-instance-identifier "$DB" --backup-retention-period 7 --deletion-protection --apply-immediately \
  --query 'DBInstance.PendingModifiedValues' --output json
```

### 3-3. 다른 두 DB가 그대로인지 확인
```bash
aws rds describe-db-instances \
  --query "DBInstances[?starts_with(DBInstanceIdentifier,'f2r-')].{Id:DBInstanceIdentifier,BackupDays:BackupRetentionPeriod,DeletionProtection:DeletionProtection,Status:DBInstanceStatus}" \
  --output table
```
**확인:** `f2r-db`와 `f2r-db-202610181240`은 `BackupDays 7`, `DeletionProtection False`로 이전과 같아야 한다.

### 3-4. 앱 영향 확인
아래 둘 중 하나로 확인한다.
- 브라우저에서 `https://feel2real.co.kr` 접속이 정상인지 본다.
- 알려 주시면 내가 `Prod read-only status`를 실행해 health 200, ALB healthy, 재시작 0회 여부와 위 값을 한 번 더 조회한다.

---

## 되돌리기 (필요할 때만)
- 보존 기간: 같은 명령에 `--backup-retention-period 2`를 넣는다. 0이 아닌 값이라 중단은 없다.
- 삭제 보호: `--no-deletion-protection`을 넣는다. 삭제 보호를 켜도 DB 운영·배포에는 영향이 없다. 영향은 DB 삭제가 막히는 것뿐이다.

## 비용
- 삭제 보호는 비용이 없다.
- 백업 7일은 리전 DB 용량 합계(60GB)까지 무료 범위라 거의 추가 비용이 없을 것으로 예상한다. 넘는 부분은 GB-월당 약 $0.095이며, 추정값이므로 Billing에서 확인한다.
