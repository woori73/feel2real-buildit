"""Start the staging JVM with only its own credentials and configuration."""
import ipaddress
import os
import re
import sys
from pathlib import Path


def main():
    env = {}
    for name in ("CODI_DB_HOST", "CODI_DB_PASSWORD", "CODI_JWT_SECRET"):
        value = os.environ.get(name)
        if not value:
            sys.exit(f"Missing {name}")
        env[name] = value
    # Keep the schema and JDBC options fixed. The hostname may be an RDS endpoint.
    host = env["CODI_DB_HOST"]
    if not re.fullmatch(r"[A-Za-z0-9.-]+", host):
        try:
            ipaddress.IPv6Address(host)
        except ValueError:
            sys.exit("Invalid CODI_DB_HOST")
        env["CODI_DB_HOST"] = f"[{host}]"
    port = os.environ.get("CODI_DB_PORT", "3306")
    if not port.isascii() or not port.isdecimal() or not 1 <= int(port) <= 65535:
        sys.exit("Invalid CODI_DB_PORT")
    env["CODI_DB_PORT"] = port
    if len(env["CODI_JWT_SECRET"].encode()) < 32:
        sys.exit("CODI_JWT_SECRET must be at least 32 bytes")

    chat = os.environ.get("CODI_CHAT_ENABLED", "false")
    if chat not in ("true", "false"):
        sys.exit("Invalid CODI_CHAT_ENABLED")
    if chat == "true":
        directory = os.environ.get("CREDENTIALS_DIRECTORY")
        if not directory or not Path(directory).is_absolute():
            sys.exit("Missing systemd credential directory for Codi chat")
        credential = Path(directory) / "codi-anthropic-key"
        if not credential.is_file():
            sys.exit("Missing Codi chat credential")
        env["CODI_CHAT_ENABLED"] = "true"
        env["CODI_CHAT_KEY_FILE"] = str(credential)

    env.update({
        "PATH": "/usr/bin:/bin",
        "LANG": "C.UTF-8",
        "AWS_EC2_METADATA_DISABLED": "true",
        "SPRING_PROFILES_ACTIVE": "codi-staging",
        "SPRING_CONFIG_LOCATION": "classpath:/config/application-codi-staging.yaml",
    })
    java = "/usr/bin/java"
    jar = Path(__file__).resolve().with_name("app.jar")
    os.execve(java, [java, "-jar", str(jar)], env)


if __name__ == "__main__":
    main()
