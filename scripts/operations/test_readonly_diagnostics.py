"""읽기 전용 운영 진단(F2R-ReadOnlyStatus SSM 문서·역할·workflow) 보안 시험.

운영 접근 없이 실행한다. SSM 문서의 셸을 가짜 /etc/f2r/env·가짜 mysql/systemctl/curl과 함께
로컬에서 돌려 비밀값이 출력되지 않는지 확인하고, IAM 정책·신뢰·workflow를 정적으로 검사한다.
실행: python -m unittest scripts/operations/test_readonly_diagnostics.py -v
"""
import json
import os
import re
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
MIG = ROOT / "docs/operations/aws-migration"
DOC = MIG / "ssm-document-F2R-ReadOnlyStatus.json"
WORKFLOWS = [ROOT / ".github/workflows/prod-readonly-status.yml", ROOT / ".github/workflows/prod-diagnostic.yml"]

PASSWORD = "Sup3r-Secret-DbPw!"
API_KEY = "sk-ant-api03-FAKEFAKEFAKE"
JWT = "jwt-signing-secret-value-123"
DB_USER = "f2r_admin"
RDS_HOST = "f2r-db-202610181331.abcdefghijkl.ap-northeast-2.rds.amazonaws.com"
SECRETS = [PASSWORD, API_KEY, JWT, RDS_HOST, "abcdefghijkl"]

FAKE_MYSQL = """#!/usr/bin/env bash
# 인자와 받은 환경변수 이름을 기록하고(값은 MYSQL_PWD 일치 여부만) 가짜 결과를 낸다.
{ printf '%s\\n' "ARGS:$*"; env | cut -d= -f1 | sed 's/^/ENV:/'; [ "$MYSQL_PWD" = "$EXPECT_PWD" ] && echo PWD_OK; } >> "$FAKE_LOG"
if [ -n "$FAKE_MYSQL_FAIL" ]; then
  echo "ERROR 1045 (28000): Access denied for user '$5'@'10.0.1.23' (using password: YES) host $3" >&2
  exit 1
fi
case "$*" in
  *VERSION*) printf 'mysql_version\\n8.4.8\\n' ;;
  *installed_rank*) printf 'version\\tchecksum\\tsuccess\\tinstalled_on\\n1093\\t123\\t1\\t2026-10-20\\n' ;;
  *flyway_schema_history*) printf 'applied\\tmax_version\\tfailed\\n180\\t1093\\t0\\n' ;;
  *) printf 'studio_or_mcp_tables\\n0\\n' ;;
esac
"""


def run_commands():
    return json.loads(DOC.read_text())["mainSteps"][0]["inputs"]["runCommand"]


class HostScriptLeakTest(unittest.TestCase):
    """SSM 문서의 셸을 가짜 호스트에서 실행한다."""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.bin = self.tmp / "bin"
        self.bin.mkdir()
        self.home = self.tmp / "home"
        self.home.mkdir()
        self.env_file = self.tmp / "env"
        self.log = self.tmp / "mysql.log"
        self._stub("mysql", FAKE_MYSQL)
        self._stub("systemctl", "#!/bin/sh\necho ActiveState=active\necho SubState=running\n")
        self._stub("curl", "#!/bin/sh\necho actuator_health_http=200\n")
        self._stub("ss", "#!/bin/sh\necho 'State Recv-Q Send-Q Local'\necho 'LISTEN 0 100 *:8080 *:*'\n")
        self._stub("journalctl", "#!/bin/sh\nexit 0\n")
        (self.home / "app.jar").write_bytes(b"jar")

    def _stub(self, name, body):
        p = self.bin / name
        p.write_text(body)
        p.chmod(p.stat().st_mode | stat.S_IEXEC)

    def write_env(self, extra=""):
        self.env_file.write_text(
            f"DB_HOST={RDS_HOST}\nDB_PORT=3306\nDB_USER={DB_USER}\nDB_PASSWORD={PASSWORD}\n"
            f"DB_NAME=f2r_db\nANTHROPIC_API_KEY={API_KEY}\nJWT_SECRET=\"{JWT}\"\nAPP_MCP_ENABLED=false\n{extra}"
        )

    def run_doc(self, section="all", **env):
        script = "\n".join(run_commands())
        script = script.replace("{{ Section }}", section)
        script = script.replace("/etc/f2r/env", str(self.env_file)).replace("/home/ec2-user", str(self.home))
        e = {"PATH": f"{self.bin}:/usr/bin:/bin", "FAKE_LOG": str(self.log), "EXPECT_PWD": PASSWORD,
             "HOME": str(self.tmp)}
        e.update(env)
        # Amazon Linux의 runShellScript는 sh(=bash)로 실행되므로 bash --posix로 재현한다.
        r = subprocess.run(["bash", "--posix", "-c", script], cwd=self.tmp, env=e,
                           capture_output=True, text=True, timeout=60)
        return r

    def assert_no_secret(self, text):
        for s in SECRETS:
            self.assertNotIn(s, text)

    def test_all_sections_print_no_secret_and_identify_db(self):
        # given: 실제와 같은 형식의 env(DB 비밀번호·API 키·JWT 포함)
        self.write_env()
        # when
        r = self.run_doc("all")
        # then: DB 식별자만 보이고 비밀값·접속 문자열·DB 사용자는 출력에 없다
        out = r.stdout + r.stderr
        self.assertEqual(r.returncode, 0, out)
        self.assertIn("db_instance_identifier=f2r-db-202610181331", r.stdout)
        self.assertIn("8.4.8", r.stdout)
        self.assertIn("APP_MCP_ENABLED=present", r.stdout)
        self.assert_no_secret(out)
        self.assertNotIn(DB_USER, out)

    def test_only_password_reaches_mysql_and_other_secrets_are_not_exported(self):
        # given
        self.write_env()
        # when
        self.run_doc("flyway-history")
        # then: mysql은 MYSQL_PWD로 비밀번호를 받지만 다른 비밀값 변수는 받지 않는다(env 전체 source 금지)
        log = self.log.read_text()
        self.assertIn("PWD_OK", log)
        for name in ("ANTHROPIC_API_KEY", "JWT_SECRET", "DB_PASSWORD", "DBW"):
            self.assertNotIn(f"ENV:{name}\n", log)
        self.assertNotIn(PASSWORD, log.replace("PWD_OK", ""))
        self.assertIn("--no-defaults", log)

    def test_mysql_error_with_user_and_host_is_suppressed(self):
        # given: 접속 오류 메시지에 사용자·호스트가 담기는 상황
        self.write_env()
        # when
        r = self.run_doc("flyway-history", FAKE_MYSQL_FAIL="1")
        # then
        out = r.stdout + r.stderr
        self.assertIn("query_failed (detail suppressed)", r.stdout)
        self.assertNotIn("Access denied", out)
        self.assertNotIn(DB_USER, out)
        self.assert_no_secret(out)

    def test_env_file_content_is_never_executed(self):
        # given: env 파일에 명령 치환이 섞인 줄과 비정상 DB_HOST
        marker = self.tmp / "PWNED"
        self.write_env(extra=f"EVIL=$(touch {marker})\n")
        self.env_file.write_text(self.env_file.read_text().replace(f"DB_HOST={RDS_HOST}", f"DB_HOST=x;touch {marker}"))
        # when
        r = self.run_doc("flyway-history")
        # then: 파일 내용이 실행되지 않고, 형식이 맞지 않으면 조회하지 않는다
        self.assertFalse(marker.exists())
        self.assertIn("unexpected format", r.stdout)
        self.assertFalse(self.log.exists())

    def test_deploy_marker_prints_only_allowed_keys(self):
        # given
        self.write_env()
        (self.home / "app.jar.meta").write_text(f"git_sha=2bed3918\nDB_PASSWORD={PASSWORD}\njar_sha256=abc123\n")
        # when
        r = self.run_doc("jar-info")
        # then
        self.assertIn("git_sha=2bed3918", r.stdout)
        self.assertIn("jar_sha256=abc123", r.stdout)
        self.assertNotIn(PASSWORD, r.stdout)

    def test_predeploy_check_prints_names_not_values(self):
        # given
        self.write_env()
        # when
        r = self.run_doc("predeploy-check")
        # then
        self.assertIn("APP_MCP_ENABLED=present", r.stdout)
        self.assertIn("ANTHROPIC_ENABLED=absent", r.stdout)
        self.assertNotIn("=false", r.stdout)


class StaticPolicyTest(unittest.TestCase):
    """문서·역할·workflow 정의를 정적으로 검사한다."""

    def test_document_has_only_the_allowlisted_parameter(self):
        # given
        d = json.loads(DOC.read_text())
        script = "\n".join(run_commands())
        # when
        placeholders = set(re.findall(r"\{\{\s*(\w+)\s*\}\}", script))
        # then: 유일한 입력은 allowedValues로 제한된 Section이다(임의 명령 불가)
        self.assertEqual(placeholders, {"Section"})
        self.assertEqual(list(d["parameters"]), ["Section"])
        allowed = d["parameters"]["Section"]["allowedValues"]
        self.assertTrue(all(re.fullmatch(r"[a-z-]+", v) for v in allowed))
        self.assertEqual(len(d["mainSteps"]), 1)
        self.assertEqual(d["mainSteps"][0]["action"], "aws:runShellScript")

    def test_document_has_no_write_or_secret_dump(self):
        # given
        script = "\n".join(run_commands())
        # then: 쓰기·재시작·env 전체 출력·source 없음
        for bad in (r"\bINSERT\b", r"\bUPDATE\b", r"\bDELETE\b", r"\bDROP\b", r"\bALTER\b", r"systemctl (restart|stop|start)",
                    r"set -a", r"^\s*\. ", r"\bsource\b", r"cat /etc/f2r/env", r"^\s*(env|printenv)\b", r"rm -"):
            self.assertIsNone(re.search(bad, script, re.M), bad)
        self.assertEqual(script.count("SET SESSION TRANSACTION READ ONLY"), 1)  # q()가 모든 조회에 붙인다

    def test_readonly_policy_is_least_privilege(self):
        # given
        p = json.loads((MIG / "policy-f2r-readonly-status.json").read_text())
        actions = []
        for s in p["Statement"]:
            self.assertEqual(s["Effect"], "Allow")
            a = s["Action"] if isinstance(s["Action"], list) else [s["Action"]]
            actions += a
            if "ssm:SendCommand" in a:
                res = s["Resource"]
                # then: SendCommand는 고정 문서 또는 태그 조건이 걸린 인스턴스에만 허용한다
                if res.endswith("document/F2R-ReadOnlyStatus"):
                    continue
                self.assertTrue(res.endswith(":instance/*"), res)
                self.assertEqual(s["Condition"]["StringEquals"]["ssm:resourceTag/Name"], "f2r-app")
        self.assertNotIn("AWS-RunShellScript", json.dumps(p))
        for a in actions:
            self.assertNotIn("*", a)
            self.assertRegex(a, r"^(ec2|rds|elasticloadbalancing|ssm):(Describe|Get|List)\w+$|^ssm:SendCommand$")

    def test_trust_policies_are_pinned(self):
        # given
        ro = json.loads((MIG / "trust-github-actions-f2r-readonly.json").read_text())["Statement"][0]["Condition"]
        sn = json.loads((MIG / "trust-github-actions-f2r-snapshot.json").read_text())["Statement"][0]["Condition"]
        # then: main 브랜치의 지정 workflow만, 스냅샷은 승인 환경만
        self.assertTrue(ro["StringEquals"]["token.actions.githubusercontent.com:sub"].endswith(":ref:refs/heads/main"))
        self.assertEqual(sorted(ro["ForAnyValue:StringEquals"]["token.actions.githubusercontent.com:job_workflow_ref"]), [
            "woori73/feel2real-buildit/.github/workflows/prod-diagnostic.yml@refs/heads/main",
            "woori73/feel2real-buildit/.github/workflows/prod-readonly-status.yml@refs/heads/main"])
        self.assertTrue(sn["StringEquals"]["token.actions.githubusercontent.com:sub"].endswith(":environment:f2r-infra-build"))
        self.assertEqual(sn["StringEquals"]["token.actions.githubusercontent.com:job_workflow_ref"],
                         "woori73/feel2real-buildit/.github/workflows/migration-infra-build.yml@refs/heads/main")

    def test_snapshot_policy_creates_only_named_snapshots(self):
        # given
        p = json.loads((MIG / "policy-f2r-predeploy-snapshot.json").read_text())
        stmts = {s["Sid"]: s for s in p["Statement"]}
        acts = sorted(a for s in p["Statement"] for a in (s["Action"] if isinstance(s["Action"], list) else [s["Action"]]))
        # then: 운영 DB 하나에서 f2r-predeploy-* 생성·태그만(삭제·복원·수정 없음)
        self.assertEqual(acts, ["rds:AddTagsToResource", "rds:CreateDBSnapshot", "rds:DescribeDBInstances", "rds:DescribeDBSnapshots"])
        self.assertEqual(sorted(stmts["CreatePreDeploySnapshotOfProductionDbOnly"]["Resource"]), [
            "arn:aws:rds:ap-northeast-2:425805163960:db:f2r-db-202610181331",
            "arn:aws:rds:ap-northeast-2:425805163960:snapshot:f2r-predeploy-*"])
        self.assertEqual(stmts["TagOnlyPreDeploySnapshotsAtCreation"]["Resource"],
                         "arn:aws:rds:ap-northeast-2:425805163960:snapshot:f2r-predeploy-*")

    def test_workflows_use_readonly_role_fixed_document_and_no_inline_inputs(self):
        allowed = set(json.loads(DOC.read_text())["parameters"]["Section"]["allowedValues"])
        for wf in WORKFLOWS:
            # given
            text = wf.read_text()
            # then
            self.assertNotIn("AWS-RunShellScript", text, wf.name)
            self.assertNotIn("github-actions-f2r-deploy", text, wf.name)
            self.assertNotIn("StandardErrorContent", text, wf.name)
            self.assertIn("--document-name F2R-ReadOnlyStatus", text, wf.name)
            self.assertIn("role/github-actions-f2r-readonly", text, wf.name)
            # 사용자 입력은 run 본문에 직접 넣지 않고 env로만 전달한다(식 주입 방지)
            for job in yaml.safe_load(text)["jobs"].values():
                for step in job.get("steps", []):
                    self.assertNotRegex(step.get("run", ""), r"\$\{\{\s*(inputs|github\.event)\.", wf.name)
        options = re.findall(r"^\s+- ([a-z-]+)$", WORKFLOWS[0].read_text(), re.M)
        self.assertEqual(set(options), allowed)

    def test_setup_step_applies_only_repo_definitions_and_not_the_deploy_role(self):
        # given: migration-infra-build의 readonly-setup 단계 본문
        text = (ROOT / ".github/workflows/migration-infra-build.yml").read_text()
        body = text[text.index("step_readonly_setup() {"):text.index("step_all_infra() {")]
        # then: 저장소 JSON만 적용하고, 배포 역할·관리형 정책·임의 셸은 쓰지 않는다
        self.assertIn("file://$D/ssm-document-F2R-ReadOnlyStatus.json", body)
        self.assertIn("file://$D/trust-github-actions-f2r-readonly.json", body)
        self.assertIn("file://$D/policy-f2r-readonly-status.json", body)
        for bad in ("github-actions-f2r-deploy", "attach-role-policy", "AWS-RunShellScript", "send-command", "--with-decryption"):
            self.assertNotIn(bad, body)
        self.assertIn("readonly-setup) step_readonly_setup ;;", text)

    def test_account_mask_keeps_rds_identifiers_readable(self):
        # given: Describe 출력의 계정 번호 마스킹 함수
        text = WORKFLOWS[0].read_text()
        line = re.search(r"mask\(\) \{ sed -E '([^']+)'; \}", text).group(1)
        sample = "arn:aws:rds:ap-northeast-2:425805163960:db:f2r-db-202610181331"
        # when
        out = subprocess.run(["sed", "-E", line], input=sample, capture_output=True, text=True).stdout
        # then: 계정 번호만 가리고 운영 DB 식별자(12자리 시각 포함)는 그대로 둔다
        self.assertNotIn("425805163960", out)
        self.assertIn("f2r-db-202610181331", out)

    def test_predeploy_snapshot_job_uses_snapshot_role_and_is_create_only(self):
        # given: 배포 직전 스냅샷 잡과 빌드 잡 조건
        wf = yaml.safe_load((ROOT / ".github/workflows/migration-infra-build.yml").read_text())
        job = wf["jobs"]["predeploy-snapshot"]
        run = job["steps"][1]["run"]
        # then: 소유자 승인 환경·스냅샷 전용 역할, 운영 DB 고정, 실행 커밋 기준 이름·확인 문자열, 생성·대기·조회만
        self.assertEqual(job["environment"], "f2r-infra-build")
        self.assertEqual(job["steps"][0]["with"]["role-to-assume"], "arn:aws:iam::425805163960:role/github-actions-f2r-snapshot")
        self.assertIn("predeploy-snapshot", wf["jobs"]["build"]["if"])
        self.assertIn("DB=f2r-db-202610181331", run)
        self.assertIn('[ "${CONFIRM:-}" = "SNAPSHOT-$SHA8" ]', run)
        self.assertIn('SNAP="f2r-predeploy-$(date -u +%Y%m%d%H%M)-$SHA8"', run)
        # AWS CLI JSON 출력은 빈 객체 {}를 아무것도 출력하지 않으므로 키 개수로 대기 변경 유무를 판정한다.
        self.assertIn("length(keys(DBInstances[0].PendingModifiedValues))", run)
        self.assertIn('[ "$PENDING" = 0 ]', run)
        self.assertNotIn("PendingModifiedValues' --output json", run)
        self.assertEqual(set(re.findall(r"aws rds ([a-z-]+)", run)),
                         {"describe-db-instances", "create-db-snapshot", "wait", "describe-db-snapshots"})

    def _run_pending_gate(self, aws_body):
        """predeploy-snapshot의 대기 변경 판정 두 줄을 가짜 aws로 실행한다(실제 AWS 호출 없음)."""
        wf = yaml.safe_load((ROOT / ".github/workflows/migration-infra-build.yml").read_text())
        run = wf["jobs"]["predeploy-snapshot"]["steps"][1]["run"]
        gate = "\n".join(line for line in run.splitlines() if "PENDING" in line)
        with tempfile.TemporaryDirectory() as temp:
            fake = Path(temp) / "aws"
            fake.write_text("#!/usr/bin/env bash\n" + aws_body + "\n")
            fake.chmod(0o755)
            env = {**os.environ, "PATH": temp + os.pathsep + os.environ["PATH"]}
            script = "set -euo pipefail\nDB=f2r-db-202610181331\n" + gate + "\necho GATE_PASSED\n"
            return subprocess.run(["bash", "-c", script], capture_output=True, text=True, env=env)

    def test_pending_gate_passes_when_no_pending_changes(self):
        # given: PendingModifiedValues가 {}라서 키 개수 쿼리가 0을 출력하는 AWS
        # (참고: 같은 상황에서 --output json은 아무것도 출력하지 않아 이전 판정이 항상 실패했다)
        aws = 'case "$*" in *"length(keys(DBInstances[0].PendingModifiedValues))"*"--output text"*) echo 0 ;; *) exit 9 ;; esac'
        # when
        result = self._run_pending_gate(aws)
        # then
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("GATE_PASSED", result.stdout)

    def test_pending_gate_stops_when_changes_are_pending(self):
        # given: 대기 중 변경 1건
        result = self._run_pending_gate('echo 1')
        # then: 스냅샷 생성 전에 중단
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("GATE_PASSED", result.stdout)
        self.assertIn("대기 중 변경", result.stdout)

    def test_pending_gate_fails_closed_when_aws_read_fails(self):
        # given: 조회 실패(필드 없음·권한 오류 등)
        result = self._run_pending_gate('echo "error" >&2; exit 255')
        # then
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("GATE_PASSED", result.stdout)

    def test_rds_protect_uses_same_pending_gate(self):
        # given: rds-protect 단계 본문
        text = (ROOT / ".github/workflows/migration-infra-build.yml").read_text()
        body = text[text.index("step_rds_protect() {"):text.index("step_a1_precheck() {")]
        # then: 빈 객체 JSON 비교를 쓰지 않고 키 개수로 판정한다
        self.assertEqual(2, body.count("length(keys(DBInstances[0].PendingModifiedValues))"))
        self.assertNotIn('"{}"', body)

    def test_snapshot_role_setup_uses_repo_definitions_and_verifies_denials(self):
        # given
        text = (ROOT / ".github/workflows/migration-infra-build.yml").read_text()
        body = text[text.index("step_snapshot_role_setup() {"):text.index("step_a1_precheck() {")]
        # then: 저장소 JSON만 적용, 배포·빌더 역할은 조회만, 삭제·복원·수정 거부를 시뮬레이션으로 확인
        self.assertIn("file://$D/trust-github-actions-f2r-snapshot.json", body)
        self.assertIn("file://$D/policy-f2r-predeploy-snapshot.json", body)
        for denied in ("rds:DeleteDBSnapshot", "rds:RestoreDBInstanceFromDBSnapshot", "rds:ModifyDBInstance", "rds:DeleteDBInstance"):
            self.assertIn(f"expect {denied}", body)
        self.assertNotIn("attach-role-policy", body)
        self.assertNotRegex(body, r"(update-assume-role-policy|put-role-policy) --role-name github-actions-f2r-deploy")
        self.assertIn("snapshot-role-setup) step_snapshot_role_setup ;;", text)
    def test_studio_import_dry_run_workflow_never_writes_or_leaks(self):
        # given: 운영 제작실 dry-run workflow
        text = (ROOT / ".github/workflows/studio-production-import.yml").read_text()
        wf = yaml.safe_load(text)
        runs = "\n".join(s.get("run", "") for s in wf["jobs"]["dry-run"]["steps"])
        # then: 적재(--apply)·업로드·임의 POST가 없고, 로그인만 POST이며 비밀값은 env로만 받는다
        # 실제 적재(--apply)는 승인 입력·확인 문자열이 있는 적재 단계에만 있다
        steps = wf["jobs"]["dry-run"]["steps"]
        apply_steps = [s for s in steps if "--apply" in s.get("run", "")]
        self.assertEqual(len(apply_steps), 1)
        self.assertEqual(apply_steps[0]["if"], "${{ inputs.apply }}")
        self.assertIn('[ "$APPLY_CONFIRM" = "IMPORT-536-${DEPLOYED_SHA:0:8}" ]', apply_steps[0]["run"])
        self.assertIn('--confirm-snapshot "$SNAPSHOT"', apply_steps[0]["run"])
        self.assertIs(wf[True]["workflow_dispatch"]["inputs"]["apply"]["default"], False)
        self.assertNotIn("set -x", runs)
        # POST는 로그인 1건 + 승인된 최초 개설 1건(확인 문자열·미개설일 때만)
        self.assertEqual(runs.count("--data-binary"), 2)
        init = next(s for s in wf["jobs"]["dry-run"]["steps"] if s.get("name", "").startswith("Initialize studio project"))
        self.assertEqual(init["if"], "${{ inputs.initialize_project }}")
        self.assertIn('[ "$CONFIRM" = "CREATE-ai-agent-part1" ]', init["run"])
        self.assertIn('already initialized — no POST sent', init["run"])
        self.assertIn('"$BASE_URL/api/studio/project")', init["run"])
        self.assertIs(wf[True]["workflow_dispatch"]["inputs"]["initialize_project"]["default"], False)
        self.assertIn("/api/auth/login", runs)
        self.assertNotIn("-X POST", runs)
        self.assertIn("::add-mask::$TOKEN", runs)
        self.assertIn('chmod 600 "$RUNNER_TEMP/f2r-auth.json"', runs)
        self.assertNotRegex(runs, r"\$\{\{\s*secrets\.")
        self.assertEqual(wf["permissions"], {"contents": "read"})


if __name__ == "__main__":
    unittest.main()
