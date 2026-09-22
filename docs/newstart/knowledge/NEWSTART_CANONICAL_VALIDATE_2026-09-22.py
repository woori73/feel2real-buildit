#!/usr/bin/env python3
"""NewStart Canonical validator (additive, re-runnable).

Validates the 3 canonical JSON files WITHOUT network / production DB.
Source of truth: branch docs/newstart-canonical-integration-20260922 HEAD 4b807bd7.
Policy: docs/newstart/knowledge/NEWSTART_KNOWLEDGE_AND_ANSWER_POLICY.md (do not reinterpret).

Usage:
    python NEWSTART_CANONICAL_VALIDATE_2026-09-22.py [--dir docs/newstart/knowledge]

Exit code: 0 = all FAIL-level checks pass (WARNINGs allowed), 1 = any FAIL.
"""
import json
import re
import sys
from collections import Counter
from pathlib import Path

FAQ_FILE = "NEWSTART_CANONICAL_FAQ_2026-09-22.json"
CASE_FILE = "NEWSTART_CANONICAL_CASE_2026-09-22.json"
REL_FILE = "NEWSTART_CANONICAL_RELATIONS_2026-09-22.json"

EXPECTED = {
    "faq_op": 83, "faq_chat": 26, "faq_official": 21, "faq_total": 130,
    "case_cafe": 30, "case_chat": 12, "case_ccrs": 20, "case_total": 62,
    "rel_total": 186, "patterns": 3, "unresolved": 108,
    "rel_by_type": {
        "PARTIALLY_SUPPORTED_BY": 63, "RELATED_FAQ": 76,
        "UNEXPLAINED_BY_OFFICIAL": 22, "CONFLICT_REVIEW": 19,
        "CONTRADICTS": 3, "RELATED_CASE": 3,
    },
    "conflicts": ["C-01", "C-02", "C-03", "C-04"],
    "pattern_members": {"PAT-01": 6, "PAT-02": 6, "PAT-03": 5},
}

PII_RE = re.compile(r"(\d{2,3}-\d{3,4}-\d{4})|([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})")


class Report:
    def __init__(self):
        self.rows = []  # (name, level, detail) level in PASS/FAIL/WARNING

    def add(self, name, level, detail=""):
        assert level in ("PASS", "FAIL", "WARNING")
        self.rows.append((name, level, detail))

    def failed(self):
        return any(l == "FAIL" for _, l, _ in self.rows)

    def print(self):
        for name, level, detail in self.rows:
            line = f"[{level}] {name}"
            if detail:
                line += f" — {detail}"
            print(line)
        fails = sum(1 for _, l, _ in self.rows if l == "FAIL")
        warns = sum(1 for _, l, _ in self.rows if l == "WARNING")
        print(f"---- validator: {len(self.rows)} checks, {fails} FAIL, {warns} WARNING ----")


def load(base):
    data = {}
    for f in (FAQ_FILE, CASE_FILE, REL_FILE):
        raw = (base / f).read_text(encoding="utf-8")
        data[f] = json.loads(raw)  # raises on parse error
    return data


def prefix_count(ids, prefix):
    return sum(1 for i in ids if i.startswith(prefix))


def check_provenance(kind, entry):
    """Per-class provenance rules (presence only, never invent facts)."""
    p = entry.get("provenance")
    if not isinstance(p, dict) or not p.get("source"):
        return False
    cid = entry.get("canonicalId", "")
    if cid.startswith("FAQ-OFFICIAL"):
        return bool(p.get("site") and p.get("citations") and p.get("verificationDoc"))
    if "CHAT" in cid:
        return bool(p.get("file") and p.get("lineRefs"))
    if cid.startswith("CASE-CCRS"):
        return bool(p.get("cafeKey") and p.get("article") and p.get("gradedFrom"))
    if cid.startswith("CASE-CAFE"):
        return bool(p.get("cafeKey") and p.get("article"))
    if cid.startswith("FAQ-OP"):
        return bool(p.get("cafeKey") or p.get("sourceArticle") or p.get("source"))
    return True


def main():
    base = Path(sys.argv[sys.argv.index("--dir") + 1]) if "--dir" in sys.argv else Path(__file__).parent
    rep = Report()

    # 1. JSON parse
    try:
        data = load(base)
        rep.add("json-parse", "PASS", "3 files json.loads OK")
    except FileNotFoundError as e:
        rep.add("json-parse", "FAIL", f"missing file: {e}")
        rep.print()
        return 1
    except json.JSONDecodeError as e:
        rep.add("json-parse", "FAIL", f"parse error: {e}")
        rep.print()
        return 1

    faq, case, rel = data[FAQ_FILE], data[CASE_FILE], data[REL_FILE]
    fe, cs, rs = faq["entries"], case["cases"], rel["relations"]

    # 2. counts (ID-prefix level = real distinguisher; classification lumps CHAT under OPERATOR)
    checks = [
        ("faq-op-ids", prefix_count([e["canonicalId"] for e in fe], "FAQ-OP-"), EXPECTED["faq_op"]),
        ("faq-chat-ids", prefix_count([e["canonicalId"] for e in fe], "FAQ-CHAT-"), EXPECTED["faq_chat"]),
        ("faq-official-ids", prefix_count([e["canonicalId"] for e in fe], "FAQ-OFFICIAL-"), EXPECTED["faq_official"]),
        ("faq-total", len(fe), EXPECTED["faq_total"]),
        ("case-cafe-ids", prefix_count([c["canonicalId"] for c in cs], "CASE-CAFE-"), EXPECTED["case_cafe"]),
        ("case-chat-ids", prefix_count([c["canonicalId"] for c in cs], "CASE-CHAT-"), EXPECTED["case_chat"]),
        ("case-ccrs-ids", prefix_count([c["canonicalId"] for c in cs], "CASE-CCRS-"), EXPECTED["case_ccrs"]),
        ("case-total", len(cs), EXPECTED["case_total"]),
        ("rel-total", len(rs), EXPECTED["rel_total"]),
        ("patterns", len(rel["patterns"]), EXPECTED["patterns"]),
        ("unresolved-refs", len(rel["unresolvedRefs"]), EXPECTED["unresolved"]),
    ]
    for name, got, want in checks:
        rep.add(f"count:{name}", "PASS" if got == want else "FAIL", f"got={got} want={want}")
    got_types = Counter(r["type"] for r in rs)
    for t, want in EXPECTED["rel_by_type"].items():
        got = got_types.get(t, 0)
        rep.add(f"count:rel-type:{t}", "PASS" if got == want else "FAIL", f"got={got} want={want}")
    extra_types = set(got_types) - set(EXPECTED["rel_by_type"])
    if extra_types:
        rep.add("count:rel-type:unexpected", "FAIL", f"{extra_types}")
    else:
        rep.add("count:rel-type:unexpected", "PASS", "no unexpected relation types")
    if sorted(rel["conflictRegistry"].keys()) == EXPECTED["conflicts"]:
        rep.add("count:conflict-registry", "PASS", "C-01~C-04")
    else:
        rep.add("count:conflict-registry", "FAIL", f"{sorted(rel['conflictRegistry'].keys())}")

    # 3. duplicate IDs
    all_ids = [e["canonicalId"] for e in fe] + [c["canonicalId"] for c in cs] + [r["canonicalId"] for r in rs]
    dups = [k for k, v in Counter(all_ids).items() if v > 1]
    rep.add("duplicate-ids", "PASS" if not dups else "FAIL", f"total={len(all_ids)} unique={len(set(all_ids))} dups={dups}")

    # 4. relation integrity
    idset = set(all_ids)
    dangling = [(r["canonicalId"], r["from"], r["to"]) for r in rs if r["from"] not in idset or r["to"] not in idset]
    rep.add("relation-dangling", "PASS" if not dangling else "FAIL", f"dangling={len(dangling)}")
    overlap = [u["target"] for u in rel["unresolvedRefs"] if u["target"] in idset]
    rep.add("unresolved-outside-canonical", "PASS" if not overlap else "FAIL",
            f"unresolved={len(rel['unresolvedRefs'])}, overlap-with-canonical={len(overlap)}")
    bad_tags = [(r["canonicalId"], r.get("conflictTag")) for r in rs
                if r.get("conflictTag") and r["conflictTag"] not in rel["conflictRegistry"]]
    rep.add("conflict-tag-refs", "PASS" if not bad_tags else "FAIL", f"orphan-tags={bad_tags}")
    bad_j = [r["canonicalId"] for r in rs if "판정" in str(r.get("judgment", "")) and "금지" not in str(r.get("judgment", ""))]
    rep.add("relation-no-verdict", "PASS" if not bad_j else "FAIL", f"verdict-like={len(bad_j)}")

    # 5. provenance presence (per-class rules)
    missing = [e["canonicalId"] for e in fe if not check_provenance("faq", e)]
    missing += [c["canonicalId"] for c in cs if not check_provenance("case", c)]
    rep.add("provenance-present", "PASS" if not missing else "FAIL", f"missing={missing}")

    # 6. official status (entry level; summary V-11 mixed judgment is a known divergence)
    off = [e for e in fe if e["canonicalId"].startswith("FAQ-OFFICIAL")]
    cnt = Counter(e.get("officialStatus") for e in off)
    rep.add("official-entry-counts",
            "PASS" if (cnt.get("OFFICIAL_CURRENT") == 16 and cnt.get("NOT_FOUND") == 3
                       and cnt.get("INSUFFICIENT_EVIDENCE") == 2) else "FAIL", dict(cnt))
    v11 = next(e for e in off if e["canonicalId"] == "FAQ-OFFICIAL-V-11")
    v11_ok = (v11.get("officialStatus") == "OFFICIAL_CURRENT"
              and "INSUFFICIENT" in str(v11.get("officialStatusNote", "")))
    rep.add("official-V-11-mixed-note", "PASS" if v11_ok else "FAIL", str(v11.get("officialStatusNote"))[:100])
    summ = faq.get("officialVerificationSummary", {})
    if summ.get("OFFICIAL_CURRENT") == 15 and "V-11" in str(summ.get("INSUFFICIENT_EVIDENCE")):
        rep.add("official-summary-V-11", "WARNING",
                "summary counts V-11 as INSUFFICIENT (15/3/3) while entry is OFFICIAL_CURRENT (16/2/3); "
                "entry wins per HANDOFF §3 mixed-judgment rule — see VALIDATION doc")
    else:
        rep.add("official-summary-V-11", "PASS", "summary consistent with entries")
    notfound_ids = sorted(e["canonicalId"] for e in off if e.get("officialStatus") == "NOT_FOUND")
    rep.add("official-notfound-set",
            "PASS" if notfound_ids == ["FAQ-OFFICIAL-V-05", "FAQ-OFFICIAL-V-08", "FAQ-OFFICIAL-V-20"] else "FAIL",
            str(notfound_ids))
    false_read = [e["canonicalId"] for e in fe
                  if "존재하지" in str(e.get("officialStatusNote", "")) or "불가능" in str(e.get("officialStatusNote", ""))]
    rep.add("notfound-not-false", "PASS" if not false_read else "FAIL", f"{false_read}")

    # 7. CASE -> OFFICIAL promotion must be 0
    promo = [c["canonicalId"] for c in cs if c.get("classification") == "OFFICIAL"]
    rep.add("case-to-official-promotion", "PASS" if not promo else "FAIL", f"promoted={promo}")
    off_src = [(e["canonicalId"], e.get("sourceType")) for e in fe if e.get("classification") == "OFFICIAL"]
    bad_src = [c for c, s in off_src if s != "OFFICIAL_SITE"]
    rep.add("official-sourceType", "PASS" if not bad_src else "FAIL", f"non-OFFICIAL_SITE={bad_src}")

    # 8. TRUE GAP overclaim must be 0
    reg = case.get("trueGapRegistry", {})
    gap_ok = (reg.get("FSS_fullchain_RESULT_CONFIRMED") == 0
              and reg.get("COURT_fullchain_RESULT_CONFIRMED") == 0
              and reg.get("COURT_15day_official_source") == 0
              and reg.get("Credit4U_detailed_guide", "").startswith("ABSENT")
              and reg.get("HF_hits_124corpus") == 0
              and reg.get("mortgage_extension_RESULT_CONFIRMED") == 0)
    rep.add("truegap-registry", "PASS" if gap_ok else "FAIL", "FSS/COURT/15d/Credit4U/HF/mortgage guards")
    confirmed = [c for c in cs if c.get("caseStatus") == "RESULT_CONFIRMED"]
    gap_articles = {"5987", "5752", "5988", "5901", "6010", "5947", "5957", "5868", "5883", "5749", "5968", "5997", "5920"}
    overclaim = [c["canonicalId"] for c in confirmed if str(c.get("sourceId")) in gap_articles]
    rep.add("truegap-no-overclaim", "PASS" if not overclaim else "FAIL", f"confirmed-on-gap-articles={overclaim}")
    do_not = [c["canonicalId"] for c in cs if c.get("doNotCanonicalize")]
    rep.add("donotcanonicalize-preserved", "PASS" if len(do_not) >= 10 else "FAIL", f"count={len(do_not)}")

    # 9. PII
    hits = []
    for e in fe:
        for m in PII_RE.finditer(json.dumps(e, ensure_ascii=False)):
            hits.append((e["canonicalId"], m.group(0)))
    for c in cs:
        for m in PII_RE.finditer(json.dumps(c, ensure_ascii=False)):
            hits.append((c["canonicalId"], m.group(0)))
    raw_leak = [e["canonicalId"] for e in fe if "contentRaw" in e] + \
               [c["canonicalId"] for c in cs if "contentRaw" in c or "authorNickname" in c]
    rep.add("pii-patterns", "PASS" if not hits and not raw_leak else "FAIL",
            f"regex-hits={hits} raw-fields={raw_leak}")

    # 10. repeated patterns: officialRule false + member counts
    for p in rel["patterns"]:
        want = EXPECTED["pattern_members"].get(p["patternId"])
        ok = (p.get("officialRule") is False and len(p.get("members", [])) == want)
        rep.add(f"pattern:{p['patternId']}", "PASS" if ok else "FAIL",
                f"officialRule={p.get('officialRule')} members={len(p.get('members', []))} want={want} "
                f"classification={p.get('classification')}")
        dang_m = [m for m in p.get("members", []) if m not in idset]
        rep.add(f"pattern:{p['patternId']}:members-resolve", "PASS" if not dang_m else "FAIL", f"dangling={dang_m}")
    pat2 = next(p for p in rel["patterns"] if p["patternId"] == "PAT-02")
    if pat2.get("classification") != "REPEATED_CASE_PATTERN":
        rep.add("pattern:PAT-02-class", "WARNING",
                f"classification={pat2.get('classification')} (vocabulary-legal, officialRule=false preserved)")

    # 11. NOT_FOUND + CASE coexistence via UNEXPLAINED_BY_OFFICIAL
    for v in ["V-05", "V-08", "V-20"]:
        faq_o = f"FAQ-OFFICIAL-{v}"
        links = [r["canonicalId"] for r in rs
                 if r["type"] == "UNEXPLAINED_BY_OFFICIAL" and r["to"] == faq_o]
        rep.add(f"coexist:{v}", "PASS" if links else "FAIL", f"unexplained-links={len(links)}")

    rep.print()
    return 1 if rep.failed() else 0


if __name__ == "__main__":
    sys.exit(main())
