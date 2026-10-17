#!/usr/bin/env bash
# 구 AWS 계정(067573916809) 읽기 전용 조사 — 사용자가 본인 콘솔 로그인(MFA)의 CloudShell(리전 ap-northeast-2)에서 직접 실행한다.
# 읽기 전용 호출만 있다(생성·수정·삭제·스냅샷·DNS 변경·S3 객체 읽기 없음). 결과는 사용자 화면에만 나오며
# 공개 저장소의 Actions 로그로 가지 않는다. 출력에는 키 값·비밀이 없지만(메타데이터만) 채팅·PR에는 붙여넣지 말고
# 필요한 항목만 요약해서 전달한다.
set -uo pipefail
R=ap-northeast-2
aws sts get-caller-identity --query '{Account:Account,Arn:Arn}' --output table
echo "### RDS (엔진·클래스·암호화·KMS·백업·다중AZ·서브넷그룹)"
aws rds describe-db-instances --region $R --query 'DBInstances[].{Id:DBInstanceIdentifier,Engine:Engine,Ver:EngineVersion,Class:DBInstanceClass,GB:AllocatedStorage,Enc:StorageEncrypted,Kms:KmsKeyId,Backup:BackupRetentionPeriod,MultiAZ:MultiAZ,Param:DBParameterGroups[0].DBParameterGroupName}' --output table
echo "### RDS 스냅샷 (유형·암호화·KMS — 계정 간 공유 가능 여부: 기본 AWS 관리형 키로 암호화된 스냅샷은 공유 불가)"
aws rds describe-db-snapshots --region $R --query 'DBSnapshots[].{Id:DBSnapshotIdentifier,Type:SnapshotType,Ver:EngineVersion,Enc:Encrypted,Kms:KmsKeyId,Created:SnapshotCreateTime}' --output table
echo "### RDS 사용량 (CloudWatch, 최근 14일 일 단위 평균/최대) — 인스턴스 크기·송신량 산정 근거"
for DB in $(aws rds describe-db-instances --region $R --query 'DBInstances[].DBInstanceIdentifier' --output text); do
  for M in CPUUtilization FreeableMemory DatabaseConnections FreeStorageSpace ReadIOPS WriteIOPS NetworkTransmitThroughput NetworkReceiveThroughput; do
    echo "- $DB $M (일자 평균 최대)"
    aws cloudwatch get-metric-statistics --region $R --namespace AWS/RDS --metric-name "$M" --dimensions Name=DBInstanceIdentifier,Value="$DB" \
      --start-time "$(date -u -d '-14 day' +%FT%TZ)" --end-time "$(date -u +%FT%TZ)" --period 86400 --statistics Average Maximum \
      --query 'sort_by(Datapoints,&Timestamp)[].[Timestamp,Average,Maximum]' --output text
  done
done
echo "### KMS 별칭"
aws kms list-aliases --region $R --query 'Aliases[].{Alias:AliasName,Key:TargetKeyId}' --output table
echo "### S3 버킷 (이름·리전·암호화·퍼블릭 차단) — 객체 목록·내용은 읽지 않는다"
for B in $(aws s3api list-buckets --query 'Buckets[].Name' --output text); do
  echo "- $B region=$(aws s3api get-bucket-location --bucket "$B" --query LocationConstraint --output text 2>/dev/null) enc=$(aws s3api get-bucket-encryption --bucket "$B" --query 'ServerSideEncryptionConfiguration.Rules[0].ApplyServerSideEncryptionByDefault.SSEAlgorithm' --output text 2>/dev/null) pab=$(aws s3api get-public-access-block --bucket "$B" --query 'PublicAccessBlockConfiguration.BlockPublicPolicy' --output text 2>/dev/null)"
done
echo "### Route53 호스팅 영역 (가비아 DNS를 쓰면 비어 있을 수 있음)"
aws route53 list-hosted-zones --query 'HostedZones[].{Name:Name,Records:ResourceRecordSetCount,Private:Config.PrivateZone}' --output table
echo "### EC2·ALB"
aws ec2 describe-instances --region $R --query 'Reservations[].Instances[].{Id:InstanceId,Type:InstanceType,State:State.Name,Profile:IamInstanceProfile.Arn}' --output table
aws elbv2 describe-load-balancers --region $R --query 'LoadBalancers[].{Name:LoadBalancerName,Scheme:Scheme,Type:Type}' --output table
echo "### IAM 장기 액세스 키 현황 (키 값은 조회되지 않음)"
for U in $(aws iam list-users --query 'Users[].UserName' --output text); do
  echo "- $U: $(aws iam list-access-keys --user-name "$U" --query 'AccessKeyMetadata[].[AccessKeyId,Status]' --output text | tr '\t' ':' | tr '\n' ' ')"
done
echo "### OIDC 공급자와 배포 역할 신뢰 정책 (sub 와일드카드 여부 확인)"
aws iam list-open-id-connect-providers --output text
aws iam get-role --role-name github-actions-f2r-deploy --query 'Role.AssumeRolePolicyDocument' --output json
echo "### 배포 역할에 붙은 권한"
aws iam list-attached-role-policies --role-name github-actions-f2r-deploy --output table
aws iam list-role-policies --role-name github-actions-f2r-deploy --output table
echo "### 최근 90일 이 역할을 가정한 이력 (sub: 어떤 브랜치/환경이 가정했는지)"
aws cloudtrail lookup-events --region $R --lookup-attributes AttributeKey=EventName,AttributeValue=AssumeRoleWithWebIdentity --max-results 50 \
  --query 'Events[].CloudTrailEvent' --output text | python3 -c "
import sys,json,collections
c=collections.Counter()
for l in sys.stdin.read().split('\t'):
    try: e=json.loads(l)
    except Exception: continue
    if 'github-actions-f2r-deploy' in json.dumps(e.get('requestParameters',{})): c[e.get('userIdentity',{}).get('userName','?')]+=1
for k,v in c.most_common(): print(v,k)"
echo "### 최근 비용 (월별)"
aws ce get-cost-and-usage --time-period "Start=$(date -u -d '-60 day' +%F),End=$(date -u +%F)" --granularity MONTHLY --metrics UnblendedCost --query 'ResultsByTime[].[TimePeriod.Start,Total.UnblendedCost.Amount]' --output text
