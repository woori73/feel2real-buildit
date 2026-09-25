# -*- coding: utf-8 -*-
"""8·9월 MISSING/INCOMPLETE 복구용 dry-run 스테이징 생성기 (§10·§11).

입력:
  docs/newstart/research/article_json/article_XXXX.json   (GitHub 원문)
  ec_state.tsv  — `mysql -N` 읽기 전용 덤프: article_id <TAB> content_raw길이 <TAB> [댓글]포함여부

출력 (DB 쓰기 없음 — 전부 로컬 파일):
  data/newstart-cafe/repair_staging/articles/article_XXXX.json
      NewstartDataIngestionService.buildCaseEntity camelCase 계약.
      DB에 없는 MISSING 52건 + INCOMPLETE 6건의 전문(repair 시 update 대상).
  data/newstart-cafe/repair_staging/REPORT_repair_staging.md
      article_id / 월 / 분류 / 제목 / 게시판 / URL / 댓글수 / followUp /
      예측·최종 감면율 / target status(REVIEW) 인벤토리.

ec_state.tsv 재생성 (읽기 전용; 로컬 docker DB에서만):
  docker exec f2r_mysql_main mysql -uf2r_user -pf2r_password --default-character-set=utf8mb4 \
    f2r_db -N -e "SELECT article_id, CHAR_LENGTH(COALESCE(content_raw,'')), \
    LOCATE('[댓글', COALESCE(content_raw,''))>0 FROM newstart_empirical_cases;" > ec_state.tsv

감면율 규율(§9): structured rate는 build_gamian_cases.CASES에서 큐레이션된 값만 사용.
그 외 기사는 전부 null 유지 — 원문 본문·댓글·후속 결과만 verbatim 보존한다.
"""
import importlib.util
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
SRC_DIR = os.path.join(ROOT, "docs", "newstart", "research", "article_json")
OUT_DIR = os.path.join(ROOT, "data", "newstart-cafe", "repair_staging", "articles")
REPORT = os.path.join(ROOT, "data", "newstart-cafe", "repair_staging", "REPORT_repair_staging.md")
EC_STATE = os.path.join(ROOT, "ec_state.tsv")

_spec = importlib.util.spec_from_file_location("bgc", os.path.join(HERE, "build_gamian_cases.py"))
bgc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(bgc)


def month_of(dt):
    m = re.match(r"(\d{4})\.(\d{1,2})", dt or "")
    return f"{m.group(1)}-{int(m.group(2)):02d}" if m else "?"


def load_db_state():
    state = {}
    with open(EC_STATE, encoding="utf-8") as f:
        for line in f:
            aid, ln, hasc = line.rstrip("\n").split("\t")
            state[aid] = (int(ln), hasc == "1")
    return state


def verbatim_case(aid, d):
    """camelCase 계약 + 본문/댓글/후속 verbatim. structured 필드는 큐레이션 값만."""
    meta = dict(bgc.CASES.get(int(aid), {})) if aid.isdigit() else {}
    case = bgc.build(int(aid), meta)
    parts = [case.get("contentRaw") or ""]
    comments = [(c.get("text") or "").strip() for c in (d.get("comments") or [])]
    comments = [c for c in comments if c]
    if comments:
        parts.append("[댓글 %d개]\n" % len(comments) + "\n".join(f"- {c}" for c in comments))
    follow = (d.get("followUpResult") or "").strip()
    if follow:
        parts.append("[후속 결과]\n" + follow)
        meta.setdefault("authorFollowupResult", None)  # 큐레이션 없으면 null 유지(원문은 contentRaw에 보존)
    case["contentRaw"] = "\n\n".join(p for p in parts if p.strip())
    return case, len(comments), bool(follow)


def main():
    db = load_db_state()
    os.makedirs(OUT_DIR, exist_ok=True)
    rows = []
    for fn in sorted(os.listdir(SRC_DIR)):
        m = re.match(r"article_(\d+)\.json$", fn)
        if not m:
            continue
        aid = m.group(1)
        with open(os.path.join(SRC_DIR, fn), encoding="utf-8") as f:
            d = json.load(f)
        month = month_of(d.get("writtenAt"))
        in_db = aid in db
        if in_db:
            _, hasc = db[aid]
            has_src_chain = bool((d.get("comments") or [])) or bool((d.get("followUpResult") or "").strip())
            if hasc or not has_src_chain:
                continue
            cls = "INCOMPLETE"       # DB 행은 있는데 댓글/후속 사슬이 유실
        else:
            cls = "MISSING" if month in ("2026-08", "2026-09") else "MISSING_JUL"

        case, ncom, hasfollow = verbatim_case(aid, d)
        if cls == "MISSING_JUL":
            rows.append((aid, month, cls, d.get("title"), d.get("boardName"),
                         case["sourceUrl"], ncom, hasfollow, None, None, "-"))
            continue
        out = os.path.join(OUT_DIR, f"article_{aid}.json")
        with open(out, "w", encoding="utf-8") as f:
            json.dump(case, f, ensure_ascii=False, indent=2)
        rows.append((aid, month, cls, d.get("title"), d.get("boardName"), case["sourceUrl"],
                     ncom, hasfollow, case.get("predictedReductionRate"),
                     case.get("finalReductionRate"), "REVIEW"))

    with open(REPORT, "w", encoding="utf-8") as f:
        f.write("# 8·9월 정합성 복구 dry-run 인벤토리 (생성: build_repair_staging.py)\n\n")
        f.write("- 대상: MISSING(8·9월) + INCOMPLETE. July는 수집 개시 전이라 목록만 제공(스테이징 JSON 미생성).\n")
        f.write("- production INSERT 금지: 이 디렉터리의 JSON은 로컬 클론 검증용 staging 원본이다.\n")
        f.write("- rate 필드는 build_gamian_cases.py 큐레이션 값만 기재, 그 외 null(§9).\n")
        f.write("- target status는 ingestion 생성 시 REVIEW 강제 — ACTIVE 전환은 admin 승인 워크플로 경유.\n\n")
        f.write("| article_id | 월 | 분류 | 게시판 | 댓글 | 후속 | 예측률 | 최종률 | target | 제목 |\n")
        f.write("|---|---|---|---|---|---|---|---|---|---|\n")
        for aid, month, cls, title, board, url, ncom, fu, pr, fr, tgt in rows:
            t = (title or "").replace("|", "\\|")[:40]
            f.write(f"| {aid} | {month} | {cls} | {board or '-'} | {ncom} | {'Y' if fu else '-'} | {pr or '-'} | {fr or '-'} | {tgt} | {t} |\n")
        import collections
        cnt = collections.Counter(r[2] for r in rows)
        f.write(f"\n합계: {dict(cnt)}\n")
    print("staged:", sum(1 for r in rows if r[10] == "REVIEW"), "jul-listed:", cnt["MISSING_JUL"], "->", OUT_DIR)
    print("report:", REPORT)


if __name__ == "__main__":
    main()
