#!/usr/bin/env python3
"""New-account diagnostic controller. No deployment, snapshot creation, IAM or DB mutation."""
import argparse
import datetime
import json
import pathlib
import re
import subprocess
import time

ACCOUNT = '425805163960'
REGION = 'ap-northeast-2'
ROLE = f'arn:aws:iam::{ACCOUNT}:role/github-actions-f2r-deploy'
ALLOWED = {('sts', 'get-caller-identity'), ('ec2', 'describe-instances'),
           ('rds', 'describe-db-instances'), ('rds', 'describe-db-snapshots'),
           ('rds', 'describe-account-attributes'), ('iam', 'simulate-principal-policy'),
           ('ssm', 'send-command'), ('ssm', 'get-command-invocation')}


class DiagnosticError(Exception):
    pass


def aws(*args):
    if tuple(args[:2]) not in ALLOWED:
        raise DiagnosticError('AWS operation is outside the fixed diagnostic allowlist')
    run = subprocess.run(['aws', *args, '--region', REGION, '--output', 'json', '--no-cli-pager'],
                         capture_output=True, text=True, timeout=45)
    if run.returncode:
        # Never copy a command response/error containing secrets into the evidence.
        code = re.search(r'\(([A-Za-z0-9.]+)\)', run.stderr)
        raise DiagnosticError(code[1] if code else 'aws_call_failed')
    return json.loads(run.stdout)


def probe(report, name, callback):
    try:
        report[name] = {'status': 'OBSERVED', 'data': callback()}
        return report[name]['data']
    except (DiagnosticError, subprocess.TimeoutExpired, ValueError, KeyError) as error:
        report[name] = {'status': 'UNVERIFIED', 'reason': str(error) if isinstance(error, DiagnosticError)
                        else 'read_permission_or_service_unavailable'}
        return None


def collect():
    report = {'account': ACCOUNT, 'region': REGION, 'observedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'snapshotCreated': False, 'productionWrites': False}
    identity = aws('sts', 'get-caller-identity')
    if identity['Account'] != ACCOUNT:
        raise DiagnosticError('wrong_account_refused')
    instances = aws('ec2', 'describe-instances', '--filters', 'Name=tag:Name,Values=f2r-app',
                    'Name=tag:Project,Values=f2r-migration', 'Name=instance-state-name,Values=running')
    found = [i for r in instances['Reservations'] for i in r['Instances']]
    if len(found) != 1:
        raise DiagnosticError('expected_exactly_one_new_account_instance')
    instance = found[0]
    report['ec2'] = {k: instance[k] for k in ('InstanceId', 'InstanceType', 'State', 'Architecture')}
    db = probe(report, 'rds', lambda: aws('rds', 'describe-db-instances', '--db-instance-identifier', 'f2r-db')['DBInstances'][0])
    expected_host = None
    if db:
        if not db['DBInstanceArn'].startswith(f'arn:aws:rds:{REGION}:{ACCOUNT}:db:'):
            raise DiagnosticError('wrong_rds_account_refused')
        expected_host = db['Endpoint']['Address']
        # Drop endpoint, username and network metadata from the published report.
        report['rds']['data'] = {k: db.get(k) for k in ('DBInstanceIdentifier', 'DBInstanceStatus', 'Engine',
            'EngineVersion', 'StorageEncrypted', 'BackupRetentionPeriod', 'LatestRestorableTime',
            'DeletionProtection', 'PendingModifiedValues', 'DBInstanceClass')}
    probe(report, 'snapshots', lambda: [
        {k: s.get(k) for k in ('DBSnapshotIdentifier', 'Status', 'SnapshotCreateTime', 'Encrypted', 'SnapshotType')}
        for s in aws('rds', 'describe-db-snapshots', '--db-instance-identifier', 'f2r-db')['DBSnapshots']])
    probe(report, 'snapshotQuota', lambda: aws('rds', 'describe-account-attributes')['AccountQuotas'])
    snapshot_arn = f'arn:aws:rds:{REGION}:{ACCOUNT}:snapshot:f2r-predeploy-permission-probe'
    simulation = probe(report, 'snapshotPermissionSimulation', lambda: aws('iam', 'simulate-principal-policy',
          '--policy-source-arn', ROLE, '--action-names', 'rds:CreateDBSnapshot', '--resource-arns',
          f'arn:aws:rds:{REGION}:{ACCOUNT}:db:f2r-db', snapshot_arn)['EvaluationResults'])
    report['snapshotCreation'] = {'status': 'NOT_EXECUTED', 'reason':
        'RDS has no CreateDBSnapshot dry-run; IAM simulation does not prove SCP/KMS/quota/runtime success',
        'simulationAvailable': simulation is not None}
    payload = pathlib.Path(__file__).with_name('ec2-readiness.py').read_text()
    command = "python3 - " + __import__('shlex').quote(expected_host or '') + " <<'F2R_READONLY_PY'\n" + payload + '\nF2R_READONLY_PY'
    sent = aws('ssm', 'send-command', '--instance-ids', instance['InstanceId'],
               '--document-name', 'AWS-RunShellScript', '--parameters', json.dumps({'commands': [command]}),
               '--timeout-seconds', '180')
    command_id = sent['Command']['CommandId']
    report['ssmCommandId'] = command_id
    for _ in range(60):
        try:
            invocation = aws('ssm', 'get-command-invocation', '--command-id', command_id,
                             '--instance-id', instance['InstanceId'])
        except DiagnosticError:
            time.sleep(3)
            continue
        if invocation['Status'] in ('Pending', 'InProgress', 'Delayed'):
            time.sleep(3)
            continue
        if invocation['Status'] != 'Success' or invocation.get('ResponseCode') != 0:
            report['host'] = {'status': 'UNVERIFIED', 'reason': 'ssm_payload_failed'}
        else:
            report['host'] = {'status': 'OBSERVED', 'data': json.loads(invocation['StandardOutputContent'])}
        return report
    report['host'] = {'status': 'UNVERIFIED', 'reason': 'ssm_poll_timeout'}
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--receipt', type=pathlib.Path, required=True)
    args = parser.parse_args()
    try:
        report = collect()
    except (DiagnosticError, subprocess.TimeoutExpired) as error:
        report = {'account': ACCOUNT, 'status': 'UNVERIFIED', 'reason': str(error) if isinstance(error, DiagnosticError)
                  else 'diagnostic_timeout', 'productionWrites': False, 'snapshotCreated': False}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if report.get('status') == 'UNVERIFIED' or report.get('host', {}).get('status') != 'OBSERVED':
        raise SystemExit(2)


if __name__ == '__main__':
    main()
