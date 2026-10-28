#!/usr/bin/env bash
# Run once in the production account's Seoul CloudShell with an authorized admin session.
# Creates an IaC stack, not a release. No static AWS keys or GitHub secrets required.
set -euo pipefail
cd "$(dirname "$0")/../../.."
export AWS_REGION=ap-northeast-2 AWS_DEFAULT_REGION=ap-northeast-2 AWS_PAGER=''
account=$(aws sts get-caller-identity --query Account --output text)
[[ "$account" == 425805163960 ]] || { echo 'Wrong AWS account'; exit 1; }
kit_tmp=$(mktemp -d)
trap 'rm -rf "$kit_tmp"' EXIT
aws ec2 describe-instances --instance-ids i-0414de33d2803c723 --output json > "$kit_tmp/instance.json"
python3 - "$kit_tmp/instance.json" > "$kit_tmp/profile" <<'PY'
import json,sys
x=json.load(open(sys.argv[1]))['Reservations'][0]['Instances'][0]
tags={t['Key']:t['Value'] for t in x.get('Tags',[])}
assert x['State']['Name']=='running' and tags.get('Name')=='f2r-app' and tags.get('Project')=='f2r-migration', 'Unexpected production target'
print(x['IamInstanceProfile']['Arn'].rsplit('/',1)[1])
PY
online=$(aws ssm describe-instance-information --filters Key=InstanceIds,Values=i-0414de33d2803c723 --query 'InstanceInformationList[0].PingStatus' --output text)
[[ "$online" == Online ]] || { echo 'Production instance is not SSM Online'; exit 1; }
# Read version 1 without updating/deleting/importing the user's existing document.
aws ssm get-document --name F2R-914-MediaAudit --document-version 1 --document-format JSON --output json > "$kit_tmp/audit.json"
python3 - "$kit_tmp/audit.json" <<'PY'
import json,sys
actual=json.loads(json.load(open(sys.argv[1]))['Content'])
expected=json.load(open('infra/pr914/F2R-914-MediaAudit.json'))
assert actual==expected, 'Existing MediaAudit differs; preserve it and report for review, do not overwrite'
print('Existing MediaAudit version 1 preserved and matched')
PY
profile=$(cat "$kit_tmp/profile")
instance_role=$(aws iam get-instance-profile --instance-profile-name "$profile" --query 'InstanceProfile.Roles[0].RoleName' --output text)
[[ "$instance_role" != None && -n "$instance_role" ]] || exit 1
aws iam get-role --role-name github-actions-f2r-readonly --query Role.AssumeRolePolicyDocument --output json > "$kit_tmp/trust.json"
subject=$(python3 - "$kit_tmp/trust.json" <<'PY'
import json,sys
values=[]
for s in json.load(open(sys.argv[1]))['Statement']:
 c=s.get('Condition',{}).get('StringEquals',{})
 if c.get('token.actions.githubusercontent.com:aud')=='sts.amazonaws.com':
  v=c.get('token.actions.githubusercontent.com:sub'); values.extend(v if isinstance(v,list) else [v])
allowed=['repo:woori73@299170593/feel2real-buildit@1340651018:ref:refs/heads/main','repo:woori73/feel2real-buildit:ref:refs/heads/main']
values=[x for x in values if x in allowed]
assert len(values)==1,'Cannot resolve one approved main OIDC subject'
print(values[0])
PY
)
python3 scripts/operations/pr914/generate.py
aws cloudformation validate-template --template-body file://infra/pr914/stack.json > /dev/null
aws cloudformation deploy --stack-name f2r-pr914-operations --template-file infra/pr914/stack.json \
  --capabilities CAPABILITY_NAMED_IAM --no-fail-on-empty-changeset \
  --parameter-overrides "ExistingInstanceRoleName=$instance_role" "GitHubMainSubject=$subject" \
  --tags Project=f2r-migration Purpose=pr914-fixed-release
aws cloudformation describe-stacks --stack-name f2r-pr914-operations --query 'Stacks[0].{Status:StackStatus,Outputs:Outputs}' --output json
printf '%s\n' 'Stack installed. No production JAR, media or DB was modified. Next: PR914 automated release workflow, operation=preflight.'
