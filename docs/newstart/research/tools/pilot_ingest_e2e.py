# -*- coding: utf-8 -*-
"""PILOT 8건 INGEST — POST /api/admin/newstart/wiki (create → DRAFT).
pilot_payload.json의 req 필드가 NewstartKnowledgeItemCreateRequest와 1:1 대응된다.
응답의 knowledgeId + status를 기록한다. (재실행 시 중복 생성되므로 1회만 실행)
"""
import json, sys, io
from urllib import request as rq, error as er

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BASE = "http://127.0.0.1:3400"
import os
COOKIE = open(os.environ.get("COOKIE_FILE", "cookies.txt"), encoding="utf-8").read()
tok = [l.split("\t")[-1].strip() for l in COOKIE.splitlines() if "ACCESS_TOKEN" in l][0]
HDR = {"Content-Type": "application/json", "Cookie": f"ACCESS_TOKEN={tok}"}

pp = json.load(open(sys.argv[1], encoding="utf-8"))
results = []
for p in pp["payloads"]:
    req = p["req"]
    body = json.dumps(req, ensure_ascii=False).encode("utf-8")
    r = rq.Request(BASE + "/api/admin/newstart/wiki", data=body, headers=HDR, method="POST")
    try:
        with rq.urlopen(r) as resp:
            out = json.loads(resp.read().decode("utf-8"))
            results.append({"unitId": p["unitId"], "http": resp.status, "id": out.get("knowledgeId"),
                            "status": out.get("status"), "title": (out.get("title") or "")[:40]})
    except er.HTTPError as e:
        results.append({"unitId": p["unitId"], "http": e.code, "err": e.read().decode("utf-8")[:200]})

json.dump(results, open(os.environ.get("RESULT_FILE", "pilot_ingest_result.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
ok = [r for r in results if r.get("status") == "DRAFT"]
print(f"ingested: {len(ok)}/{len(results)} as DRAFT")
for r in results:
    print(" ", r)
ids = [r["id"] for r in ok if r.get("id")]
json.dump(ids, open(os.environ.get("IDS_FILE", "pilot_ids.json"), "w"))
print("ids saved:", ids)
