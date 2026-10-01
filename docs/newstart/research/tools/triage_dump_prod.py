#!/usr/bin/env python3
"""Read-only REVIEW queue dump for FAST TRIAGE analysis.

Paginates GET /api/admin/newstart/wiki/review (sort=oldest) and saves every
item with triage-relevant fields. No writes. Prints aggregate stats used to
derive the APPLY rule set (empty/spam/dup/unsafe candidates with samples).
"""
from __future__ import annotations

import argparse
import collections
import http.cookiejar
import json
import os
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE_URL = "https://feel2real.co.kr"
API = BASE_URL + "/api/admin/newstart/wiki"
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
            with self.opener.open(req, timeout=60) as resp:
                raw = resp.read()
                return resp.status, json.loads(raw.decode("utf-8")) if raw else None
        except urllib.error.HTTPError as e:
            raise RuntimeError(f"HTTP {e.code} for {method} {urllib.parse.urlsplit(url).path}") from None
        except (urllib.error.URLError, TimeoutError) as e:
            raise RuntimeError(f"network error: {type(e).__name__}") from None

    def review_page(self, page: int, size: int) -> dict:
        q = urllib.parse.urlencode({"page": page, "size": size, "sort": "oldest"})
        status, data = self._request("GET", API + "/review?" + q)
        if status != 200 or not isinstance(data, dict):
            raise RuntimeError("unexpected review response")
        return data


FIELDS = ("knowledgeId", "sourceType", "category", "title", "summary", "content",
          "authority", "tags", "sourceUrl", "sourceDate", "confidence",
          "relatedCaseIds", "createdBy", "createdAt", "status")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--size", type=int, default=100)
    args = ap.parse_args()
    login_id = os.environ.get("NEWSTART_SEED_ADMIN_LOGIN_ID")
    password = os.environ.get("NEWSTART_SEED_ADMIN_PASSWORD")
    if not login_id or not password:
        print("ADMIN credentials missing", file=sys.stderr)
        return 2
    api = Api(login_id, password)
    items, page, total = [], 0, None
    while True:
        data = api.review_page(page, args.size)
        content = data.get("content", [])
        if total is None:
            total = data.get("totalElements", "?")
        if not content:
            break
        items.extend({k: it.get(k) for k in FIELDS} for it in content)
        print(f"page {page}: +{len(content)} (total {len(items)}/{total})", flush=True)
        if data.get("last", True):
            break
        page += 1
    out = pathlib.Path(args.out)
    out.write_text(json.dumps(items, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"DUMPED {len(items)} review items -> {out}")

    by_type = collections.Counter(i.get("sourceType") for i in items)
    print("BY_TYPE:", dict(by_type))
    empty = [i["knowledgeId"] for i in items
             if not (i.get("title") or "").strip()
             and not (i.get("summary") or "").strip()
             and not (i.get("content") or "").strip()]
    print(f"EMPTY(all blank): {len(empty)} ids={empty[:20]}")
    groups: dict = collections.defaultdict(list)
    for i in items:
        key = ((i.get("title") or "").strip().lower(),
               (i.get("sourceUrl") or "").strip().rstrip("/").lower())
        groups[key].append(i["knowledgeId"])
    dups = {k: v for k, v in groups.items() if len(v) > 1}
    print(f"DUP(title+url) groups: {len(dups)} rows={sum(len(v) for v in dups.values())}")
    for k, v in list(dups.items())[:10]:
        print(f"  DUP {v} title={k[0][:60]!r}")
    for label, needles in {
        "FORBIDDEN": ("SAFETY_FLAG", "FORBIDDEN", "4451"),
        "UNSAFE_CAND": ("은닉", "허위", "가장", "사해", "회피", "조작", "위조", "차명"),
        "SPAM_CAND": ("카지노", "바카라", "토토", "대출광고", "비아그라", "http://bit.ly"),
    }.items():
        hits = [i["knowledgeId"] for i in items
                if any(n in " ".join(str(i.get(f) or "") for f in ("title", "summary", "content", "tags")) for n in needles)]
        print(f"{label}: {len(hits)} ids={hits[:20]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
