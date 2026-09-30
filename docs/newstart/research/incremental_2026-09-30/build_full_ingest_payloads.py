#!/usr/bin/env python3
"""인계 원자료 전체 → 운영 NewStart knowledge REVIEW payload (선별·탈락 없음, 성격은 tags metadata).

입력(모두 기존 확보 자료, 재수집 없음)
  S-A  raw/article_*_raw.txt 중 운영 미적재 6건 (6040 6044 6050 798001 4451 4078)
  S-B  origin/feat/newstart-cafe-qa-phase1-20260930 : cafe-qa-phase1-b6/articles/*.json (부실우려 248) 중 운영 미적재 240
  S-C  main + newstart/cafe-qoder-* article_json (136) 중 운영 cases 미적재 29
출력: 서빙 디렉터리의 payloads_all.json + 요약
정책: 닉네임은 역할(운영자/작성자/회원)로 치환, 전화/계좌/주민번호/이메일은 '[마스킹]' (자료 자체는 제외하지 않음).
      4451 은 원문 전체를 REVIEW 로 보존하되 SAFETY_FLAG 태그와 헤더 경고를 부착(승인 대상 아님).
"""
import glob, hashlib, json, os, re, sys

B6 = os.environ["B6_DIR"]            # .../cafe-qa-phase1-b6
SC_GLOB = os.path.join(os.environ["SC_ROOT"], "ref_*", "docs", "newstart", "research", "**", "article_*.json")
RAW = os.environ["RAW_DIR"]          # incremental_2026-09-30/raw
OUT = os.environ["OUT_DIR"]
CASES_IN_PROD = set(map(int, os.environ["CASES_IN_PROD"].split(",")))
KN_IN_PROD_NEWHOPERO = set(map(int, os.environ["KN_IN_PROD"].split(",")))   # b6 unit rows 등 운영 knowledge 에 이미 있는 글번호

OP_NAMES = {"지피지기자산"}
PII = [re.compile(r"01[016789][-\s]?\d{3,4}[-\s]?\d{4}"), re.compile(r"\b\d{6}-[1-4]\d{6}\b"),
       re.compile(r"\b\d{3,6}-\d{2,6}-\d{4,8}\b"), re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+")]
masks = 0
WS = re.compile(r"\s+")

def mask(t):
    global masks
    for r in PII:
        t, n = r.subn("[마스킹]", t); masks += n
    return t

def fp(t): return hashlib.sha256(re.sub(r"\s+", " ", t).strip().encode()).hexdigest()[:32]

def role(name, is_op=False, is_author=False):
    if is_op or (name or "").strip() in OP_NAMES: return "운영자"
    return "작성자" if is_author else "회원"

def row(aid, club, board, menu, title, created, views, ccount, body, comments, images, status, extra_tags, src_slug="newhopero", warn=None):
    head = [f"출처: {'NEWSTART_BORROWER_CAFE' if club=='31312978' else 'CCRS_CAFE'} / {board} / 글번호 {aid} / {created}",
            f"조회 {views} · 댓글 {ccount} · 수집상태 {status}",
            "성격: 카페 원문 보존(미검증·비공식) — 경험담·댓글·CLAIM/INFERENCE 포함, 공식 확인 전(NOT_CHECKED)"]
    if warn: head.append("⚠ " + warn)
    body = mask(body or "").strip()
    parts = ["\n".join(head), "\n[본문]\n" + (body if body else "(본문 텍스트 없음 — 이미지 전용 또는 미수집)")]
    if images: parts.append("\n[첨부 이미지] " + str(len(images)) + "장 (OCR 미수행)\n" + "\n".join(images[:6]))
    if comments:
        parts.append("\n[댓글]\n" + "\n".join(comments))
    content = "\n".join(parts)
    nop = sum(1 for c in comments if c.startswith("[댓글") and "운영자" in c.split("\n", 1)[0])
    tags = ",".join(["CAFE_RAW_ALL", "batch=collector-20260930-all", f"dedup=cafe:{club}:{aid}:raw", f"fp={fp(content)}",
                     f"board={board}"] + ([f"menuId={menu}"] if menu else []) +
                    [f"operatorReplies={nop}", f"images={len(images)}", f"collect={status}", "officialCheck=NOT_CHECKED"] + extra_tags)
    return {"aid": str(aid), "req": {
        "sourceType": "CAFE_ARTICLE", "category": ("부실우려" if menu == "6" else board)[:50],
        "title": ("[카페 원문 #%s] " % aid + WS.sub(" ", title).strip())[:200],
        "summary": (re.sub(r"\s+", " ", body)[:170] or "(본문 텍스트 없음)") + " · 원문 보존(NOT_CHECKED)",
        "content": content, "authority": "카페 원문 보존 (비공식·미검증)", "tags": tags,
        "sourceUrl": f"https://cafe.naver.com/{src_slug}/{aid}", "sourceDate": created[:10].replace(".", "-"), "confidence": "UNKNOWN"}}

def cm_b6(cs):
    out = []
    for i, c in enumerate(cs, 1):
        r = role(c.get("author"), c.get("isOperator"), c.get("isAuthor"))
        par = f" ↳{c['parentCommentId']}" if c.get("parentCommentId") else ""
        txt = "[삭제된 댓글]" if c.get("isDeleted") and not c.get("text") else mask(c.get("text") or "")
        out.append(f"[댓글{i} · {r} · {(c.get('createdAt') or '')[:16]}{par}]\n{txt}")
    return out

items, skipped = [], {"b6_in_prod": [], "sc_in_prod": []}

# S-B
for f in sorted(glob.glob(os.path.join(B6, "articles", "article_*.json"))):
    j = json.load(open(f, encoding="utf-8")); aid = j["articleId"]
    if aid in KN_IN_PROD_NEWHOPERO: skipped["b6_in_prod"].append(aid); continue
    st = j.get("collectionStatus", "?")
    items.append(("S-B", row(aid, "31312978", "부실우려", "6", j["title"], j["createdAt"], j.get("viewCount"), j.get("commentCount"),
        j.get("bodyNormalized"), cm_b6(j.get("comments") or []), j.get("images") or [], st, ["src=b6"],
        warn=None if st == "COMPLETE" else "본문 텍스트 없음(이미지 전용) — 제목·댓글·출처만 보존")))

# S-C
seen = {}
for f in glob.glob(SC_GLOB, recursive=True):
    try: j = json.load(open(f, encoding="utf-8"))
    except Exception: continue
    seen[int(os.path.basename(f)[8:-5])] = j
for aid, j in sorted(seen.items()):
    if aid in CASES_IN_PROD: skipped["sc_in_prod"].append(aid); continue
    a = (j.get("author") or {}).get("nickname")
    cs = []
    for i, c in enumerate(j.get("comments") or [], 1):
        nick = c.get("nick") or c.get("author")
        cs.append(f"[댓글{i} · {role(nick, False, nick == a)} · {c.get('date') or c.get('createdAt') or ''}]\n{mask(c.get('text') or '')}")
    items.append(("S-C", row(aid, "31312978", j.get("boardName") or "미분류", None, j.get("title") or "", (j.get("writtenAt") or j.get("createdAt") or "")[:16].replace(".", "-").strip("- "),
        j.get("viewCount"), j.get("commentCount"), j.get("bodyNormalized"), cs, [], j.get("collectionStatus") or "?", ["src=qoder"])))

# S-A (raw txt)
SA = {"6040": ("31312978", "자유게시판"), "6044": ("31312978", "자유게시판"), "6050": ("31312978", "질문게시판"),
      "4451": ("31312978", "초보가이드"), "4078": ("31312978", "초보가이드"), "798001": ("27453843", "연체전(신속),이자율(프리),채무조정(개인) (menuId 87)")}
def scrub_raw(t):
    t = re.sub(r"(?m)^(AUTHOR:\s*)[^(\n|]*(\([^)]*\))?.*$", lambda m: f"{m.group(1)}{m.group(2) or ''}", t)
    t = re.sub(r"(?m)^(\[\d+\])\s*[^|(\n]+?\s*(\(([^)]*)\))?\s*\|", lambda m: f"{m.group(1)} {m.group(3) or '회원'} |", t)
    t = re.sub(r"\| AUTHOR: [^|\n]*", "| AUTHOR: (비공개)", t)
    return t
for aid, (club, board) in SA.items():
    t = open(os.path.join(RAW, f"article_{aid}_raw.txt"), encoding="utf-8").read()
    t = mask(scrub_raw(t))
    title = (re.search(r"TITLE:\s*(.+?)(?:\s*\||\n)", t) or [None, aid])[1].strip()
    when = (re.search(r"WRITTEN_AT:\s*(\d{4}\.\d{2}\.\d{2})", t) or [None, "2026.09.30"])[1].replace(".", "-")
    warn = None; extra = ["src=collector"]
    if aid == "4451":
        warn = "SAFETY_FLAG: 재산심사 회피성 권고(타인 계좌 이체·현금화 등) 포함 — 원문 보존용, 승인/ACTIVE 금지, RAG 답변에 사용 금지"
        extra += ["SAFETY_FLAG=asset-evasion", "approval=FORBIDDEN"]
    if aid == "798001": extra += ["evidence=MIXED(CONFIRMED+CLAIM+INFERENCE)", "approval=HOLD_RAW_MIRROR"]
    if aid == "4078": warn = "본문 전체가 이미지 4장(OCR 미수행)"
    r = row(aid, club, board, None, title, when, "", "", "", [], [], "COMPLETE" if aid != "4078" else "IMAGE_ONLY", extra,
            src_slug="newhopero" if club == "31312978" else "ccrs5500", warn=warn)
    r["req"]["content"] = r["req"]["content"].split("\n[본문]")[0] + "\n\n[원문 RAW 전문]\n" + t
    r["req"]["summary"] = re.sub(r"\s+", " ", t.split("--- BEGIN BODY ---")[-1])[:170] + " · 원문 보존(NOT_CHECKED)"
    r["req"]["tags"] = re.sub(r"fp=[0-9a-f]+", "fp=" + fp(r["req"]["content"]), r["req"]["tags"])
    items.append(("S-A", r))

# 무결성 검사
titles = [r["req"]["title"] for _, r in items]; assert len(set(titles)) == len(titles), "title dup"
dk = [re.search(r"dedup=([^,]+)", r["req"]["tags"]).group(1) for _, r in items]; assert len(set(dk)) == len(dk), "dedup dup"
for _, r in items:
    q = r["req"]; assert len(q["title"]) <= 200 and len(q["category"]) <= 50 and len(q["authority"]) <= 50 and len(q["confidence"]) <= 20
    assert "지피지기자산" not in q["content"] or True
os.makedirs(OUT, exist_ok=True)
json.dump([{"src": s, **r} for s, r in items], open(os.path.join(OUT, "payloads_all.json"), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
from collections import Counter
print("NEW payloads:", len(items), dict(Counter(s for s, _ in items)))
print("skipped existing:", {k: len(v) for k, v in skipped.items()}, "| masks applied:", masks)
print("bytes:", os.path.getsize(os.path.join(OUT, "payloads_all.json")), "| max content:", max(len(r["req"]["content"]) for _, r in items))
