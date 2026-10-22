#!/usr/bin/env python3
"""A1 read-only AWS adapter. Raw AWS/SSM responses are never logged."""
import base64
import datetime as dt
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import tarfile
import time

from importlib.util import module_from_spec, spec_from_file_location

spec = spec_from_file_location("a1_host", Path(__file__).with_name("a1-precheck.py"))
host = module_from_spec(spec)
spec.loader.exec_module(host)


def aws(service, operation, **options):
    args = ["aws", service, operation, "--output", "json", "--no-cli-pager"]
    for key, value in options.items():
        args.append("--" + key.replace("_", "-"))
        args.extend(value if isinstance(value, list) else [str(value)])
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=60)
        if result.returncode == 0:
            return json.loads(result.stdout)
    except (OSError, ValueError, subprocess.SubprocessError):
        pass
    raise RuntimeError("AWS read unavailable")


def kind(value):
    # Instance classes and versions only, never free-form tags, names, paths or error messages.
    return value if isinstance(value, str) and re.fullmatch(r"(?:db\.)?[a-z][0-9][a-z]*\.[a-z0-9]+", value) else None


def port_scope(rules, alb_groups):
    result = {"world": 0, "other_cidr": 0, "alb_group": 0, "other_group": 0, "prefix_list": 0}
    for rule in rules:
        if rule.get("IpProtocol") != "-1" and not (rule.get("IpProtocol") in ("tcp", "6") and rule.get("FromPort", 65536) <= 8083 <= rule.get("ToPort", -1)):
            continue
        for item in rule.get("IpRanges", []) + rule.get("Ipv6Ranges", []):
            cidr = item.get("CidrIp") or item.get("CidrIpv6")
            result["world" if cidr in ("0.0.0.0/0", "::/0") else "other_cidr"] += 1
        for item in rule.get("UserIdGroupPairs", []):
            result["alb_group" if item.get("GroupId") in alb_groups else "other_group"] += 1
        result["prefix_list"] += len(rule.get("PrefixListIds", []))
    result["restrictedToAlb"] = not any(result[k] for k in ("world", "other_cidr", "other_group", "prefix_list"))
    return result


HEX64 = re.compile(r"[0-9a-f]{64}")
# RDS identifiers of this project are already public in docs; other names never reach logs.
DB_IDENTIFIER = re.compile(r"f2r-db(?:-[0-9]{12})?")


def hex64(value):
    return value if isinstance(value, str) and HEX64.fullmatch(value) else None


def identifier(value):
    return value if isinstance(value, str) and DB_IDENTIFIER.fullmatch(value) else None


def endpoint_digest(address):
    return hashlib.sha256(address.strip().lower().rstrip(".").encode()).hexdigest() if isinstance(address, str) and address else None


def timestamp(value):
    text = value.isoformat() if isinstance(value, dt.datetime) else value
    return text if isinstance(text, str) and re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:.]+(?:Z|[+-][0-9]{2}:[0-9]{2})", text) else None


def safe_jar(value):
    if not isinstance(value, dict) or value.get("present") is not True:
        return {"present": value.get("present") if isinstance(value, dict) and value.get("present") in (False, None) else None}
    markers = value.get("markers") if isinstance(value.get("markers"), dict) else {}
    return {"present": True, "sizeBytes": host.number(value.get("sizeBytes")), "mtimeEpoch": host.number(value.get("mtimeEpoch")),
            "sha256": hex64(value.get("sha256")), "maxMigration": host.number(value.get("maxMigration")),
            "markers": {k: markers.get(k) is True for k in ("studioImporter", "studioMcpController")}}


def attributes(items, keys):
    return {k: host.number(next((item.get("Value") for item in items if item.get("Key") == k), None)) for k in keys}


def safe_host(raw):
    """Second allowlist before GitHub logs; stderr and arbitrary stdout are discarded."""
    data = json.loads(raw)
    output = {"schema": 1}
    for section, keys in {"memory": ("MemTotalMB", "MemAvailableMB", "SwapTotalMB", "SwapFreeMB"), "jvm": ("VmHWM", "VmRSS", "Threads")}.items():
        output[section] = {k: host.number(data.get(section, {}).get(k)) for k in keys}
    output["database"] = host.parse_db("\n".join(str(k) + "\t" + str(v) for k, v in data.get("database", {}).items()))
    output["services"] = {}
    for unit in ("f2r-app", "academycraft-app", "f2r-academycraft", "admissions-embedding", "wiki", "wiki-app"):
        values = data.get("services", {}).get(unit, {})
        output["services"][unit] = {k: host.number(values.get(k)) for k in ("MemoryCurrent", "MemoryPeak", "MemoryMax", "NRestarts")}
        output["services"][unit]["state"] = host.enum(values.get("state"), ("active", "inactive", "failed", "activating", "deactivating"))
    output["bindings"] = {str(p): [host.enum(v, ("loopback", "non-loopback")) for v in data.get("bindings", {}).get(str(p), [])[:16]] for p in (8000, 8080, 8081, 8082, 8083, 3306)}
    output["metrics"] = {k: [host.number(v) for v in (data.get("metrics", {}).get(k) or [])[:8]] for k in ("jvm.memory.used", "jvm.memory.max", "hikaricp.connections.max", "hikaricp.connections.active", "hikaricp.connections.pending", "process.uptime")}
    output["oomEvents7d"] = host.number(data.get("oomEvents7d"))
    output["dbEndpointSha256"] = hex64(data.get("dbEndpointSha256"))
    jars = data.get("jar") if isinstance(data.get("jar"), dict) else {}
    output["jar"] = {k: safe_jar(jars.get(k)) for k in ("current", "previous")}
    return output


def ssm_command():
    bundle = io.BytesIO()
    with tarfile.open(fileobj=bundle, mode="w:gz") as archive:
        for name in ("a1-precheck.sh", "a1-precheck.py"):
            archive.add(Path(__file__).with_name(name), arcname=name)
    encoded = base64.b64encode(bundle.getvalue()).decode()
    # Command history contains source code only, no configuration or credentials.
    return "set +x; umask 077; d=$(mktemp -d /tmp/f2r-a1-code.XXXXXXXX) || exit 1; trap 'rm -rf -- \"$d\"' EXIT; trap 'exit 143' TERM INT HUP; printf '%s' '" + encoded + "' | base64 -d | tar -xz -C \"$d\" && bash \"$d/a1-precheck.sh\" 2>/dev/null"


def collect():
    account = aws("sts", "get-caller-identity").get("Account")
    if account != "425805163960":
        raise RuntimeError("wrong account")
    instances = aws("ec2", "describe-instances", filters=["Name=tag:Name,Values=f2r-app", "Name=tag:Project,Values=f2r-migration", "Name=instance-state-name,Values=running"])
    instances = [i for r in instances.get("Reservations", []) for i in r.get("Instances", [])]
    if len(instances) != 1:
        raise RuntimeError("ambiguous instance")
    instance = instances[0]
    iid = instance["InstanceId"]
    alb = aws("elbv2", "describe-load-balancers", names=["f2r-alb"])["LoadBalancers"][0]
    arn = alb["LoadBalancerArn"]
    groups = aws("ec2", "describe-security-groups", group_ids=[g["GroupId"] for g in instance["SecurityGroups"]])["SecurityGroups"]
    output = {"ec2": {"class": kind(instance.get("InstanceType")), "hasInstanceProfile": bool(instance.get("IamInstanceProfile"))}, "port8083": port_scope([r for g in groups for r in g.get("IpPermissions", [])], alb.get("SecurityGroups", []))}
    output["alb"] = attributes(aws("elbv2", "describe-load-balancer-attributes", load_balancer_arn=arn)["Attributes"], ("idle_timeout.timeout_seconds",))
    output["listeners"] = [{"port": host.number(x.get("Port")), "protocol": host.enum(x.get("Protocol"), ("HTTP", "HTTPS", "TCP", "TLS"))} for x in aws("elbv2", "describe-listeners", load_balancer_arn=arn)["Listeners"]]
    output["targets"] = []
    for tg in aws("elbv2", "describe-target-groups", load_balancer_arn=arn)["TargetGroups"]:
        entry = {k: host.number(tg.get(k)) for k in ("Port", "HealthCheckIntervalSeconds", "HealthCheckTimeoutSeconds", "HealthyThresholdCount", "UnhealthyThresholdCount")}
        entry["usesActuatorHealth"] = tg.get("HealthCheckPath") == "/actuator/health"
        entry.update(attributes(aws("elbv2", "describe-target-group-attributes", target_group_arn=tg["TargetGroupArn"])["Attributes"], ("deregistration_delay.timeout_seconds", "slow_start.duration_seconds")))
        entry["health"] = [host.enum(x.get("TargetHealth", {}).get("State"), ("healthy", "unhealthy", "initial", "draining", "unused", "unavailable")) for x in aws("elbv2", "describe-target-health", target_group_arn=tg["TargetGroupArn"])["TargetHealthDescriptions"]]
        output["targets"].append(entry)
    end = dt.datetime.now(dt.timezone.utc)
    start = end - dt.timedelta(days=14)
    output["rds"] = []
    digests = []  # kept in memory only, never printed
    dbs = sorted(aws("rds", "describe-db-instances")["DBInstances"], key=lambda d: d["DBInstanceIdentifier"])
    for index, db in enumerate(dbs, 1):
        entry = {"alias": "db" + str(index), "class": kind(db.get("DBInstanceClass")), "engine": host.enum(db.get("Engine"), ("mysql", "mariadb", "postgres", "aurora-mysql", "aurora-postgresql")), "version": host.version(db.get("EngineVersion")), "status": host.enum(db.get("DBInstanceStatus"), ("available", "stopped", "starting", "stopping", "backing-up", "modifying"))}
        entry.update({k: host.number(db.get(k)) for k in ("AllocatedStorage", "Iops", "BackupRetentionPeriod")})
        entry.update({k: db.get(k) is True for k in ("MultiAZ", "PubliclyAccessible", "DeletionProtection", "StorageEncrypted")})
        entry["identifier"] = identifier(db.get("DBInstanceIdentifier"))
        entry["latestRestorableTime"] = timestamp(db.get("LatestRestorableTime"))
        entry["backupWindow"] = db.get("PreferredBackupWindow") if re.fullmatch(r"[0-9]{2}:[0-9]{2}-[0-9]{2}:[0-9]{2}", str(db.get("PreferredBackupWindow"))) else None
        entry["storageType"] = host.enum(db.get("StorageType"), ("gp2", "gp3", "io1", "io2", "standard"))
        digests.append(endpoint_digest((db.get("Endpoint") or {}).get("Address")))
        entry["metrics14d"] = {}
        for metric, stat in (("DatabaseConnections", "Maximum"), ("DatabaseConnections", "Average"), ("CPUUtilization", "Average"), ("ReadIOPS", "Average"), ("WriteIOPS", "Average"), ("FreeableMemory", "Minimum")):
            try:
                points = aws("cloudwatch", "get-metric-statistics", namespace="AWS/RDS", metric_name=metric, dimensions=["Name=DBInstanceIdentifier,Value=" + db["DBInstanceIdentifier"]], start_time=start.isoformat(), end_time=end.isoformat(), period="86400", statistics=[stat])["Datapoints"]
                entry["metrics14d"][metric + "/" + stat] = [host.number(p.get(stat)) for p in sorted(points, key=lambda p: p["Timestamp"])]
            except RuntimeError:
                entry["metrics14d"][metric + "/" + stat] = None
        # Cost cannot be inferred from low activity or list prices; no stop/delete decision here.
        entry["actualBilledCost"] = "not-measured"
        output["rds"].append(entry)
    actions = ["elasticloadbalancing:" + a for a in ("ModifyListener", "CreateTargetGroup", "RegisterTargets", "DeregisterTargets", "ModifyRule", "DescribeTargetHealth")] + ["ec2:AuthorizeSecurityGroupIngress"]
    try:
        results = aws("iam", "simulate-principal-policy", policy_source_arn="arn:aws:iam::" + account + ":role/github-actions-f2r-deploy", action_names=actions)["EvaluationResults"]
        output["deployPermissions"] = {a: host.enum(next((r.get("EvalDecision") for r in results if r.get("EvalActionName") == a), None), ("allowed", "implicitDeny", "explicitDeny")) for a in actions}
    except RuntimeError:
        output["deployPermissions"] = "unavailable"
    command = aws("ssm", "send-command", instance_ids=[iid], document_name="AWS-RunShellScript", timeout_seconds="120", parameters=json.dumps({"commands": [ssm_command()], "executionTimeout": ["120"]}))["Command"]["CommandId"]
    invocation = {}
    for _ in range(35):
        time.sleep(4)
        try:
            invocation = aws("ssm", "get-command-invocation", command_id=command, instance_id=iid)
        except RuntimeError:
            continue
        if invocation.get("Status") not in ("Pending", "InProgress", "Delayed"):
            break
    output["ssmStatus"] = host.enum(invocation.get("Status"), ("Success", "Failed", "TimedOut", "Cancelled", "Pending", "InProgress", "Delayed"))
    try:
        output["host"] = safe_host(invocation.get("StandardOutputContent", ""))
    except (ValueError, TypeError, AttributeError):
        output["host"] = {"status": "unavailable"}
    app_digest = output["host"].get("dbEndpointSha256")
    for entry, digest in zip(output["rds"], digests):
        entry["appDatabase"] = app_digest is not None and digest == app_digest
    matched = [e for e in output["rds"] if e["appDatabase"]]
    output["appDatabase"] = (matched[0]["identifier"] or matched[0]["alias"]) if len(matched) == 1 else ("ambiguous" if matched else "no-match")
    available = output["host"].get("memory", {}).get("MemAvailableMB")
    connected = output["host"].get("database", {}).get("Threads_connected")
    pools = output["host"].get("metrics", {}).get("hikaricp.connections.max", [])
    pool = pools[0] if pools else None
    output["gates"] = {"memory": "PASS" if available is not None and available >= 2500 else "FAIL", "connections": "PASS" if connected is not None and pool is not None and connected + pool <= 40 else "FAIL", "port8083": "PASS" if output["port8083"]["restrictedToAlb"] and not output["host"].get("bindings", {}).get("8083", ["unknown"]) else "FAIL"}
    print(json.dumps(output, allow_nan=False))
    return 0 if output["ssmStatus"] == "Success" and output["host"].get("database", {}).get("status") == "ok" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(collect())
    except Exception:
        print('{"status":"unavailable","reason":"diagnostic-read-failed"}')
        raise SystemExit(1)
