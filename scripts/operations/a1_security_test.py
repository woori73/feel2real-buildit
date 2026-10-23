import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import signal
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import patch


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


host = load("host", "a1-precheck.py")
cloud = load("cloud", "a1-aws-precheck.py")
SECRET = "SECRET_CANARY_not_for_logs"
USER = "private_database_user"
DB = "private_database_name"


class SecurityTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.env = Path(self.temp.name) / "env"
        self.env.write_text(f'DB_HOST="private.endpoint"\nDB_USER="{USER}"\nDB_PASSWORD="{SECRET}"\nDB_NAME="{DB}"\n')

    def assert_safe(self, output):
        for value in (SECRET, USER, DB, "private.endpoint", "arn:aws:", "sg-private", "10.9.8.7"):
            self.assertNotIn(value, output)

    def test_database_success_argv_permissions_and_cleanup(self):
        configs = []
        def fake(args, **options):
            self.assert_safe(json.dumps(args))
            config = Path(args[1].split("=", 1)[1])
            configs.append(config)
            self.assertEqual(0o600, stat.S_IMODE(config.stat().st_mode))
            self.assertEqual(0o700, stat.S_IMODE(config.parent.stat().st_mode))
            self.assertIn("password=\"" + SECRET, config.read_text())
            self.assertIn("database=\"" + DB, config.read_text())
            self.assertNotIn("MYSQL_PWD", options["env"])
            self.assertIn("START TRANSACTION READ ONLY", options["input"])
            return "Threads_connected\t18\nengine_version\t8.4.7\nflyway_version\t1093\npassword\t" + SECRET
        with patch.dict(os.environ, {"MYSQL_PWD": SECRET}):
            output = host.database(self.env, fake)
        self.assertEqual(18, output["Threads_connected"])
        self.assertEqual("1093", output["flyway_version"])
        self.assert_safe(json.dumps(output))
        self.assertFalse(configs[0].parent.exists())

    def test_cleanup_on_failure_timeout_and_signal(self):
        for failure in (None, subprocess.TimeoutExpired("mysql", 40), SystemExit(143)):
            configs = []
            def fake(args, **_options):
                configs.append(Path(args[1].split("=", 1)[1]))
                if failure:
                    raise failure
                return None
            try:
                host.database(self.env, fake)
            except (subprocess.TimeoutExpired, SystemExit):
                pass
            self.assertFalse(configs[0].parent.exists())

    def test_real_termination_unwinds_secret_file(self):
        # Actual OS signal, not only a mocked exception.
        pidfile = Path(self.temp.name) / "path"
        code = """import importlib.util,os,signal,sys
from pathlib import Path
s=importlib.util.spec_from_file_location('host',sys.argv[1]);m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
signal.signal(signal.SIGTERM,m.interrupted)
def runner(args,**kw):
 Path(sys.argv[3]).write_text(args[1].split('=',1)[1]);os.kill(os.getpid(),signal.SIGTERM)
m.database(sys.argv[2],runner)
"""
        proc = subprocess.run(["python3", "-c", code, host.__file__, str(self.env), str(pidfile)], capture_output=True, text=True)
        self.assertEqual(143, proc.returncode)
        self.assertFalse(Path(pidfile.read_text()).parent.exists())
        self.assert_safe(proc.stdout + proc.stderr)

    def test_raw_client_errors_never_escape(self):
        result = subprocess.CompletedProcess([], 1, SECRET, f"Access denied for {USER} database {DB} password {SECRET}")
        with patch.object(host.subprocess, "run", return_value=result):
            self.assertIsNone(host.run(["mysql"]))

    def test_mysql_config_escapes_and_rejects_line_injection(self):
        self.assertEqual('"a\\\\b\\\"c"', host.cnf_quote('a\\b"c'))
        with self.assertRaises(ValueError):
            host.cnf_quote("secret\n[mysql]\ninit-command=DELETE")
        self.env.write_text('DB_PASSWORD="unterminated')
        output = host.database(self.env)
        self.assertEqual({"status": "unavailable"}, output)

    def test_systemd_injection_and_process_args_exclusion(self):
        def fake(args):
            self.assertNotIn("cat", args)
            self.assertNotIn("--property=ExecStart", args)
            self.assertNotIn("--property=Environment", args)
            return f"MainPID=123\nActiveState=active\nMemoryPeak=500\nRestart={SECRET}\nExecStart={SECRET}\nEnvironment={SECRET}"
        with patch.object(host, "run", side_effect=fake):
            output, pid = host.systemd("f2r-app")
        self.assertEqual(123, pid)
        self.assertEqual(500, output["MemoryPeak"])
        self.assert_safe(json.dumps(output))
        source = Path(host.__file__).read_text()
        self.assertIn('"rss=,nlwp=,comm="', source)
        self.assertNotIn("systemctl cat", source)

    def test_db_and_ssm_allowlists_reject_nested_identifiers(self):
        raw = {"memory": {"MemAvailableMB": SECRET, "private": SECRET}, "database": {"max_connections": 60, "engine_version": SECRET, USER: DB}, "metrics": {"jvm.memory.used": [SECRET, 100]}, "services": {"f2r-app": {"state": SECRET, "MemoryPeak": 1024}}, "error": SECRET, "processes": [{"args": SECRET}], "bindings": {"8083": [SECRET]}, "oomEvents7d": 0}
        output = cloud.safe_host(json.dumps(raw))
        self.assertEqual(60, output["database"]["max_connections"])
        self.assertEqual([None, 100], output["metrics"]["jvm.memory.used"])
        self.assert_safe(json.dumps(output))

    def test_nonfinite_numbers_and_versions_fail_closed(self):
        for value in ("nan", "inf", "-1", SECRET):
            self.assertIsNone(host.number(value))
        self.assertIsNone(host.version(SECRET))

    def test_aws_errors_not_logged(self):
        with patch.object(cloud.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, SECRET, SECRET)):
            with self.assertRaisesRegex(RuntimeError, "AWS read unavailable"):
                cloud.aws("ec2", "describe-instances")

    def test_8083_ipv4_ipv6_port_ranges_and_groups(self):
        self.assertTrue(cloud.port_scope([{"IpProtocol": "tcp", "FromPort": 8083, "ToPort": 8083, "UserIdGroupPairs": [{"GroupId": "alb"}]}], ["alb"])["restrictedToAlb"])
        for rule in ({"IpProtocol": "-1", "Ipv6Ranges": [{"CidrIpv6": "::/0"}]}, {"IpProtocol": "tcp", "FromPort": 8000, "ToPort": 9000, "IpRanges": [{"CidrIp": "0.0.0.0/0"}]}, {"IpProtocol": "tcp", "FromPort": 8083, "ToPort": 8083, "UserIdGroupPairs": [{"GroupId": "other"}]}):
            self.assertFalse(cloud.port_scope([rule], ["alb"])["restrictedToAlb"])

    def test_ssm_payload_contains_source_only_and_private_cleanup(self):
        command = cloud.ssm_command()
        self.assert_safe(command)
        self.assertIn("umask 077", command)
        self.assertIn("mktemp -d", command)
        self.assertIn("trap", command)
        self.assertIn('rm -rf -- "$d"', command)
        self.assertNotIn("A1_LIB_B64", command)

    def test_full_aws_and_ssm_projection_keeps_numbers_discards_secrets(self):
        def fake(service, operation, **options):
            fixtures = {
                "get-caller-identity": {"Account": "425805163960", "Arn": SECRET},
                "describe-instances": {"Reservations": [{"Instances": [{"InstanceId": SECRET, "InstanceType": "t3.large", "PrivateIpAddress": "10.9.8.7", "SecurityGroups": [{"GroupId": "sg-private"}]}]}]},
                "describe-load-balancers": {"LoadBalancers": [{"LoadBalancerArn": SECRET, "SecurityGroups": ["sg-alb"]}]},
                "describe-security-groups": {"SecurityGroups": [{"GroupId": "sg-private", "IpPermissions": []}]},
                "describe-load-balancer-attributes": {"Attributes": [{"Key": "idle_timeout.timeout_seconds", "Value": "300"}, {"Key": "secret", "Value": SECRET}]},
                "describe-listeners": {"Listeners": [{"Port": 443, "Protocol": "HTTPS", "Arn": SECRET}]},
                "describe-target-groups": {"TargetGroups": []},
                "describe-db-instances": {"DBInstances": [{"DBInstanceIdentifier": SECRET, "DBInstanceClass": "db.t4g.micro", "Engine": "mysql", "EngineVersion": "8.4.7", "DBInstanceStatus": "available", "Endpoint": {"Address": "private.endpoint"}, "AllocatedStorage": 20}]},
                "get-metric-statistics": {"Datapoints": [{"Timestamp": "2026-10-22", "Maximum": 18, "Average": 1, "Minimum": 200}]},
                "simulate-principal-policy": {"EvaluationResults": []},
                "send-command": {"Command": {"CommandId": SECRET}},
                "get-command-invocation": {"Status": "Success", "StandardOutputContent": json.dumps({"memory": {"MemAvailableMB": 3000}, "database": {"max_connections": 60, "Threads_connected": 18, "Threads_running": 1, "Max_used_connections": 20, "Uptime": 10, "app_connections": 10, "failed_migrations": 0}, "metrics": {"hikaricp.connections.max": [10]}, "bindings": {"8083": []}, "evil": SECRET}), "StandardErrorContent": SECRET},
            }
            return fixtures[operation]
        stdout = io.StringIO()
        with patch.object(cloud, "aws", side_effect=fake), patch.object(cloud.time, "sleep"), contextlib.redirect_stdout(stdout):
            self.assertEqual(0, cloud.collect())
        output = stdout.getvalue()
        self.assert_safe(output)
        result = json.loads(output)
        self.assertEqual("PASS", result["gates"]["connections"])
        self.assertEqual(300, result["alb"]["idle_timeout.timeout_seconds"])
        self.assertEqual("db1", result["rds"][0]["alias"])

    def test_endpoint_match_identifies_app_database_without_printing_endpoint(self):
        # given: 앱 설정 DB_HOST와 같은 엔드포인트를 가진 RDS 1개, 다른 RDS 1개
        digest = host.endpoint_digest(self.env)
        dbs = [{"DBInstanceIdentifier": "f2r-db", "DBInstanceClass": "db.t3.micro", "Engine": "mysql", "EngineVersion": "8.4.8", "DBInstanceStatus": "available", "Endpoint": {"Address": "other.endpoint"}, "BackupRetentionPeriod": 7},
               {"DBInstanceIdentifier": "f2r-db-202610181331", "DBInstanceClass": "db.t3.micro", "Engine": "mysql", "EngineVersion": "8.4.8", "DBInstanceStatus": "available", "Endpoint": {"Address": "Private.Endpoint."}, "BackupRetentionPeriod": 2, "LatestRestorableTime": "2026-10-22T23:31:55+00:00", "PreferredBackupWindow": "18:44-19:14"}]
        host_out = {"memory": {"MemAvailableMB": 3000}, "database": {"max_connections": 60, "Threads_connected": 18, "Threads_running": 1, "Max_used_connections": 20, "Uptime": 10, "app_connections": 10, "failed_migrations": 0, "flyway_version": "1093"},
                    "metrics": {"hikaricp.connections.max": [10]}, "bindings": {"8083": []}, "dbEndpointSha256": digest,
                    "jar": {"current": {"present": True, "sizeBytes": 10, "mtimeEpoch": 1, "sha256": "a" * 64, "maxMigration": 1093, "markers": {"studioImporter": False, "studioMcpController": False}, "path": SECRET}}}
        def fake(service, operation, **options):
            return {"get-caller-identity": {"Account": "425805163960"},
                    "describe-instances": {"Reservations": [{"Instances": [{"InstanceId": "i-1", "InstanceType": "t3.large", "SecurityGroups": [{"GroupId": "sg-private"}]}]}]},
                    "describe-load-balancers": {"LoadBalancers": [{"LoadBalancerArn": SECRET, "SecurityGroups": ["sg-alb"]}]},
                    "describe-security-groups": {"SecurityGroups": [{"IpPermissions": []}]},
                    "describe-load-balancer-attributes": {"Attributes": []}, "describe-listeners": {"Listeners": []}, "describe-target-groups": {"TargetGroups": []},
                    "describe-db-instances": {"DBInstances": dbs}, "get-metric-statistics": {"Datapoints": []}, "simulate-principal-policy": {"EvaluationResults": []},
                    "send-command": {"Command": {"CommandId": "c"}}, "get-command-invocation": {"Status": "Success", "StandardOutputContent": json.dumps(host_out)}}[operation]
        stdout = io.StringIO()
        # when
        with patch.object(cloud, "aws", side_effect=fake), patch.object(cloud.time, "sleep"), contextlib.redirect_stdout(stdout):
            cloud.collect()
        # then: 식별자로 일치 대상을 보고하고 엔드포인트·해시는 출력하지 않는다
        output = stdout.getvalue()
        self.assert_safe(output)
        self.assertNotIn("other.endpoint", output)
        self.assertNotIn(digest, output)
        self.assertNotIn("dbEndpointSha256", output)
        result = json.loads(output)
        self.assertEqual("f2r-db-202610181331", result["appDatabase"])
        self.assertEqual([False, True], [e["appDatabase"] for e in result["rds"]])
        self.assertEqual("1093", result["host"]["database"]["flyway_version"])
        self.assertEqual(1093, result["host"]["jar"]["current"]["maxMigration"])
        self.assertEqual("18:44-19:14", result["rds"][1]["backupWindow"])

    def test_no_or_ambiguous_endpoint_match_is_reported(self):
        # given: 해시가 없는 호스트 출력 / when: 투영 / then: 해시 None
        self.assertIsNone(cloud.safe_host(json.dumps({"dbEndpointSha256": SECRET}))["dbEndpointSha256"])
        # given 잘못된 식별자 / when / then 출력하지 않음
        self.assertIsNone(cloud.identifier("prod-" + SECRET))
        self.assertEqual("f2r-db-202610181240", cloud.identifier("f2r-db-202610181240"))

    def test_jar_info_reports_build_markers_without_paths(self):
        # given: 마이그레이션 V1093·V1095와 스튜디오 적재 클래스가 든 jar
        import zipfile
        jar = Path(self.temp.name) / "app.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("BOOT-INF/classes/db/migration/V1093__a.sql", "")
            archive.writestr("BOOT-INF/classes/db/migration/V1095__studio_and_mcp.sql", "")
            archive.writestr("BOOT-INF/classes/f2r/x/domain/studio/StudioProductionImporter.class", "")
        # when
        info = host.jar_info(jar)
        projected = cloud.safe_jar({**info, "path": SECRET})
        # then
        self.assertEqual(1095, projected["maxMigration"])
        self.assertTrue(projected["markers"]["studioImporter"])
        self.assertFalse(projected["markers"]["studioMcpController"])
        self.assertEqual(64, len(projected["sha256"]))
        self.assertEqual({"present": False}, host.jar_info(Path(self.temp.name) / "missing.jar"))
        self.assert_safe(json.dumps(projected))


if __name__ == "__main__":
    unittest.main()
