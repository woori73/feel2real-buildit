#!/usr/bin/env python3
"""seed-v3.3-ops.json 에서 ACTIVE 승격 대상이 아닌 공식자료 행만 제외해 운영 적재용 파일을 만든다.
사용법: python3 make_seed_active.py <seed-v3.3-ops.json> <out.json> [제외할 document_key ...]
기본 제외: doc_cau_2027_susi (중앙대 수시 사설 미러본 — 공식 원본 미확보).
제외 대상에 연결된 전형·일정이 있으면 중단한다(고아 행 방지).
"""
import json, sys
src, out, *ex = sys.argv[1:]
exclude = set(ex) or {"doc_cau_2027_susi"}
d = json.load(open(src, encoding="utf-8"))
linked = [r for k in ("ipsi_departments", "ipsi_admission_types", "ipsi_schedules")
          for r in d.get(k, []) if r.get("source_document_key") in exclude]
if linked:
    sys.exit(f"제외 대상에 연결된 행이 {len(linked)}건 있어 중단")
before = len(d["ipsi_documents"])
d["ipsi_documents"] = [x for x in d["ipsi_documents"] if x["document_key"] not in exclude]
json.dump(d, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("documents", before, "->", len(d["ipsi_documents"]),
      "types", len(d["ipsi_admission_types"]), "schedules", len(d["ipsi_schedules"]))
