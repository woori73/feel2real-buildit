"""Install the production Kakao REST client before the normal F2R service restart."""
import json
import os
import re

from connect_google_client import install_client


def properties(raw):
    try:
        client = json.loads(raw)
        key, secret = client["client_id"], client["client_secret"]
        if not all(isinstance(v, str) and re.fullmatch(r"[A-Za-z0-9_-]{16,128}", v) for v in (key, secret)):
            raise ValueError
    except (ValueError, KeyError, TypeError):
        raise SystemExit("Invalid Kakao REST client configuration") from None
    return f"f2r.kakao.client-id={key}\nf2r.kakao.client-secret={secret}\n".encode()


if __name__ == "__main__":
    raw = os.environ.pop("KAKAO_CLIENT", "")
    if raw:
        install_client(os.environ["DEPLOY_IID"], properties(raw), "/var/lib/f2r-kakao", "client.properties", ":")
