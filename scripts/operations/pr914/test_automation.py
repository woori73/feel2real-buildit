import contextlib
import hashlib
import io
import json
import os
import shutil
import tempfile
import types
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch
import generate
import overlay
import github_release


class OverlayTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        self.baseline=self.root/'baseline.jar';self.patch=self.root/'patch.zip';self.output=self.root/'output.jar'
        self.name='BOOT-INF/classes/templates/public/landing.html'
        self.approved={self.name:hashlib.sha256(b'approved').hexdigest()}
        with zipfile.ZipFile(self.baseline,'w') as z:
            z.writestr(self.name,b'old');z.writestr('BOOT-INF/classes/App.class',b'class');z.writestr('BOOT-INF/classes/db/migration/V1.sql',b'unchanged')
        self.make_patch()
    def make_patch(self,entries=None):
        with zipfile.ZipFile(self.patch,'w') as z:
            for name,data in (entries or {self.name:b'approved'}).items():z.writestr(name,data)
    def build(self):overlay.build_overlay(self.baseline,self.patch,self.output,self.approved)
    def test_overlay_preserves_java_and_database_entries(self):
        self.build()
        with zipfile.ZipFile(self.output) as z:
            self.assertEqual(z.read(self.name),b'approved');self.assertEqual(z.read('BOOT-INF/classes/App.class'),b'class');self.assertEqual(z.read('BOOT-INF/classes/db/migration/V1.sql'),b'unchanged')
    def test_unapproved_java_is_rejected(self):
        self.make_patch({self.name:b'approved','BOOT-INF/classes/App.class':b'changed'})
        with self.assertRaisesRegex(RuntimeError,'unapproved'):self.build()
        self.assertFalse(self.output.exists())
    def test_resource_content_tampering_is_rejected(self):
        self.make_patch({self.name:b'not-approved'})
        with self.assertRaisesRegex(RuntimeError,'differs'):self.build()
    def test_traversal_is_rejected(self):
        self.make_patch({'../app.jar':b'x'})
        with self.assertRaisesRegex(RuntimeError,'unsafe'):self.build()
    def test_signed_baseline_is_rejected(self):
        with zipfile.ZipFile(self.baseline,'a') as z:z.writestr('META-INF/SIGN.RSA',b'signature')
        with self.assertRaisesRegex(RuntimeError,'signed'):self.build()
    def test_duplicate_zip_members_are_rejected(self):
        with zipfile.ZipFile(self.patch,'a') as z:
            import warnings
            with warnings.catch_warnings():warnings.simplefilter('ignore');z.writestr(self.name,b'approved')
        with self.assertRaisesRegex(RuntimeError,'duplicate'):self.build()
    def test_original_jar_never_overwritten(self):
        before=self.baseline.read_bytes();self.build();self.assertEqual(before,self.baseline.read_bytes())


class HostTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        self.ops=types.ModuleType('host')
        # Load the union needed for scenarios, without the actual SSM entrypoint.
        for entry in ('backup_or_verify','deploy','rollback','verify_permission'):
            exec(generate.host_source(entry),self.ops.__dict__)
        o=self.ops;o.STAGING_ROOT=str(self.root);o.JAR=self.root/'app.jar';o.BACKUP=self.root/'backup';o.RECEIPT=o.BACKUP/'deployment.json';o.ENV_FILES=[self.root/'app.env',self.root/'wiki.env']
        for p in o.ENV_FILES:p.write_bytes(b'local-test-fixture')
        name='BOOT-INF/classes/templates/public/landing.html'
        with zipfile.ZipFile(o.JAR,'w') as z:z.writestr(name,b'old');z.writestr('BOOT-INF/classes/App.class',b'unchanged-class')
        o.EXPECTED=o.digest(o.JAR)['sha256'];o.APPROVED_FILES={name:hashlib.sha256(b'approved').hexdigest()}
        self.payload=self.root/'payload.zip'
        with zipfile.ZipFile(self.payload,'w') as z:z.writestr(name,b'approved')
        self.media=self.root/'media'
        for agent in ('seven','suni'):
            d=self.media/agent;d.mkdir(parents=True);(d/'poster.jpg').write_bytes(b'approved-image');(d/'idle.mp4').write_bytes(b'approved-video')
        self.calls=[]
        def command(*args):
            self.calls.append(args)
            if args[0]=='aws':shutil.copyfile(self.payload,args[-1]);return ''
            if 'RefuseManualStart' in args or 'RefuseManualStop' in args:return 'no'
            if 'LoadState' in args:return 'loaded'
            if 'is-active' in args:return 'active'
            return ''
        patches=[patch.object(o,'command',side_effect=command),patch.object(o,'runtime_media_root',return_value=self.media),patch.object(o.os,'geteuid',return_value=0),patch.object(o,'private_directory',side_effect=lambda:o.require(o.BACKUP.is_dir() and not o.BACKUP.is_symlink(),'backup absent')),patch.dict(os.environ,{'SSM_ReleaseSha':'a'*40,'SSM_PatchSha256':o.digest(self.payload)['sha256'],'SSM_ReleaseRunId':'123'})]
        for p in patches:p.start();self.addCleanup(p.stop)
        self.silence=patch('sys.stdout',new=io.StringIO());self.silence.start();self.addCleanup(self.silence.stop)
    def test_backup_reuse_is_idempotent(self):
        o=self.ops;o.backup_or_verify();before=(o.BACKUP/'manifest.json').read_bytes();o.backup_or_verify();self.assertEqual(before,(o.BACKUP/'manifest.json').read_bytes())
    def test_wrong_live_baseline_prevents_backup(self):
        o=self.ops;o.JAR.write_bytes(b'wrong')
        with self.assertRaisesRegex(RuntimeError,'baseline'):o.backup_or_verify()
        self.assertFalse(o.BACKUP.exists())
    def test_corrupt_backup_prevents_deploy(self):
        o=self.ops;o.backup_or_verify();(o.BACKUP/'app.jar').write_bytes(b'bad')
        with self.assertRaisesRegex(RuntimeError,'backup JAR'):o.deploy()
        self.assertFalse(any('restart' in c for c in self.calls))
    def test_verify_does_not_restart_service(self):
        self.ops.backup_or_verify();self.ops.verify_permission();self.assertFalse(any('restart' in c for c in self.calls))
    def test_deploy_and_restore_preserve_media_and_environment(self):
        o=self.ops;before=o.media_manifest(self.media);env=[p.read_bytes() for p in o.ENV_FILES];o.backup_or_verify();o.deploy()
        self.assertNotEqual(o.digest(o.JAR)['sha256'],o.EXPECTED);o.rollback()
        self.assertEqual(o.digest(o.JAR)['sha256'],o.EXPECTED);self.assertEqual(before,o.media_manifest(self.media));self.assertEqual(env,[p.read_bytes() for p in o.ENV_FILES])
    def test_stale_run_cannot_roll_back_newer_release(self):
        o=self.ops;o.backup_or_verify();o.deploy();deployed=o.JAR.read_bytes()
        with patch.dict(os.environ,{'SSM_ReleaseRunId':'999'}),self.assertRaisesRegex(RuntimeError,'stale'):o.rollback()
        self.assertEqual(deployed,o.JAR.read_bytes())
    def test_unknown_current_jar_not_overwritten(self):
        o=self.ops;o.backup_or_verify();o.deploy();o.JAR.write_bytes(b'another-release')
        with self.assertRaisesRegex(RuntimeError,'another release'):o.rollback()
        self.assertEqual(o.JAR.read_bytes(),b'another-release')
    def test_media_drift_stops_deployment(self):
        o=self.ops;o.backup_or_verify();(self.media/'seven'/'poster.jpg').write_bytes(b'changed')
        with self.assertRaisesRegex(RuntimeError,'media drift'):o.deploy()
        self.assertEqual(o.digest(o.JAR)['sha256'],o.EXPECTED)
    def test_failed_health_can_restore_verified_jar(self):
        o=self.ops;o.backup_or_verify()
        with patch.object(o,'health',side_effect=RuntimeError('unhealthy')),self.assertRaisesRegex(RuntimeError,'unhealthy'):o.deploy()
        o.rollback();self.assertEqual(o.digest(o.JAR)['sha256'],o.EXPECTED)
    def test_environment_drift_stops_deploy_without_edit(self):
        o=self.ops;o.backup_or_verify();o.ENV_FILES[0].write_bytes(b'changed')
        with self.assertRaisesRegex(RuntimeError,'environment'):o.deploy()
        self.assertEqual(o.ENV_FILES[0].read_bytes(),b'changed')


class ContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        generate.generate();cls.stack=json.loads((generate.ROOT/'infra/pr914/stack.json').read_text())
    def test_existing_mediaaudit_not_managed_by_stack(self):
        names=[r.get('Properties',{}).get('Name') for r in self.stack['Resources'].values()]
        self.assertNotIn('F2R-914-MediaAudit',names)
    def test_runtime_roles_separate_deploy_and_restore(self):
        resources=self.stack['Resources']
        for role,expected in [('Deploy','AutoDeploy'),('Restore','AutoRestore'),('Preflight','AutoBackup')]:
            statements=resources[role+'Role']['Properties']['Policies'][0]['PolicyDocument']['Statement']
            docs=json.dumps(statements[0]);self.assertIn(expected,docs)
            if role!='Restore':self.assertNotIn('AutoRestore',docs)
            if role!='Deploy':self.assertNotIn('AutoDeploy',docs)
            actions=[a for s in statements for a in ([s['Action']] if isinstance(s['Action'],str) else s['Action'])]
            self.assertFalse(any('Delete' in a or a.endswith(':*') or a.startswith(('rds:','iam:')) for a in actions))
    def test_exact_instance_and_tags(self):
        self.assertEqual(self.stack['Parameters']['ProductionInstanceId']['AllowedValues'],['i-0414de33d2803c723'])
        for role in ['Preflight','Deploy','Restore']:
            policy=self.stack['Resources'][role+'Role']['Properties']['Policies'][0]['PolicyDocument']['Statement'][1]
            self.assertIn('ProductionInstanceId',str(policy['Resource']));self.assertEqual(policy['Condition']['StringEquals']['ssm:resourceTag/Name'],'f2r-app')
    def test_mediaaudit_file_identical_to_prepared_version(self):
        p=generate.ROOT/'infra/pr914/F2R-914-MediaAudit.json'
        self.assertEqual(hashlib.sha256(p.read_bytes()).hexdigest(),'dcf762eb8ebea2c7332fd291ae7d9bc42241231b1bfb256ab87aaf9a1993ea77')
    def test_all_embedded_scripts_compile_and_have_no_missing_functions(self):
        for name in ['Backup','Deploy','Restore']:
            doc=self.stack['Resources'][name+'Document']['Properties']['Content']
            code=doc['mainSteps'][0]['inputs']['runCommand'][1].split("\n",1)[1].rsplit('\nF2R_914_PY',1)[0]
            compile(code,'ssm','exec')
            # Load declarations, without executing root commands; verify entry exists.
            scope={};exec(code.split('\n\ntry:\n')[0],scope)
            if name=='Restore':self.assertIn('verify_permission',scope);self.assertIn('rollback',scope)
    def test_ci_missing_and_failing_are_blocked(self):
        pr={'head':{'sha':github_release.APPROVED['approved_head'],'repo':{'full_name':github_release.REPO}},'base':{'ref':'main'}}
        with patch.object(github_release,'repo',return_value={'check_runs':[]}),self.assertRaisesRegex(RuntimeError,'CI'):github_release.verify_pr(pr)
    def test_changed_pr_head_is_blocked(self):
        with self.assertRaisesRegex(RuntimeError,'changed'):github_release.verify_pr({'head':{'sha':'b'*40}})
    def test_template_fits_direct_cloudformation_upload(self):
        self.assertLess((generate.ROOT/'infra/pr914/stack.json').stat().st_size,51200)

if __name__=='__main__':unittest.main()
