# -*- coding: utf-8 -*-
"""NEWSTART INITIAL CASE SEED — canonical 107건(8·9월) 스테이징 생성기.

production empirical_cases는 0건이므로 이 산출물은 "repair"가 아니라 INITIAL SEED다.

SOURCE OF TRUTH:
  docs/newstart/research/article_json/  — canonical 원문 (Agent3 정본, 브랜치가 단일 정본)
  August+September canonical = articleId 5855~6010의 107건 (July 5737~5754 17건은 DEFER).
  repair_staging(58건)은 입력으로 사용하지 않는다.

출력 (DB 쓰기 없음 — 전부 로컬 파일, git 커밋 금지):
  data/newstart-cafe/initial_seed/articles/article_XXXX.json
      NewstartDataIngestionService.buildCaseEntity camelCase 계약 (107건).
  data/newstart-cafe/initial_seed/MANIFEST_initial_seed.tsv
      article_id <TAB> source_file <TAB> source_sha256 <TAB> staged_sha256 <TAB> written_month <TAB> parse_status
      — PII(본문·제목·닉네임)는 manifest에 기록하지 않는다.

검증 게이트 (하나라도 실패하면 exit 1):
  INPUT canonical 107 == OUTPUT 107 / articleId 중복 0 / writtenAt 월이 2026-07 또는 7월 이전이면 July 위반 /
  2026-10 이후 월 출현 시 범위 위반.

감면율 규율(§9) 계승: structured rate는 build_gamian_cases.CASES 큐레이션 값만, 그 외 null.
ec_state.tsv 의존 없음 — 신규 테이블 상태와 무관하게 canonical만으로 생성한다.
"""
import hashlib
import importlib.util
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
SRC_DIR = os.path.join(ROOT, "docs", "newstart", "research", "article_json")
OUT_DIR = os.path.join(ROOT, "data", "newstart-cafe", "initial_seed", "articles")
MANIFEST = os.path.join(ROOT, "data", "newstart-cafe", "initial_seed", "MANIFEST_initial_seed.tsv")

# build_repair_staging.py의 verbatim 변환 계약(본문+댓글+후속 체인)만 재사용한다.
_spec = importlib.util.spec_from_file_location(
    "brs", os.path.join(HERE, "build_repair_staging.py"))
brs = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(brs)


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def canonical_ids():
    ids = []
    for fn in os.listdir(SRC_DIR):
        m = re.match(r"article_(\d+)\.json$", fn)
        if m and int(m.group(1)) >= 5855:  # 8·9월 구간 (July 17건 = 5737~5754 제외)
            ids.append(int(m.group(1)))
    return sorted(ids)


def main():
    ids = canonical_ids()
    os.makedirs(OUT_DIR, exist_ok=True)
    rows = []
    errors = []
    for aid in ids:
        src = os.path.join(SRC_DIR, f"article_{aid}.json")
        src_sha = sha256_file(src)
        status = "OK"
        try:
            with open(src, encoding="utf-8") as f:
                d = json.load(f)
            month = brs.month_of(d.get("writtenAt"))
            case, _ncom, _fu = brs.verbatim_case(str(aid), d)
            if not (case.get("contentRaw") or "").strip():
                status = "EMPTY_BODY_IMAGE_ONLY"  # 5981/5996: 본문이 이미지뿐(OCR 후속 과제)
        except Exception as e:  # parse 실패는 manifest에 기록하고 전체 실패로 종료
            rows.append((aid, os.path.basename(src), src_sha, "", "?", f"FAIL:{e}"))
            errors.append(f"article_{aid}: {e}")
            continue
        if month in ("2026-07", "2026-06", "2026-05", "2026-04", "2026-03", "2026-02", "2026-01", "??"):
            rows.append((aid, os.path.basename(src), src_sha, "", month, f"FAIL:JULY_OR_UNKNOWN_MONTH"))
            errors.append(f"article_{aid}: month {month} out of Aug/Sep scope")
            continue
        if month not in ("2026-08", "2026-09"):
            rows.append((aid, os.path.basename(src), src_sha, "", month, "FAIL:OUT_OF_RANGE_MONTH"))
            errors.append(f"article_{aid}: month {month} out of range")
            continue
        out = os.path.join(OUT_DIR, f"article_{aid}.json")
        with open(out, "w", encoding="utf-8") as f:
            json.dump(case, f, ensure_ascii=False, indent=2)
        rows.append((aid, os.path.basename(src), src_sha, sha256_file(out), month, status))

    with open(MANIFEST, "w", encoding="utf-8") as f:
        f.write("article_id\tsource_file\tsource_sha256\tstaged_sha256\twritten_month\tparse_status\n")
        for r in rows:
            f.write("\t".join(str(x) for x in r) + "\n")

    staged = [r for r in rows if r[5].startswith(("OK", "EMPTY_BODY"))]
    months = {}
    for r in staged:
        months[r[4]] = months.get(r[4], 0) + 1
    dup = len(staged) - len({r[0] for r in staged})
    print(f"input canonical={len(ids)} output staged={len(staged)} months={months} dup={dup}")
    print("manifest:", MANIFEST)
    if errors or len(ids) != 107 or len(staged) != 107 or dup != 0:
        print("GATE FAILED:", errors[:5], file=sys.stderr)
        sys.exit(1)
    print("GATE PASS: 107/107, duplicates=0, july=0")


if __name__ == "__main__":
    main()
