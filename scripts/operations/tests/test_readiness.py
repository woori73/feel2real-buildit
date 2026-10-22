import importlib.util
import hashlib
import json
import pathlib
import tempfile
import subprocess
import unittest
from unittest.mock import patch
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[3]


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


diag = load('scripts/operations/production-readiness.py', 'diag')
host = load('scripts/operations/ec2-readiness.py', 'host')
imp = load('scripts/ai-agent-studio/import-production.py', 'imp')


class ReadinessTest(unittest.TestCase):
    def fake_aws(self, *args):
        self.calls.append(args)
        pair = args[:2]
        if pair == ('sts', 'get-caller-identity'):
            return {'Account': self.account}
        if pair == ('ec2', 'describe-instances'):
            return {'Reservations': [{'Instances': [{'InstanceId': 'i-isolated', 'InstanceType': 't3.medium',
                'State': {'Name': 'running'}, 'Architecture': 'x86_64'}] * self.instance_count}]}
        if pair == ('rds', 'describe-db-instances'):
            return {'DBInstances': [{'DBInstanceArn': f'arn:aws:rds:{diag.REGION}:{diag.ACCOUNT}:db:f2r-db',
                'Endpoint': {'Address': 'new-account.example.rds.amazonaws.com'}, 'MasterUsername': 'DO_NOT_PUBLISH',
                'DBInstanceIdentifier': 'f2r-db', 'DBInstanceStatus': 'available'}]}
        if pair == ('iam', 'simulate-principal-policy'):
            raise diag.DiagnosticError('AccessDenied')
        if pair == ('rds', 'describe-account-attributes'):
            return {'AccountQuotas': []}
        if pair == ('rds', 'describe-db-snapshots'):
            return {'DBSnapshots': []}
        if pair == ('ssm', 'send-command'):
            return {'Command': {'CommandId': 'isolated-command'}}
        if pair == ('ssm', 'get-command-invocation'):
            return {'Status': 'Success', 'ResponseCode': 0,
                    'StandardOutputContent': json.dumps({'memory': {'MemAvailableKiB': 600000},
                        'databaseEndpointSha256': hashlib.sha256(b'new-account.example.rds.amazonaws.com').hexdigest()})}
        raise AssertionError(args)

    def setUp(self):
        self.calls = []
        self.account = diag.ACCOUNT
        self.instance_count = 1

    def test_wrong_account_never_sends_ssm_or_reads_database(self):
        self.account = '067573916809'
        with patch.object(diag, 'aws', self.fake_aws), self.assertRaises(diag.DiagnosticError):
            diag.collect()
        self.assertEqual([('sts', 'get-caller-identity')], self.calls)

    def test_ambiguous_ec2_never_selects_first_instance(self):
        self.instance_count = 2
        with patch.object(diag, 'aws', self.fake_aws), self.assertRaises(diag.DiagnosticError):
            diag.collect()
        self.assertFalse(any(c[:2] == ('ssm', 'send-command') for c in self.calls))

    def test_denied_simulation_is_unverified_and_never_creates_snapshot(self):
        with patch.object(diag, 'aws', self.fake_aws):
            result = diag.collect()
        self.assertEqual('UNVERIFIED', result['snapshotPermissionSimulation']['status'])
        self.assertFalse(result['snapshotCreated'])
        self.assertEqual('NOT_EXECUTED', result['snapshotCreation']['status'])
        self.assertTrue(all(c[:2] in diag.ALLOWED for c in self.calls))
        self.assertNotIn('DO_NOT_PUBLISH', json.dumps(result))
        self.assertNotIn('Endpoint', result['rds']['data'])

    def test_mutations_cannot_pass_controller_allowlist(self):
        for command in [('rds', 'create-db-snapshot'), ('iam', 'put-role-policy'), ('ec2', 'stop-instances')]:
            with self.assertRaises(diag.DiagnosticError), patch.object(diag.subprocess, 'run') as run:
                diag.aws(*command)
            run.assert_not_called()

    def test_env_parsing_does_not_evaluate_shell_or_publish_extra_keys(self):
        with tempfile.TemporaryDirectory() as temp:
            path = pathlib.Path(temp) / 'env'
            path.write_text("export DB_HOST='new-host'\nDB_PASSWORD='value with spaces'\nAPI_SECRET=not-selected\nDB_NAME=f2r_db\n")
            values = host.env_values(path)
        self.assertEqual('value with spaces', values['DB_PASSWORD'])
        self.assertNotIn('API_SECRET', values)

    def test_service_flyway_metadata_is_read_only_even_when_rds_describe_is_denied(self):
        # The new-account EC2's configured service DB can be read independently of control-plane IAM.
        values = dict(DB_HOST='f2r-db.example.ap-northeast-2.rds.amazonaws.com', DB_NAME='f2r_db',
                      DB_USERNAME='app_user', DB_PASSWORD='NEVER_PRINT_THIS_SECRET')
        mysql_calls = []
        original = pathlib.Path.read_text
        def read(path, *args, **kwargs):
            if str(path) == '/proc/meminfo':
                return 'MemAvailable: 600000 kB\n'
            return original(path, *args, **kwargs)
        def command(argv, **kwargs):
            if argv[0] == 'systemctl':
                return subprocess.CompletedProcess(argv, 0, 'MainPID=0\nActiveState=active', '')
            if '--version' in argv:
                return subprocess.CompletedProcess(argv, 0, 'mysql Ver 8.4', '')
            mysql_calls.append((argv, kwargs))
            return subprocess.CompletedProcess(argv, 0, 'Ssl_cipher\tTLS_AES_256_GCM_SHA384\n{"version":"1093","success":1}\n', '')
        with patch.object(host, 'env_values', return_value=values), patch.object(host, 'jar_info', return_value={}), \
             patch.object(pathlib.Path, 'read_text', read), patch.object(host.shutil, 'which', return_value='/usr/bin/mysql'), \
             patch.object(host, 'command', command):
            result = host.collect('')
        self.assertEqual('OBSERVED', result['flyway']['status'])
        self.assertEqual('UNVERIFIED', result['databaseAccountVerification'])
        argv, kwargs = mysql_calls[0]
        self.assertIn('START TRANSACTION READ ONLY', argv[-1])
        self.assertNotIn(values['DB_PASSWORD'], ' '.join(argv))
        self.assertEqual(values['DB_PASSWORD'], kwargs['env']['MYSQL_PWD'])
        self.assertNotIn(values['DB_PASSWORD'], json.dumps(result))

    def test_rds_matching_uses_actual_service_endpoint_instead_of_fixed_database_name(self):
        def multiple(*args):
            result = self.fake_aws(*args)
            if args[:2] == ('rds', 'describe-db-instances'):
                result['DBInstances'].insert(0, dict(result['DBInstances'][0],
                    Endpoint={'Address': 'wrong.example.rds.amazonaws.com'}, DBInstanceIdentifier='f2r-old-clone'))
            return result
        with patch.object(diag, 'aws', multiple):
            result = diag.collect()
        self.assertEqual('f2r-db', result['rds']['data']['DBInstanceIdentifier'])
        self.assertEqual('MATCHED_NEW_ACCOUNT_RDS', result['host']['data']['databaseAccountVerification'])

    def test_old_or_dirty_jar_never_claims_main_sha(self):
        with tempfile.TemporaryDirectory() as temp:
            jar = pathlib.Path(temp) / 'app.jar'
            with zipfile.ZipFile(jar, 'w') as out:
                out.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nBuild-Commit: ' + 'a' * 40 + '\nBuild-Working-Tree: dirty\n')
            self.assertIsNone(host.jar_info(jar)['gitSha'])
            with zipfile.ZipFile(jar, 'w') as out:
                out.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nBuild-Commit: ' + 'b' * 40 + '\nBuild-Working-Tree: clean\n')
            self.assertEqual('b' * 40, host.jar_info(jar)['gitSha'])


class ImportPolicyTest(unittest.TestCase):
    def args(self, **kwargs):
        import argparse
        values = dict(base_url=None, production=True, apply=False, approval_file=None)
        values.update(kwargs)
        return argparse.Namespace(**values)

    def test_production_dry_run_needs_no_auth_or_approval_and_makes_no_requests(self):
        with patch.object(imp.urllib.request, 'build_opener') as request:
            self.assertEqual(imp.PRODUCTION_ORIGIN, imp.target(self.args()))
        request.assert_not_called()

    def test_apply_without_approval_is_refused(self):
        with self.assertRaises(ValueError):
            imp.target(self.args(apply=True))

    def test_arbitrary_and_credential_urls_are_refused(self):
        for url in ['https://evil.example', 'http://feel2real.co.kr', 'https://feel2real.co.kr?other=1',
                    'https://user:secret@feel2real.co.kr', 'https://feel2real.co.kr/#fragment']:
            with self.assertRaises(ValueError):
                imp.target(self.args(base_url=url))

    def test_approval_is_bound_to_exact_account_project_origin_source(self):
        approval = dict(approved=True, action='studio-source-import', account='425805163960',
                        origin=imp.PRODUCTION_ORIGIN, projectId=imp.PROJECT, sourceCommit=imp.PIN,
                        approvedBy='isolated-test', reference='TEST-ONLY-NOT-AN-APPROVAL')
        with tempfile.TemporaryDirectory() as temp:
            path = pathlib.Path(temp) / 'approval.json'
            path.write_text(json.dumps(approval))
            self.assertEqual(imp.PRODUCTION_ORIGIN, imp.target(self.args(apply=True, approval_file=path)))
            for key in ('account', 'projectId', 'sourceCommit', 'origin', 'action'):
                changed = dict(approval, **{key: 'wrong'})
                path.write_text(json.dumps(changed))
                with self.assertRaises(ValueError):
                    imp.target(self.args(apply=True, approval_file=path))

    def test_pagination_reads_all_678_revisions(self):
        def call(path):
            offset = int(path.split('offset=')[1])
            return json.dumps(list(range(678))[offset:offset+200])
        self.assertEqual(list(range(678)), imp.pages(call, 'revisions'))

    def test_redirect_never_forwards_session(self):
        with self.assertRaises(ValueError):
            imp.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://evil.example')


if __name__ == '__main__':
    unittest.main()
