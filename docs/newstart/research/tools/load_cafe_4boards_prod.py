#!/usr/bin/env python3
"""Insert-only production loader for the preserved cafe 4-board batch.

No update/delete/approval calls. Exact stored-field matches are counted and skipped;
new records are created through the existing admin API as DRAFT, then moved to REVIEW.

b6(cafe-qa) 로더와 같은 계약·같은 안전장치이되, 기대 개수를 하드코딩하지 않고
manifest.totalToLoad에서 읽는다.
"""
from __future__ import annotations

import argparse
import collections
import glob
import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import http.cookiejar

BASE_URL = "https://feel2real.co.kr"
API = BASE_URL + "/api/admin/newstart/wiki"
LOGIN = BASE_URL + "/api/auth/login"
FIELDS = (
    "sourceType", "category", "title", "summary", "content", "authority",
    "tags", "sourceUrl", "sourceDate", "effectiveFrom", "effectiveTo",
    "confidence", "relatedCaseIds", "relatedRuleIds",
)


def sig(item: dict) -> tuple:
    return tuple(item.get(k) for k in FIELDS)


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
            # Suppress response body: it may echo user-provided source text.
            raise RuntimeError(f"HTTP {e.code} for {method} {urllib.parse.urlsplit(url).path}") from None
        except (urllib.error.URLError, TimeoutError) as e:
            raise RuntimeError(f"network error for {method} {urllib.parse.urlsplit(url).path}: {type(e).__name__}") from None

    def all_items(self) -> list[dict]:
        result, page = [], 0
        total_pages = None
        while total_pages is None or page < total_pages:
            query = urllib.parse.urlencode({"page": page, "size": 100, "sort": "oldest"})
            status, data = self._request("GET", API + "?" + query)
            if status != 200 or not isinstance(data, dict) or not isinstance(data.get("content"), list):
                raise RuntimeError("unexpected admin search response")
            result.extend(data["content"])
            total_pages = int(data.get("totalPages", 1))
            page += 1
        return result

    def create(self, payload: dict) -> dict:
        status, data = self._request("POST", API, payload)
        if status != 200 or not isinstance(data, dict) or not data.get("knowledgeId"):
            raise RuntimeError("create response missing knowledgeId")
        return data

    def move_to_review(self, knowledge_id: int) -> None:
        query = urllib.parse.urlencode({"status": "REVIEW"})
        status, _ = self._request("PUT", f"{API}/{knowledge_id}/status?{query}", {})
        if status != 200:
            raise RuntimeError("status transition failed")


def load_inputs(root: pathlib.Path):
    manifest = json.loads((root / "load_batches" / "manifest.json").read_text(encoding="utf-8"))
    expected = int(manifest["totalToLoad"])
    payloads, per_file = [], []
    for entry in manifest["batches"]:
        path = root / "load_batches" / entry["file"]
        rows = json.loads(path.read_text(encoding="utf-8"))
        if len(rows) != entry["count"]:
            raise RuntimeError(f"manifest count mismatch in {entry['file']}")
        per_file.append((path.name, len(rows)))
        payloads.extend(rows)
    if len(payloads) != expected:
        raise RuntimeError(f"expected {expected} payloads, found {len(payloads)}")

    malformed = [i for i, row in enumerate(payloads)
                 if not isinstance(row, dict) or any(
                     not isinstance(row.get(k), str) or not row[k].strip()
                     for k in ("sourceType", "title", "content"))]
    signatures = collections.Counter(sig(row) for row in payloads)
    duplicate_extras = sum(n - 1 for n in signatures.values() if n > 1)
    raw_files = glob.glob(str(root / "articles" / "article_*.json"))
    qa = json.loads((root / "qa_units.json").read_text(encoding="utf-8"))
    return payloads, per_file, malformed, duplicate_extras, len(raw_files), len(qa.get("units", [])), manifest


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", required=True, help="cafe-4boards package directory")
    ap.add_argument("--write", action="store_true", help="perform production inserts; otherwise read-only dry-run")
    args = ap.parse_args()
    root = pathlib.Path(args.root)
    payloads, per_file, malformed, duplicate_extras, raw_count, unit_count, manifest = load_inputs(root)
    print(f"INPUT raw_sources={raw_count} units={unit_count} candidates={len(payloads)} batches={len(per_file)}")
    print("BATCHES " + " ".join(f"{name}:{count}" for name, count in per_file))
    print(f"PER_BOARD " + " ".join(f"{k}={v}" for k, v in sorted(manifest.get("perBoard", {}).items())))
    print(f"INPUT_CHECK malformed={len(malformed)} exact_duplicate_extras={duplicate_extras}")
    if malformed or duplicate_extras:
        raise RuntimeError("input integrity gate failed; no API write attempted")

    login_id = os.environ.get("NEWSTART_SEED_ADMIN_LOGIN_ID", "")
    password = os.environ.get("NEWSTART_SEED_ADMIN_PASSWORD", "")
    if not login_id or not password:
        raise RuntimeError("required ADMIN secrets are not configured")
    api = Api(login_id, password)
    before = api.all_items()
    existing_sigs = {sig(item) for item in before}
    existing_indices = {i for i, row in enumerate(payloads) if sig(row) in existing_sigs}
    new_indices = [i for i in range(len(payloads)) if i not in existing_indices]
    print(f"PROD_BEFORE knowledge_rows={len(before)} exact_existing={len(existing_indices)} would_insert={len(new_indices)}")
    if not args.write:
        print("DRY_RUN_ONLY no production records changed")
        return 0

    inserted = review_ok = 0
    create_failed, review_failed = [], []
    for n, i in enumerate(new_indices, 1):
        row = payloads[i]
        try:
            saved = api.create(row)
            inserted += 1
        except Exception as e:
            create_failed.append((i, str(e)))
            continue
        try:
            api.move_to_review(int(saved["knowledgeId"]))
            review_ok += 1
        except Exception as e:
            review_failed.append((i, str(e)))
        if n % 25 == 0:
            print(f"PROGRESS processed={n}/{len(new_indices)} inserted={inserted} review={review_ok} create_failed={len(create_failed)}")
        time.sleep(0.05)

    after = api.all_items()
    after_sigs = {sig(item) for item in after}
    unresolved = sum(1 for i in range(len(payloads)) if sig(payloads[i]) not in after_sigs)
    print(f"PROD_AFTER knowledge_rows={len(after)} row_delta={len(after)-len(before)}")
    print(f"RESULT raw_sources={raw_count} units={unit_count} candidates={len(payloads)} "
          f"exact_existing_before={len(existing_indices)} newly_inserted={inserted} "
          f"moved_to_review={review_ok} create_failed={len(create_failed)} "
          f"review_transition_failed={len(review_failed)} unresolved_after={unresolved}")
    for i, err in (create_failed + review_failed)[:30]:
        print(f"FAIL candidate_index={i} reason={err}")
    return 1 if unresolved or create_failed or review_failed else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(2)
