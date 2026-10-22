#!/usr/bin/env python3
"""운영 임베딩(DB)과 실제 임베딩 서버로 검색 품질·유사도 하한(0.316)을 평가한다. 읽기 전용.

- DB: READ ONLY 트랜잭션 SELECT만(지식 항목 제목·종류·벡터). 쓰기·DDL·비밀값 출력 없음.
- 임베딩 서버(127.0.0.1:8081): 질문을 query 모드로 임베딩만 한다.
- 앱의 벡터 검색과 같은 계산(내적=코사인, 벡터는 L2 정규화 저장)이며, 키워드 검색·LLM 답변 단계는 포함하지 않는다.
"""
import json
import os
import shlex
import statistics
import subprocess
import tempfile
import urllib.request

URL = "http://127.0.0.1:8081/embed"
FLOOR = float(os.environ.get("EVAL_FLOOR", "0.316"))

HERE = os.path.dirname(os.path.abspath(__file__))
QUESTIONS_FILE = os.environ.get("EVAL_QUESTIONS", os.path.join(HERE, "eval_questions.json"))


def load_cases(path=QUESTIONS_FILE):
    """평가 질문 세트를 읽는다. category: relevant / irrelevant / no_evidence."""
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    cases = data["cases"]
    for c in cases:
        if c.get("category") not in ("relevant", "irrelevant", "no_evidence") or not c.get("question"):
            raise ValueError("invalid case: %r" % (c,))
    return cases


def case_hit(ranked, case):
    """상위 근거 중 expected_title_contains를 모두 포함하는 제목이 있는가(정답 근거 회수)."""
    need = case.get("expected_title_contains") or []
    if not need:
        return None
    return any(all(n in it["title"] for n in need) for _, it in ranked)


def load_env():
    values = {}
    for line in open("/etc/f2r/env"):
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        k = k.replace("export ", "").strip()
        if k in ("DB_HOST", "DB_USER", "DB_USERNAME", "DB_PASSWORD", "DB_NAME", "DB_PORT",
                 "APP_ADMISSIONS_EMBEDDING_MODEL_VERSION"):
            parts = shlex.split(v)
            values[k] = parts[0] if parts else ""
    return values


def quote(v):
    return '"' + v.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n") + '"'


def fetch_items(values):
    host = values.get("DB_HOST"); user = values.get("DB_USER") or values.get("DB_USERNAME"); pw = values.get("DB_PASSWORD")
    if not all((host, user, pw)):
        raise SystemExit("DB credential fields unavailable; no values printed")
    client = next((p for p in ("/usr/bin/mysql", "/usr/bin/mariadb") if os.path.isfile(p)), None)
    if not client:
        raise SystemExit("MySQL client unavailable")
    model = values.get("APP_ADMISSIONS_EMBEDDING_MODEL_VERSION", "")
    if not model or not all(c.isalnum() or c in "-._" for c in model):
        raise SystemExit("model version unavailable or unsafe")
    f = tempfile.NamedTemporaryFile(mode="w", prefix="admissions-eval-", delete=False)
    try:
        os.chmod(f.name, 0o600)
        cfg = "[client]\nhost=%s\nuser=%s\npassword=%s\nconnect-timeout=10\ndefault-character-set=utf8mb4\n" % (
            quote(host), quote(user), quote(pw))
        if values.get("DB_NAME"):
            cfg += "database=" + quote(values["DB_NAME"]) + "\n"
        if values.get("DB_PORT"):
            cfg += "port=" + values["DB_PORT"] + "\n"
        f.write(cfg); f.close()
        sql = ("SET SESSION TRANSACTION READ ONLY; START TRANSACTION READ ONLY; "
               "SELECT k.knowledge_id, SUBSTRING_INDEX(k.dedup_key,':',1), k.title, HEX(e.vector) "
               "FROM admissions_knowledge_items k JOIN admissions_knowledge_embeddings e ON e.knowledge_id=k.knowledge_id "
               "WHERE k.status='ACTIVE' AND e.model_version='%s'; ROLLBACK;" % model)
        out = subprocess.run([client, "--defaults-extra-file=" + f.name, "--batch", "--skip-column-names", "-e", sql],
                             capture_output=True, text=True, check=False)
        if out.returncode != 0:
            raise SystemExit("db query failed (message suppressed)")
    finally:
        try:
            f.close()
        finally:
            os.unlink(f.name)
    import struct
    items = []
    for line in out.stdout.splitlines():
        p = line.split("\t")
        if len(p) < 4:
            continue
        raw = bytes.fromhex(p[-1])
        vec = struct.unpack("<%df" % (len(raw) // 4), raw)  # 앱과 같은 little-endian
        items.append({"id": int(p[0]), "kind": p[1], "title": "\t".join(p[2:-1]), "vec": vec})
    return items, model


def embed_query(text):
    req = urllib.request.Request(URL, json.dumps({"texts": [text], "task": "query"}).encode(),
                                 {"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req, timeout=30))["embeddings"][0]


def top(items, q, k=3):
    scored = sorted(((sum(a * b for a, b in zip(q, it["vec"])), it) for it in items), key=lambda x: -x[0])
    return scored[:k]


def show(label, question, ranked):
    best = ranked[0][0]
    print("[%s] %s" % (label, question))
    for s, it in ranked:
        print("    %.3f %s %s #%d %s" % (s, "PASS" if s >= FLOOR else "----", it["kind"], it["id"], it["title"][:70]))
    return best


def main():
    cases = load_cases()
    items, model = fetch_items(load_env())
    print("items=%d model=%s floor=%.3f cases=%d" % (len(items), model, FLOOR, len(cases)))
    dims = {len(i["vec"]) for i in items}
    norms = [sum(x * x for x in i["vec"]) ** 0.5 for i in items]
    print("dims=%s norm_min=%.4f norm_max=%.4f" % (sorted(dims), min(norms), max(norms)))
    best = {"relevant": [], "irrelevant": [], "no_evidence": []}
    top1_ok = top1_n = recall_ok = recall_n = 0
    problems = []
    for cat, title in (("relevant", "관련 질문(근거가 있어야 함)"), ("irrelevant", "무관 질문(근거가 없어야 함)"),
                       ("no_evidence", "도메인은 같지만 DB에 근거 없는/거절해야 하는 질문")):
        print("\n== %s ==" % title)
        for c in [c for c in cases if c["category"] == cat]:
            ranked = top(items, embed_query(c["question"]), 3 if cat == "relevant" else 2)
            b = show(cat[:3].upper(), c["question"], ranked)
            best[cat].append(b)
            if c.get("note"):
                print("    note: %s" % c["note"])
            if cat == "relevant":
                top1 = ranked[0][1]
                if c.get("university"):
                    ok = c["university"] in top1["title"] and (not c.get("kind") or top1["kind"] == c["kind"])
                    top1_n += 1; top1_ok += ok
                    print("    -> top1 대학/종류 일치: %s" % ("YES" if ok else "NO"))
                    if not ok:
                        problems.append("top1 불일치: " + c["question"])
                hit = case_hit(ranked, c)
                if hit is not None:
                    recall_n += 1; recall_ok += hit
                    print("    -> 상위3 정답 근거 포함: %s" % ("YES" if hit else "NO"))
                    if not hit:
                        problems.append("정답 근거 미포함: " + c["question"])
                if b < FLOOR:
                    problems.append("관련 질문이 하한 미만: " + c["question"])
            elif cat == "irrelevant" and b >= FLOOR:
                problems.append("무관 질문이 하한 이상: " + c["question"])
    print("\n== 요약 ==")
    for cat, label in (("relevant", "relevant"), ("irrelevant", "irrelevant"), ("no_evidence", "no-evidence")):
        v = best[cat]
        if v:
            print("%s best-sim: min=%.3f median=%.3f max=%.3f" % (label, min(v), statistics.median(v), max(v)))
    r, i, n = best["relevant"], best["irrelevant"], best["no_evidence"]
    print("floor=%.3f: relevant below floor=%d/%d, irrelevant above floor=%d/%d, no-evidence above floor=%d/%d" % (
        FLOOR, sum(b < FLOOR for b in r), len(r), sum(b >= FLOOR for b in i), len(i), sum(b >= FLOOR for b in n), len(n)))
    print("top1 대학/종류 일치: %d/%d" % (top1_ok, top1_n))
    print("상위3 정답 근거 포함: %d/%d" % (recall_ok, recall_n))
    if r and i:
        print("분리 여유(관련 최저 - 무관 최고) = %.3f" % (min(r) - max(i)))
    print("(no-evidence 질문은 벡터 유사도로는 거를 수 없다 — 앱의 대학·학년도 구조 필터와 거절 규칙은 AdmissionsSearchAccuracyDbTest가 검증한다)")
    if problems:
        print("\n문제 %d건:" % len(problems))
        for p in problems:
            print("  - " + p)
    if os.environ.get("EVAL_STRICT") == "1" and problems:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
