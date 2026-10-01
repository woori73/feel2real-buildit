#!/usr/bin/env python3
"""FAST TRIAGE APPLY — REVIEW backlog through existing per-item ADMIN review APIs.

Rules (evidence: DUMP 1305 rows, 2026-10-01):
- HOLD (stay REVIEW, keepReview memo only):
    447 (SAFETY_FLAG=asset-evasion, approval=FORBIDDEN — never approve),
    396+632 (bank-setoff timing tactics — human judgment needed).
- ARCHIVE (reason required, rows preserved, never deleted):
    all-blank title/summary/content only. (No spam/unrelated patterns evidenced;
    same title+URL groups all differ in content -> kept, no title-only exclusion.)
- APPROVE rest with reason+confirmed=true (warnings reviewed, provenance kept).
  CLAIM/INFERENCE/COUNSELOR_CLAIM, past cases, official-mismatch are NOT
  exclusion grounds (operating principles).

No RAG/LLM/model/UI changes. No deletes. Re-runnable (non-REVIEW -> skip).
"""
from __future__ import annotations

import argparse
import http.cookiejar
import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE_URL = "https://feel2real.co.kr"
API = BASE_URL + "/api/admin/newstart/wiki"
LOGIN = BASE_URL + "/api/auth/login"

HOLD = {
    447: "SAFETY_FLAG=asset-evasion, approval=FORBIDDEN — 원본 보존, 승인 금지, RAG 사용 금지",
    396: "판단 필요: 은행 상계 회피 타이밍 조언 포함 — 운영자 판단 후 승인/보관",
    632: "판단 필요: 은행 상계 회피 타이밍 조언 포함 — 운영자 판단 후 승인/보관",
}

BUCKET = {
    "CAFE_ARTICLE": "CAFE_CASE", "CAFE_COMMENT": "CAFE_COMMENT", "CASE": "CASE_MIRROR",
    "OPERATOR_KNOWLEDGE": "OPERATOR_EXP", "OFFICIAL_CURRENT": "OFFICIAL_CUR",
    "EBOOK": "EBOOK", "DERIVED_RULE": "DERIVED",
}


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

    def approve(self, kid: int, reason: str):
        return self._request("POST", f"{API}/review/{kid}/approve",
                             {"reason": reason, "confirmed": True})

    def archive(self, kid: int, reason: str):
        return self._request("POST", f"{API}/review/{kid}/archive", {"reason": reason})

    def keep(self, kid: int, reason: str):
        return self._request("POST", f"{API}/review/{kid}/keep", {"reason": reason})

    def stats(self):
        status, data = self._request("GET", API + "/stats")
        return data


def blank(s) -> bool:
    return not (s or "").strip()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    login_id = os.environ.get("NEWSTART_SEED_ADMIN_LOGIN_ID")
    password = os.environ.get("NEWSTART_SEED_ADMIN_PASSWORD")
    if not login_id or not password:
        print("ADMIN credentials missing", file=sys.stderr)
        return 2
    api = Api(login_id, password)
    print("BEFORE stats:", json.dumps(api.stats(), ensure_ascii=False), flush=True)

    items, page = [], 0
    while True:
        data = api.review_page(page, 100)
        content = data.get("content", [])
        if not content:
            break
        items.extend(content)
        if data.get("last", True):
            break
        page += 1
    print(f"REVIEW queue: {len(items)}", flush=True)

    log = []
    counts = {"approved": 0, "archived": 0, "held": 0, "skipped": 0, "errors": 0}
    for it in items:
        kid = it["knowledgeId"]
        try:
            if it.get("status") != "REVIEW":
                counts["skipped"] += 1
                log.append({"id": kid, "action": "SKIP", "why": it.get("status")})
                continue
            if kid in HOLD:
                api.keep(kid, "TRIAGE_2026-10-01 HOLD: " + HOLD[kid])
                counts["held"] += 1
                log.append({"id": kid, "action": "HOLD", "why": HOLD[kid]})
                continue
            if blank(it.get("title")) and blank(it.get("summary")) and blank(it.get("content")):
                api.archive(kid, "TRIAGE_2026-10-01 EMPTY: 제목·요약·본문 모두 비어 RAG 근거로 사용할 실질 내용 없음(행 보존)")
                counts["archived"] += 1
                log.append({"id": kid, "action": "ARCHIVED", "why": "EMPTY"})
                continue
            bucket = BUCKET.get(it.get("sourceType"), "OTHER")
            reason = (f"TRIAGE_2026-10-01:{bucket} — 실제 사례/경험/공식 근거로 RAG 활용 가능. "
                      f"warnings 확인·provenance 유지, 단일사례 일반화는 답변단계 통제.")
            api.approve(kid, reason)
            counts["approved"] += 1
            if (counts["approved"] + counts["archived"]) % 100 == 0:
                print(f"progress: {counts}", flush=True)
            log.append({"id": kid, "action": "ACTIVE", "why": bucket})
        except RuntimeError as e:
            counts["errors"] += 1
            log.append({"id": kid, "action": "ERROR", "why": str(e)})
            time.sleep(2)
    print("AFTER stats:", json.dumps(api.stats(), ensure_ascii=False), flush=True)
    print("COUNTS:", counts, flush=True)
    by_action = {}
    for e in log:
        by_action[e["action"]] = by_action.get(e["action"], 0) + 1
    out = {"counts": counts, "by_action": by_action, "items": log}
    pathlib.Path(args.out).write_text(json.dumps(out, ensure_ascii=False), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
