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

# (질문, 기대하는 대학 접두어, 기대 종류 'type'|'schedule'|None)
RELEVANT = [
    ("연세대학교 2027학년도 학생부교과 추천형 지원자격 알려주세요", "연세대", "type"),
    ("서울대 2027 수시 지역균형선발 수능최저학력기준이 어떻게 되나요", "서울대", "type"),
    ("고려대 학생부종합 학업우수형 전형방법이 궁금해요", "고려대", "type"),
    ("성균관대 2027학년도 수시 원서접수 일정 언제예요", "성균관대", "schedule"),
    ("한양대 정시 원서접수 기간 알려줘", "한양대", "schedule"),
    ("서강대 논술전형 제출서류가 뭐예요", "서강대", "type"),
    ("이화여대 정시 모집 일정", "이화여자대", "schedule"),
    ("경희대 수시 합격자 발표일", "경희대", "schedule"),
    ("한국외대 정시 전형방법", "한국외국어대", "type"),
    ("중앙대 정시 원서접수 일정", "중앙대", "schedule"),
    ("연대 교과전형 수능최저 있어?", "연세대", "type"),
    ("서울대 정시 일정이 어떻게 돼요", "서울대", "schedule"),
]
IRRELEVANT = [
    "오늘 점심 뭐 먹을까요", "강아지 훈련 방법 알려줘", "영어 단어 외우는 요령", "주말 날씨가 어떨까요",
    "파이썬 리스트 정렬하는 법", "다이어트에 좋은 운동 추천", "비트코인 가격 전망", "서울에서 부산 가는 기차 시간",
]
# 도메인은 같지만 DB에 근거가 없는 질문(근거 없음으로 처리돼야 안전)
NO_EVIDENCE = [
    "카이스트 2027학년도 수시 모집요강 알려주세요", "2025학년도 서울대 수시 지역균형 수능최저", "의대 면접 질문 기출 알려줘",
    "고려대 편입학 모집 일정", "대학원 입학 전형 안내", "미국 대학 SAT 점수 기준",
]


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
    items, model = fetch_items(load_env())
    print("items=%d model=%s floor=%.3f" % (len(items), model, FLOOR))
    dims = {len(i["vec"]) for i in items}
    norms = [sum(x * x for x in i["vec"]) ** 0.5 for i in items]
    print("dims=%s norm_min=%.4f norm_max=%.4f" % (sorted(dims), min(norms), max(norms)))
    rel_best, hit = [], 0
    print("\n== 관련 질문(근거가 있어야 함) ==")
    for q, univ, kind in RELEVANT:
        ranked = top(items, embed_query(q))
        best = show("REL", q, ranked)
        rel_best.append(best)
        top1 = ranked[0][1]
        ok = univ in top1["title"] and (kind is None or top1["kind"] == kind)
        hit += ok
        print("    -> top1 대학/종류 일치: %s (기대 %s/%s)" % ("YES" if ok else "NO", univ, kind))
    irr_best = []
    print("\n== 무관 질문(근거가 없어야 함) ==")
    for q in IRRELEVANT:
        irr_best.append(show("IRR", q, top(items, embed_query(q), 1)))
    noev_best = []
    print("\n== 도메인은 같지만 DB에 근거 없는 질문 ==")
    for q in NO_EVIDENCE:
        noev_best.append(show("NOEV", q, top(items, embed_query(q), 2)))
    print("\n== 요약 ==")
    print("relevant best-sim: min=%.3f median=%.3f max=%.3f" % (min(rel_best), statistics.median(rel_best), max(rel_best)))
    print("irrelevant best-sim: min=%.3f median=%.3f max=%.3f" % (min(irr_best), statistics.median(irr_best), max(irr_best)))
    print("no-evidence best-sim: min=%.3f median=%.3f max=%.3f" % (min(noev_best), statistics.median(noev_best), max(noev_best)))
    print("floor=%.3f: relevant below floor=%d/%d, irrelevant above floor=%d/%d, no-evidence above floor=%d/%d" % (
        FLOOR, sum(b < FLOOR for b in rel_best), len(rel_best), sum(b >= FLOOR for b in irr_best), len(irr_best),
        sum(b >= FLOOR for b in noev_best), len(noev_best)))
    print("top1 대학/종류 일치: %d/%d" % (hit, len(RELEVANT)))
    gap_lo, gap_hi = max(irr_best), min(rel_best)
    print("분리 여유(관련 최저 - 무관 최고) = %.3f" % (gap_hi - gap_lo))


if __name__ == "__main__":
    main()
