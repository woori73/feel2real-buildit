#!/usr/bin/env python3
"""Insert-only production loader for curated official_updates connect batch.

Source: docs/newstart/research-official/updates-connect/official_updates_connect_2026-10-01.json
(built from k13~k42 verified sources + hand-verified FSC/HOPE collection - no re-crawl).

DRY_RUN (default): reads production admin list, reports which rows already exist.
WRITE (insert-only): POSTs missing rows as REVIEW via admin create, then publishes
each newly-created row via admin publish (reviewer = ADMIN login id). It never
updates, deletes, rejects, archives, or touches knowledge/RAG rows.

Duplicate rule: match by officialDocumentId first, then normalized originalUrl.
Service-level 400 duplicates are also caught and counted as skips.
"""
from __future__ import annotations

import argparse
import http.cookiejar
import json
import os
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE_URL = "https://feel2real.co.kr"
ADMIN = BASE_URL + "/api/admin/newstart/updates"
LOGIN = BASE_URL + "/api/auth/login"


class Api:
    def __init__(self, login_id: str, password: str):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.headers = {
            "Content-Type": "application/json; charset=utf-8",
            "Accept": "application/json",
            "Origin": BASE_URL,
            "Referer": BASE_URL + "/",
        }
        self._request("POST", LOGIN, {"loginId": login_id, "password": password})
        if not any(c.name == "ACCESS_TOKEN" for c in self.jar):
            raise RuntimeError("ADMIN login succeeded without ACCESS_TOKEN cookie")

    def _request(self, method: str, url: str, body=None):
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        req = urllib.request.Request(url, data=data, headers=self.headers, method=method)
        try:
            with self.opener.open(req, timeout=40) as resp:
                raw = resp.read()
                return resp.status, json.loads(raw.decode("utf-8")) if raw else None
        except urllib.error.HTTPError as e:
            raise RuntimeError(f"HTTP {e.code} for {method} {urllib.parse.urlsplit(url).path}") from None
        except (urllib.error.URLError, TimeoutError) as e:
            raise RuntimeError(f"network error for {method}: {type(e).__name__}") from None

    def list_all(self) -> list:
        status, data = self._request("GET", ADMIN)
        if status != 200 or not isinstance(data, list):
            raise RuntimeError("unexpected admin list response")
        return data

    def create(self, payload: dict) -> dict:
        status, data = self._request("POST", ADMIN, payload)
        if status != 200 or not isinstance(data, dict) or not data.get("updateId"):
            raise RuntimeError("create response missing updateId")
        return data

    def publish(self, update_id: int) -> dict:
        status, data = self._request("POST", f"{ADMIN}/{update_id}/publish", {})
        if status != 200:
            raise RuntimeError("publish failed")
        return data


def norm_url(u: str) -> str:
    return (u or "").strip().rstrip("/").lower()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--payload", required=True)
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args()

    login_id = os.environ.get("NEWSTART_SEED_ADMIN_LOGIN_ID")
    password = os.environ.get("NEWSTART_SEED_ADMIN_PASSWORD")
    if not login_id or not password:
        print("ADMIN credentials missing", file=sys.stderr)
        return 2

    rows = json.loads(pathlib.Path(args.payload).read_text(encoding="utf-8"))
    print(f"payload rows: {len(rows)}")
    api = Api(login_id, password)
    existing = api.list_all()
    print(f"production official_updates total: {len(existing)}")
    by_doc = {str(r.get("officialDocumentId") or "").strip().lower(): r for r in existing}
    by_url = {norm_url(r.get("originalUrl")): r for r in existing}

    created, published, skipped = 0, 0, 0
    for row in rows:
        doc = str(row.get("officialDocumentId") or "").strip().lower()
        url = norm_url(row.get("originalUrl"))
        if (doc and doc in by_doc) or (url and url in by_url):
            skipped += 1
            print(f"SKIP existing doc={row.get('officialDocumentId')}")
            continue
        if not args.write:
            print(f"WOULD CREATE {row.get('sourceType')} {row.get('officialDocumentId')}")
            continue
        try:
            made = api.create(row)
        except RuntimeError as e:
            skipped += 1
            print(f"SKIP service-duplicate doc={row.get('officialDocumentId')} ({e})")
            continue
        created += 1
        by_doc[doc] = made
        by_url[url] = made
        try:
            api.publish(made["updateId"])
            published += 1
            print(f"PUBLISHED id={made['updateId']} doc={row.get('officialDocumentId')}")
        except RuntimeError as e:
            print(f"REVIEW-ONLY id={made['updateId']} doc={row.get('officialDocumentId')} ({e})")

    print(f"RESULT created={created} published={published} skipped={skipped}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
