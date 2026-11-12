#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""cafe-4boards 패키지 출발 REVIEW 행만 기존 ADMIN review API로 처리한다.

계약(기존 triage_apply_prod.py와 동일 API, 신규 엔드포인트 발명 없음):
- GET  /api/admin/newstart/wiki/review      : REVIEW 큐만 읽는다.
- POST /api/admin/newstart/wiki/review/{id}/approve : reason + confirmed=true
- POST /api/admin/newstart/wiki/review/{id}/keep     : 상태 유지 + 검토 메모
- 대상은 tags의 ck=CAFE4B:... 로 이번 패키지에서 생성한 행만 골란다.
  (패키지 밖 행·기존 ACTIVE 행은 조회 대상에서 제외되어 수정·삭제가 전혀 없다.)
- 근거가 불완전한 자료(본문 이미지/링크뿐, 댓글 부분 수집)는 승인하지 않고 REVIEW에 남긴다.
  -> 2026-10-04 사용자 지시: "근거가 불완전한 이미지/첨부 중심 자료나 댓글 부분수집 자료는
     억지로 ACTIVE 처리하지 않는다."
- 재실행 가능: ACTIVE/ARCHIVED로 바뀐 행은 REVIEW 큐에 없으므로 자동으로 스킵된다.
"""
from __future__ import annotations

import argparse
import collections
import http.cookiejar
import json
import os
import pathlib
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE_URL = "https://feel2real.co.kr"
API = BASE_URL + "/api/admin/newstart/wiki"
LOGIN = BASE_URL + "/api/auth/login"
CK = re.compile(r"ck=(CAFE4B:[^\s,]+)")

REASON_APPROVE = ("카페 4boards tranch 2026-10-04 승인: 원문 URL·작성일·회원 경험 근거(상황→행동→반응→결과) 보존, "
                  "officialCheck=NOT_CHECKED 표기 유지. 경고는 확인 후 승인함(자동 승인이 아니라 이번 tranch 반영 지시).")
REASON_HOLD_MEDIA = ("REVIEW 유지: 본문이 이미지/링크 카드로만 구성되어 텍스트 근거가 불완전"
                     "(2026-10-04 지시: 근거 불완전 자료는 억지로 ACTIVE 금지). "
                     "provenance=하랑랑 스페셜/운영자 작성, historicalCANDIDATE=true, 작성일 2024-11-05 보존.")
REASON_HOLD_PARTIAL = ("REVIEW 유지: 스레드가 길어 댓글이 부분 수집됨(원문 대비 보존 수 불일치). "
                       "근거 보강 후 승인. 원문 URL·작성일·provenance는 보존됨.")


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

    def _request(self, method, url, body=None):
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

    def review_page(self, page, size):
        q = urllib.parse.urlencode({"page": page, "size": size, "sort": "oldest"})
        status, data = self._request("GET", API + "/review?" + q)
        if status != 200 or not isinstance(data, dict):
            raise RuntimeError("unexpected review response")
        return data

    def approve(self, kid, reason):
        return self._request("POST", f"{API}/review/{kid}/approve",
                             {"reason": reason, "confirmed": True})

    def keep(self, kid, reason):
        return self._request("POST", f"{API}/review/{kid}/keep", {"reason": reason})

    def stats(self):
        return self._request("GET", API + "/stats")[1]


def load_plan(root: pathlib.Path) -> dict:
    """패키지 RAW + 페이로드에서 ck -> 승인/보류 결정을 만든다(문서 결정이며 DB를 읽지 않는다)."""
    arts = {}
    for p in (root / "articles").glob("article_*.json"):
        a = json.loads(p.read_text(encoding="utf-8"))
        arts[a["articleId"]] = a
    manifest = json.loads((root / "load_batches" / "manifest.json").read_text(encoding="utf-8"))
    plan = {}
    for entry in manifest["batches"]:
        for row in json.loads((root / "load_batches" / entry["file"]).read_text(encoding="utf-8")):
            ck = CK.search(row["tags"]).group(1)
            aid = int(ck.split(":")[2])
            a = arts[aid]
            if a.get("contentForm") in ("IMAGE_ONLY", "LINK_PREVIEW") or a.get("collectionStatus") == "EMPTY_BODY":
                plan[ck] = ("HOLD", REASON_HOLD_MEDIA)
            elif a.get("collectionStatus") == "COMMENTS_PARTIAL":
                plan[ck] = ("HOLD", REASON_HOLD_PARTIAL)
            else:
                plan[ck] = ("APPROVE", REASON_APPROVE)
    return plan


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", required=True)
    ap.add_argument("--apply", action="store_true", help="미지정 시 대상 목록만 출력(dry-run)")
    args = ap.parse_args()
    plan = load_plan(pathlib.Path(args.root))
    print("PLAN rows=%d approve=%d hold=%d"
          % (len(plan), sum(1 for v in plan.values() if v[0] == "APPROVE"),
             sum(1 for v in plan.values() if v[0] == "HOLD")), flush=True)

    if not args.apply:
        print("DRY_RUN_ONLY 대상 선별만 수행, API write 없음")
        return 0

    login_id = os.environ.get("NEWSTART_SEED_ADMIN_LOGIN_ID", "")
    password = os.environ.get("NEWSTART_SEED_ADMIN_PASSWORD", "")
    if not login_id or not password:
        raise RuntimeError("required ADMIN secrets are not configured")
    api = Api(login_id, password)
    print("BEFORE stats:", json.dumps(api.stats(), ensure_ascii=False), flush=True)

    items, page = [], 0
    while True:
        data = api.review_page(page, 100)
        content = data.get("content", [])
        items.extend(content)
        if data.get("last", True):
            break
        page += 1
    mine = []
    for it in items:
        m = CK.search(it.get("tags") or "")
        if m and m.group(1) in plan:
            mine.append((it, m.group(1)))
    print("REVIEW queue=%d matched_this_package=%d" % (len(items), len(mine)), flush=True)

    counts = collections.Counter()
    errors = []
    for n, (it, ck) in enumerate(mine, 1):
        decision, reason = plan[ck]
        kid = int(it["knowledgeId"])
        try:
            status, _ = (api.approve(kid, reason) if decision == "APPROVE" else api.keep(kid, reason))
        except Exception as exc:
            errors.append((kid, ck, str(exc)))
            counts["error"] += 1
            continue
        if status != 200:
            errors.append((kid, ck, "HTTP %s" % status))
            counts["error"] += 1
        else:
            counts[decision.lower()] += 1
        if n % 50 == 0:
            print("PROGRESS %d/%d %s" % (n, len(mine), dict(counts)), flush=True)
        time.sleep(0.05)

    print("AFTER stats:", json.dumps(api.stats(), ensure_ascii=False), flush=True)
    print("RESULT matched=%d approved=%d held_review=%d errors=%d"
          % (len(mine), counts["approve"], counts["hold"], counts["error"]), flush=True)
    for kid, ck, err in errors[:30]:
        print("FAIL knowledgeId=%s ck=%s reason=%s" % (kid, ck, err), flush=True)
    return 1 if errors else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(2)
