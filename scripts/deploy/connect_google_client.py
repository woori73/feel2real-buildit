"""Sync the operator's GitHub secret to the production host over an encrypted SSM handoff."""
import base64
import json
import os
import re
import shlex
import subprocess
import tempfile
import time
from pathlib import Path


def aws(*args):
    return subprocess.check_output(["aws", *args], text=True).strip()


def host(instance, script):
    # Only the public certificate or non-sensitive status is returned. Never dump diagnostics
    # containing the original command, the credential document, or its decrypted output.
    cmd = aws("ssm", "send-command", "--instance-ids", instance, "--document-name", "AWS-RunShellScript",
              "--parameters", json.dumps({"commands": ["set -eu", script]}),
              "--query", "Command.CommandId", "--output", "text")
    for _ in range(60):
        result = subprocess.run(["aws", "ssm", "get-command-invocation", "--instance-id", instance,
                                 "--command-id", cmd, "--output", "json"], capture_output=True, text=True)
        if result.returncode == 0:
            invocation = json.loads(result.stdout)
            if invocation["Status"] == "Success":
                return invocation["StandardOutputContent"].strip()
            if invocation["Status"] not in {"Pending", "InProgress", "Delayed"}:
                raise SystemExit("Google client host operation failed: " + invocation["Status"])
        time.sleep(4)
    raise SystemExit("Google client host operation timed out")


def web_document(value):
    try:
        web = json.loads(value)["web"]
        client = web["client_id"]
        secret = web["client_secret"]
        callbacks = {"https://feel2real.co.kr/login/oauth2/code/" + suffix for suffix in ("google", "google-student")}
        if (not re.fullmatch(r"[A-Za-z0-9._-]+\.apps\.googleusercontent\.com", client)
                or not re.fullmatch(r"[A-Za-z0-9_-]{8,256}", secret) or not callbacks.issubset(web["redirect_uris"])):
            raise ValueError
    except (TypeError, ValueError, KeyError):
        raise SystemExit("Expected a Google web-client JSON with both F2R HTTPS callback URIs") from None
    # Stable encoding avoids restarting on subsequent deploys with the same settings.
    return json.dumps({"web": {"client_id": client, "client_secret": secret,
                              "redirect_uris": sorted(callbacks)}}, sort_keys=True).encode()


def main():
    value = os.environ.pop("WEB_CLIENT", "")
    if not value:
        print("No GitHub Google client supplied; keeping host configuration.")
        return
    doc = web_document(value)
    instance = os.environ["DEPLOY_IID"]
    print("Preparing the server for secure credential transfer.", flush=True)
    directory = host(instance, "umask 077; mktemp -d /run/f2r-google-key.XXXXXXXX")
    if not re.fullmatch(r"/run/f2r-google-key\.[A-Za-z0-9]{8}", directory):
        raise SystemExit("Unexpected private transfer directory")
    try:
        cert = host(instance, f"""umask 077
cd {directory}
openssl req -new -x509 -newkey rsa:3072 -nodes -days 1 -subj /CN=f2r-google-transfer -keyout key -out cert >/dev/null 2>&1
cat cert""")
        with tempfile.TemporaryDirectory() as tmp:
            pem = Path(tmp, "recipient.pem")
            pem.write_text(cert)
            encrypted = subprocess.check_output(["openssl", "cms", "-encrypt", "-aes-256-cbc", "-binary",
                                                 "-outform", "DER", str(pem)], input=doc)
        packet = shlex.quote(base64.b64encode(encrypted).decode())
        # /run is ephemeral; decrypt to a private file and atomically install from the
        # destination filesystem. The existing systemd path unit handles the restart.
        print("Installing Google configuration on the server.", flush=True)
        host(instance, f"""umask 077
cd {directory}
printf %s {packet} | base64 -d > encrypted
openssl cms -decrypt -binary -inform DER -in encrypted -recip cert -inkey key -out client.json
rm -f key
dest=/var/lib/f2r-google/web-client.json
if ! cmp -s client.json "$dest"; then
  file=$(mktemp /var/lib/f2r-google/.install-XXXXXXXX)
  trap 'rm -f "$file"' EXIT
  install -m 0600 -o ec2-user -g ec2-user client.json "$file"
  mv -f "$file" "$dest"
fi
systemctl is-active --quiet f2r-google-activate.path""")
    finally:
        host(instance, f"rm -f {directory}/key {directory}/cert {directory}/encrypted {directory}/client.json; rmdir {directory}")
    print("Checking the Google sign-in page after activation.", flush=True)
    host(instance, """for i in $(seq 1 55); do
  if curl -fsS --max-time 3 http://127.0.0.1:8080/auth/google 2>/dev/null | grep -q '/auth/google/quick'; then exit 0; fi
  sleep 3
done
exit 1""")
    print("Google client configured; the live F2R Google entry page is active.")


if __name__ == "__main__":
    main()
