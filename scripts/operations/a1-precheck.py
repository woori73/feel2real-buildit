#!/usr/bin/env python3
"""Only allowlisted numbers/enums reach stdout. Never print raw command errors."""
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shlex
import signal
import subprocess
import tempfile
import urllib.request
import zipfile


def run(args, **kwargs):
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=45, **kwargs)
        return result.stdout if result.returncode == 0 else None
    except (OSError, subprocess.SubprocessError, UnicodeError):
        return None


def number(value):
    try:
        n = float(value)
        return n if math.isfinite(n) and n >= 0 else None
    except (ValueError, TypeError):
        return None


def enum(value, allowed):
    return value if value in allowed else "unknown"


def version(value):
    return value if isinstance(value, str) and re.fullmatch(r"[0-9]+(?:[._-][0-9]+){0,5}", value) else None


def systemd(unit):
    # Do NOT query ExecStart, Environment, EnvironmentFiles, User, paths, or unit contents.
    props = ["MainPID", "ActiveState", "MemoryCurrent", "MemoryPeak", "MemoryMax", "NRestarts", "Restart"]
    raw = run(["systemctl", "show", unit] + ["--property=" + p for p in props]) or ""
    values = dict(line.split("=", 1) for line in raw.splitlines() if "=" in line)
    safe = {p: number(values.get(p)) for p in props if p not in ("MainPID", "ActiveState", "Restart")}
    safe["state"] = enum(values.get("ActiveState"), ("active", "inactive", "failed", "activating", "deactivating"))
    safe["restart"] = enum(values.get("Restart"), ("no", "always", "on-failure", "on-abnormal", "on-abort", "on-success", "on-watchdog"))
    pid = values.get("MainPID", "0")
    return safe, int(pid) if pid.isdecimal() else 0


def parse_db(raw):
    result = {}
    numeric = {"max_connections", "Threads_connected", "Threads_running", "Max_used_connections", "Uptime", "app_connections", "failed_migrations"}
    for line in (raw or "").splitlines():
        fields = line.split("\t")
        if len(fields) == 2 and fields[0] in numeric:
            result[fields[0]] = number(fields[1])
        elif len(fields) == 2 and fields[0] in ("engine_version", "flyway_version"):
            result[fields[0]] = version(fields[1])
    result["status"] = "ok" if all(result.get(k) is not None for k in numeric) else "unavailable"
    return result


SQL = """SET SESSION TRANSACTION READ ONLY;
START TRANSACTION READ ONLY;
SELECT 'engine_version', SUBSTRING_INDEX(VERSION(),'-',1);
SELECT 'max_connections', @@max_connections;
SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected','Threads_running','Max_used_connections','Uptime');
SELECT 'app_connections', COUNT(*) FROM information_schema.processlist WHERE db=DATABASE();
SELECT 'flyway_version', version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;
SELECT 'failed_migrations', COUNT(*) FROM flyway_schema_history WHERE success=0;
ROLLBACK;
"""


def credentials(path):
    values = {}
    for line in Path(path).read_text().splitlines():
        line = line.strip()
        if line.startswith("export "):
            line = line[7:]
        if "=" not in line or line.startswith("#"):
            continue
        key, value = line.split("=", 1)
        if key in ("DB_HOST", "DB_USER", "DB_USERNAME", "DB_PASSWORD", "DB_NAME", "DB_PORT"):
            parsed = shlex.split(value)
            if len(parsed) != 1:
                raise ValueError("invalid configuration")
            values[key] = parsed[0]
    return values


def cnf_quote(value):
    if any(c in value for c in "\r\n\x00"):
        raise ValueError("invalid configuration")
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def database(path="/etc/f2r/env", runner=run):
    try:
        vals = credentials(path)
        settings = {"host": vals["DB_HOST"], "user": vals.get("DB_USER") or vals["DB_USERNAME"], "password": vals["DB_PASSWORD"], "database": vals.get("DB_NAME") or "f2r_db", "port": vals.get("DB_PORT") or "3306"}
        client = next((p for p in ("/usr/bin/mysql", "/usr/bin/mariadb") if Path(p).is_file()), "/usr/bin/mysql")
        # Prefer RAM-backed storage, reducing credential remnants after host failure/SIGKILL.
        ram = "/dev/shm" if os.path.isdir("/dev/shm") and os.access("/dev/shm", os.W_OK) else None
        with tempfile.TemporaryDirectory(prefix="f2r-a1-", dir=ram) as directory:
            os.chmod(directory, 0o700)
            config = Path(directory) / "client.cnf"
            fd = os.open(config, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "w") as output:
                output.write("[client]\n" + "".join(k + "=" + cnf_quote(v) + "\n" for k, v in settings.items()))
            # Identifiers/password stay in the file, SQL uses stdin. No MYSQL_PWD inheritance.
            env = {k: v for k, v in os.environ.items() if k not in ("MYSQL_PWD", "MYSQL_TEST_LOGIN_FILE")}
            raw = runner([client, "--defaults-file=" + str(config), "--batch", "--skip-column-names", "--connect-timeout=10"], input=SQL, env=env)
            return parse_db(raw)
    except (OSError, ValueError, KeyError):
        return {"status": "unavailable"}


def endpoint_digest(path="/etc/f2r/env"):
    """SHA-256 of the configured DB host only; the host name itself never leaves this process."""
    try:
        value = credentials(path)["DB_HOST"].strip().lower().rstrip(".")
        return hashlib.sha256(value.encode()).hexdigest() if value else None
    except (OSError, ValueError, KeyError):
        return None


MIGRATION = re.compile(r"BOOT-INF/classes/db/migration/V([0-9]+)__[^/]+\.sql")
MARKERS = {
    "studioImporter": "/domain/studio/StudioProductionImporter.class",
    "studioMcpController": "/domain/mcp/McpController.class",
}


def jar_info(path):
    """Identify the deployed build without reading configuration: size, mtime, SHA-256, newest bundled migration."""
    jar = Path(path)
    try:
        if not jar.is_file():
            return {"present": False}
        digest = hashlib.sha256()
        with jar.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1 << 20), b""):
                digest.update(chunk)
        with zipfile.ZipFile(jar) as archive:
            names = archive.namelist()
        versions = [int(m.group(1)) for m in (MIGRATION.fullmatch(n) for n in names) if m]
        return {"present": True, "sizeBytes": jar.stat().st_size, "mtimeEpoch": int(jar.stat().st_mtime), "sha256": digest.hexdigest(),
                "maxMigration": max(versions) if versions else None,
                "markers": {k: any(n.endswith(v) for n in names) for k, v in MARKERS.items()}}
    except (OSError, zipfile.BadZipFile, ValueError):
        return {"present": None}


def metrics():
    result = {}
    for name in ("jvm.memory.used", "jvm.memory.max", "hikaricp.connections.max", "hikaricp.connections.active", "hikaricp.connections.pending", "process.uptime"):
        try:
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            with opener.open("http://127.0.0.1:8080/actuator/metrics/" + name, timeout=4) as response:
                body = json.loads(response.read(65536))
            result[name] = [number(m.get("value")) for m in body.get("measurements", [])[:8]]
        except (OSError, ValueError, TypeError, AttributeError):
            result[name] = None
    return result


def collect():
    mem = {}
    for line in Path("/proc/meminfo").read_text().splitlines():
        key, value = line.split(":", 1)
        if key in ("MemTotal", "MemAvailable", "SwapTotal", "SwapFree"):
            mem[key + "MB"] = int(value.split()[0]) // 1024
    services = {}
    main_pid = 0
    for unit in ("f2r-app", "academycraft-app", "f2r-academycraft", "admissions-embedding", "wiki", "wiki-app"):
        services[unit], pid = systemd(unit)
        if unit == "f2r-app":
            main_pid = pid
    jvm = {}
    if main_pid:
        try:
            for line in Path(f"/proc/{main_pid}/status").read_text().splitlines():
                key, value = line.split(":", 1)
                if key in ("VmHWM", "VmRSS", "Threads"):
                    jvm[key] = number(value.split()[0])
            for arg in Path(f"/proc/{main_pid}/cmdline").read_bytes().split(b"\0"):
                if re.fullmatch(rb"-Xm[sx][0-9]+[mMgG]", arg):
                    jvm[arg[1:4].decode()] = arg[4:].decode()
        except OSError:
            pass
    processes = []
    for line in (run(["ps", "-eo", "rss=,nlwp=,comm=", "--sort=-rss"]) or "").splitlines()[:7]:
        fields = line.split(maxsplit=2)
        if len(fields) == 3:
            processes.append({"rssKB": number(fields[0]), "threads": number(fields[1]), "kind": enum(fields[2], ("java", "python3", "nginx", "mysqld"))})
    bindings = {str(p): [] for p in (8000, 8080, 8081, 8082, 8083, 3306)}
    for line in (run(["ss", "-ltnH"]) or "").splitlines():
        fields = line.split()
        if len(fields) >= 4:
            host, _, port = fields[3].rpartition(":")
            if port in bindings:
                bindings[port].append("loopback" if host.strip("[]") in ("127.0.0.1", "::1") else "non-loopback")
    oom = run(["journalctl", "-k", "--since", "7 days ago", "--no-pager"]) or ""
    return {"schema": 1, "memory": mem, "services": services, "jvm": jvm, "processes": processes, "metrics": metrics(), "bindings": bindings, "oomEvents7d": len(re.findall(r"oom-kill|killed process", oom, re.I)), "database": database(),
            "dbEndpointSha256": endpoint_digest(), "jar": {"current": jar_info("/home/ec2-user/app.jar"), "previous": jar_info("/home/ec2-user/app.jar.previous")}}


def interrupted(_sig, _frame):
    raise SystemExit(143)  # Unwind TemporaryDirectory on SSM cancellation.


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    try:
        print(json.dumps(collect(), allow_nan=False))
    except Exception:
        print('{"schema":1,"status":"unavailable"}')
        raise SystemExit(1)
