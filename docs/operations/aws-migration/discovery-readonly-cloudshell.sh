#!/usr/bin/env bash
# 구 AWS 계정(067573916809) 읽기 전용 조사 — 사용자가 본인 콘솔 로그인(MFA)의 CloudShell(리전 ap-northeast-2)에서 직접 실행한다.
# 읽기 전용 호출만 있다(생성·수정·삭제·스냅샷·DNS 변경·S3 객체 읽기 없음). 결과는 사용자 화면에만 나오며
# 공개 저장소의 Actions 로그로 가지 않는다. 키 값·비밀은 조회되지 않지만, 채팅·PR에는 붙여넣지 말고 필요한 항목만 요약해 전달한다.
#
# 조회 실패 처리 원칙: 호출이 실패하면 "FAILED(원인 코드)"를 그대로 출력한다. 실패를 "리소스 없음"이나 "권한 없음"으로 해석하지 않는다
# (원인 코드: AccessDenied=권한, NoSuchEntity/NotFound=리소스 없음, DataUnavailable/OptInRequired=서비스 미활성, 기타=원문 확인).
set -uo pipefail
R=ap-northeast-2

# 계정 ID(12자리)·접근 키 ID는 화면에서도 가려서 복사·캡처 시 유출을 줄인다.
mask() { sed -E 's/[0-9]{12}/<ACCT>/g; s/AKIA[0-9A-Z]{16}/<AKID>/g; s/ASIA[0-9A-Z]{16}/<AKID>/g'; }

# 사용법: run <설명> <aws 인자...> — 성공하면 출력, 실패하면 종료 코드와 AWS 오류 코드를 출력한다.
run() {
  local label="$1"; shift
  local out rc code
  out=$(aws "$@" 2>&1); rc=$?
  if [ $rc -eq 0 ]; then
    printf '%s\n' "$out"
  else
    code=$(printf '%s' "$out" | grep -oE '\(([A-Za-z]+)\)' | head -1 | tr -d '()')
    echo "FAILED: $label (exit=$rc code=${code:-unknown})"
  fi
}

run "caller" sts get-caller-identity --query '{Account:Account,Arn:Arn}' --output table | mask

echo "### RDS (엔진·클래스·암호화·KMS 종류·백업·다중AZ·파라미터 그룹)"
run "rds describe-db-instances" rds describe-db-instances --region $R --output table \
  --query 'DBInstances[].{Id:DBInstanceIdentifier,Engine:Engine,Ver:EngineVersion,Class:DBInstanceClass,GB:AllocatedStorage,StorageType:StorageType,Enc:StorageEncrypted,Backup:BackupRetentionPeriod,MultiAZ:MultiAZ,Param:DBParameterGroups[0].DBParameterGroupName}'
echo "### RDS KMS 키 종류 (aws/rds 기본 키인지 고객 관리형 CMK인지 — 키 ID는 화면에서 가림)"
for K in $(aws rds describe-db-instances --region $R --query 'DBInstances[].KmsKeyId' --output text 2>/dev/null); do
  run "kms describe-key" kms describe-key --region $R --key-id "$K" --query 'KeyMetadata.{Manager:KeyManager,State:KeyState}' --output text
done
echo "### RDS 스냅샷 (유형·암호화 — 기본 키 암호화 스냅샷은 계정 간 공유 불가)"
run "rds describe-db-snapshots" rds describe-db-snapshots --region $R --output table \
  --query 'DBSnapshots[].{Type:SnapshotType,Ver:EngineVersion,Enc:Encrypted,Created:SnapshotCreateTime}'
echo "### RDS 파라미터 그룹 비교용 (문자셋·sql_mode·시간대·max_connections·secure_transport 등 비기본값만)"
for PG in $(aws rds describe-db-instances --region $R --query 'DBInstances[].DBParameterGroups[].DBParameterGroupName' --output text 2>/dev/null); do
  echo "- group=$PG"
  run "rds describe-db-parameters" rds describe-db-parameters --region $R --db-parameter-group-name "$PG" --source user \
    --query 'Parameters[].[ParameterName,ParameterValue]' --output text
done

echo "### RDS 사용량 (CloudWatch, 최근 14일 일 단위 평균/최대)"
START="$(date -u -d '-14 day' +%FT%TZ)"; END="$(date -u +%FT%TZ)"
for DB in $(aws rds describe-db-instances --region $R --query 'DBInstances[].DBInstanceIdentifier' --output text 2>/dev/null); do
  for M in CPUUtilization FreeableMemory DatabaseConnections FreeStorageSpace ReadIOPS WriteIOPS NetworkTransmitThroughput NetworkReceiveThroughput CPUCreditBalance; do
    echo "- $M (일자 평균 최대)"
    # --statistics 는 공백으로 구분한 한 번의 인자 목록으로 전달한다(--statistics 반복 지정이 아님).
    run "cloudwatch $M" cloudwatch get-metric-statistics --region $R --namespace AWS/RDS --metric-name "$M" \
      --dimensions Name=DBInstanceIdentifier,Value="$DB" --start-time "$START" --end-time "$END" --period 86400 \
      --statistics Average Maximum --query 'sort_by(Datapoints,&Timestamp)[].[Timestamp,Average,Maximum]' --output text
  done
done

echo "### KMS 별칭"
run "kms list-aliases" kms list-aliases --region $R --query 'Aliases[].AliasName' --output text
echo "### S3 버킷 (리전·암호화·퍼블릭 차단) — 객체 목록·내용은 읽지 않는다. 버킷 이름은 화면에서도 번호로 대체"
N=0
for B in $(aws s3api list-buckets --query 'Buckets[].Name' --output text 2>/dev/null); do
  N=$((N+1))
  echo "- bucket#$N region=$(aws s3api get-bucket-location --bucket "$B" --query LocationConstraint --output text 2>/dev/null || echo FAILED) enc=$(aws s3api get-bucket-encryption --bucket "$B" --query 'ServerSideEncryptionConfiguration.Rules[0].ApplyServerSideEncryptionByDefault.SSEAlgorithm' --output text 2>/dev/null || echo none/FAILED) pab=$(aws s3api get-public-access-block --bucket "$B" --query 'PublicAccessBlockConfiguration.BlockPublicPolicy' --output text 2>/dev/null || echo none/FAILED)"
done
echo "### Route53 호스팅 영역 (가비아 DNS를 쓰면 비어 있을 수 있음)"
run "route53 list-hosted-zones" route53 list-hosted-zones --query 'HostedZones[].{Records:ResourceRecordSetCount,Private:Config.PrivateZone}' --output table

echo "### EC2·ALB·공인 IPv4 (과금 대상 개수 산정)"
run "ec2 describe-instances" ec2 describe-instances --region $R --output table \
  --query 'Reservations[].Instances[].{Type:InstanceType,State:State.Name,PublicIp:PublicIpAddress!=null,Profile:IamInstanceProfile!=null}'
echo "- Elastic IP(탄력적 IP) 개수:"
run "ec2 describe-addresses" ec2 describe-addresses --region $R --query 'length(Addresses)' --output text
echo "- 공인 IPv4가 붙은 네트워크 인터페이스 개수(EC2·ALB 노드 포함, 과금 대상):"
run "ec2 describe-network-interfaces" ec2 describe-network-interfaces --region $R --query 'length(NetworkInterfaces[?Association.PublicIp!=null])' --output text
run "elbv2 describe-load-balancers" elbv2 describe-load-balancers --region $R --output table \
  --query 'LoadBalancers[].{Type:Type,Scheme:Scheme,AZs:length(AvailabilityZones)}'
echo "### ALB LCU 근거 (CloudWatch AWS/ApplicationELB, 최근 14일 일 단위) — LCU = max(신규연결/25/s, 동시연결/3000, 처리 GB/시, 규칙평가/1000/s)"
for LB in $(aws elbv2 describe-load-balancers --region $R --query 'LoadBalancers[].LoadBalancerArn' --output text 2>/dev/null); do
  DIM=$(printf '%s' "$LB" | sed -E 's#^arn:aws:elasticloadbalancing:[^:]+:[0-9]+:loadbalancer/##')
  for M in ConsumedLCUs NewConnectionCount ActiveConnectionCount ProcessedBytes RequestCount; do
    S=Sum; [ "$M" = ConsumedLCUs ] && S=Average
    echo "- $M ($S, 최대 포함)"
    run "cloudwatch $M" cloudwatch get-metric-statistics --region $R --namespace AWS/ApplicationELB --metric-name "$M" \
      --dimensions Name=LoadBalancer,Value="$DIM" --start-time "$START" --end-time "$END" --period 86400 \
      --statistics $S Maximum --query "sort_by(Datapoints,&Timestamp)[].[Timestamp,$S,Maximum]" --output text
  done
done

echo "### IAM 장기 액세스 키 현황 (키 값은 조회되지 않음, 사용자명·키 ID는 번호로 대체)"
N=0
for U in $(aws iam list-users --query 'Users[].UserName' --output text 2>/dev/null); do
  N=$((N+1))
  echo "- user#$N: $(aws iam list-access-keys --user-name "$U" --query 'AccessKeyMetadata[].[Status,CreateDate]' --output text 2>/dev/null | tr '\t' ':' | tr '\n' ' ')"
done
echo "### OIDC 공급자와 배포 역할 신뢰 정책 (sub 와일드카드 여부 확인)"
run "iam list-open-id-connect-providers" iam list-open-id-connect-providers --query 'length(OpenIDConnectProviderList)' --output text
run "iam get-role" iam get-role --role-name github-actions-f2r-deploy --query 'Role.AssumeRolePolicyDocument' --output json | mask
echo "### 배포 역할 정책 (관리형 이름 + 인라인 정책 문서; 계정 ID는 <ACCT>로 가림, 버킷·리소스 패턴은 권한 판단에 필요해 그대로)"
run "iam list-attached-role-policies" iam list-attached-role-policies --role-name github-actions-f2r-deploy --query 'AttachedPolicies[].PolicyName' --output text
for P in $(aws iam list-role-policies --role-name github-actions-f2r-deploy --query 'PolicyNames' --output text 2>/dev/null); do
  echo "- inline policy: $P"
  run "iam get-role-policy" iam get-role-policy --role-name github-actions-f2r-deploy --policy-name "$P" --query 'PolicyDocument' --output json | mask
done

echo "### 최근 90일 이 역할을 가정한 sub 별 횟수 (어떤 브랜치/환경이 가정했는지)"
aws cloudtrail lookup-events --region $R --lookup-attributes AttributeKey=EventName,AttributeValue=AssumeRoleWithWebIdentity --max-results 50 \
  --query 'Events[].CloudTrailEvent' --output text 2>&1 | python3 -c "
import sys,json,collections
raw=sys.stdin.read()
if 'An error occurred' in raw or 'Unable' in raw: print('FAILED: cloudtrail lookup-events (원문 확인 필요)'); raise SystemExit
c=collections.Counter()
for l in raw.split('\t'):
    try: e=json.loads(l)
    except Exception: continue
    if 'github-actions-f2r-deploy' in json.dumps(e.get('requestParameters',{})): c[e.get('userIdentity',{}).get('userName','?')]+=1
for k,v in c.most_common(): print(v,k)"

echo "### 최근 비용 (월별 합계)"
run "ce get-cost-and-usage total" ce get-cost-and-usage --time-period "Start=$(date -u -d '-60 day' +%F),End=$(date -u +%F)" --granularity MONTHLY --metrics UnblendedCost --query 'ResultsByTime[].[TimePeriod.Start,Total.UnblendedCost.Amount]' --output text
echo "### 최근 비용 (서비스별, 지난 완전한 달)"
# Cost Explorer 는 us-east-1 전역 엔드포인트라 --region 을 지정하지 않는다. 미활성이면 활성 후 최대 24시간 걸린다.
run "ce get-cost-and-usage by service" ce get-cost-and-usage --time-period "Start=$(date -u -d "$(date -u +%Y-%m-01) -1 month" +%F),End=$(date -u +%Y-%m-01)" --granularity MONTHLY --metrics UnblendedCost \
  --group-by Type=DIMENSION,Key=SERVICE --query 'ResultsByTime[].Groups[].[Keys[0],Metrics.UnblendedCost.Amount]' --output text
