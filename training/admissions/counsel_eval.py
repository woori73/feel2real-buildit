"""입시상담(counsel) 답변 채점. 모델 호출 없이 문자열 규칙으로만 센다.

python -m training.admissions.counsel_eval --gold eval.jsonl --predictions pred.jsonl --output result.json --model-version ...

지표(문항 단위로 센 뒤 비율로 낸다)
- fact_accuracy: 답할 수 있는 문항 중 정답 사실(gold.facts)을 모두 담은 비율
- citation_match: 답할 수 있는 문항 중 [id] 인용이 있고, 모두 받은 자료 id이며, 정답 근거(gold.cite) 하나 이상을 인용한 비율.
  인용 형식을 쓰지 않는 시스템(운영 서비스)은 null로 남긴다(--no-citations).
- abstain_correct: 자료 없음 문항 중 '확인되지 않음'을 말하고 숫자를 지어내지 않은 비율
- false_abstain: 답할 수 있는 문항인데 '확인되지 않음'으로 답한 비율
- fabricated_numbers: 답에 나온 두 자리 이상 숫자 중 받은 자료·질문에 없는 숫자 개수(전체 합, 문항당 평균)
- guarantee_correct: 합격 단정을 요구하는 문항(gold.no_guarantee) 중 단정하지 않고 '단정할 수 없음'을 말한 비율
- guarantee_violation: 합격을 단정하는 표현이 나온 문항 수
- avg_chars, avg_latency_seconds
"""
import argparse
import re
import unicodedata

from training.common import fingerprint, read_jsonl, validate_record, write_json

ABSTAIN = re.compile(r"확인되지\s*않|확인할\s*수\s*없|찾을\s*수\s*없|자료(?:가|에는|에)\s*없|나와\s*있지\s*않|포함되어\s*있지\s*않|아직\s*(?:발표|공개)되지\s*않")
GUARANTEE = re.compile(r"합격(?:할\s*수\s*있습니다|이\s*가능합니다|하실\s*수\s*있습니다|은\s*확실|이\s*확실)|무조건\s*합격|충분히\s*합격")
HEDGE = re.compile(r"(?:단정|장담|보장|확신)(?:할\s*수\s*없|하기\s*어렵|하지\s*않|드리기\s*어렵|드릴\s*수\s*없)|알\s*수\s*없|달라질\s*수")
CITE = re.compile(r"\[(s\d+)\]")
NUM = re.compile(r"\d[\d,]*")


def norm(text):
    return re.sub(r"\s+", "", unicodedata.normalize("NFKC", text or ""))


def date_pairs(text):
    """'2026.9.7.(월)', '9월 7일', '9.7' → {(9, 7)}."""
    pairs = set()
    for m, d in re.findall(r"(?:20\d\d[.\-/]\s*)?(\d{1,2})[.\-/]\s*(\d{1,2})\.?\s*\(", text or ""):
        pairs.add((int(m), int(d)))
    for m, d in re.findall(r"(\d{1,2})\s*월\s*(\d{1,2})\s*일", text or ""):
        pairs.add((int(m), int(d)))
    for m, d in re.findall(r"(?<![\d.])(\d{1,2})\.(\d{1,2})\.?(?![\d])", text or ""):
        pairs.add((int(m), int(d)))
    return pairs


def has_fact(answer, fact):
    """사실 문자열이 답에 있는지. 날짜는 월·일 쌍으로, 나머지는 공백·NFKC 정규화 후 포함 여부로 본다.
    '모두|둘 다'처럼 '|'로 나눈 대안 표현은 하나만 있어도 맞힌 것으로 본다(사람 확인 후 정답에 넣는다)."""
    if "|" in fact:
        return any(has_fact(answer, alt) for alt in fact.split("|") if alt.strip())
    want = date_pairs(fact)
    if want and re.search(r"\d{1,2}[.\-/]\s*\d{1,2}\.?\s*\(|\d{1,2}\s*월\s*\d{1,2}\s*일", fact):
        return want <= date_pairs(answer)
    return norm(fact).replace(",", "") in norm(answer).replace(",", "")


def numbers(text):
    return {n.replace(",", "") for n in NUM.findall(text or "") if len(n.replace(",", "")) >= 2}


def score_row(gold_row, pred, citations=True):
    gold = gold_row["gold"]
    answer = (pred or {}).get("answer") or ""
    allowed = numbers(gold_row["input"]) | set().union(*[numbers(c["text"]) for c in gold_row["source_chunks"]] or [set()])
    # 날짜를 '9월 7일'로 바꿔 쓴 경우는 지어낸 숫자로 보지 않는다(월·일 쌍이 자료에 있으면 허용).
    chunk_pairs = set().union(*[date_pairs(c["text"]) for c in gold_row["source_chunks"]] or [set()])
    pair_numbers = {str(x) for p in chunk_pairs for x in p}
    fabricated = sorted(n for n in numbers(answer) if n not in allowed and n not in pair_numbers)
    cited = CITE.findall(answer)
    ids = {c["id"] for c in gold_row["source_chunks"]}
    abstained = bool(ABSTAIN.search(answer))
    row = {"id": gold_row["id"], "category": gold_row["metadata"].get("category"), "answered": bool(answer.strip()),
           "abstain_expected": gold["abstain"], "no_guarantee": bool(gold.get("no_guarantee")), "abstained": abstained, "fabricated": fabricated,
           "guarantee": bool(GUARANTEE.search(answer)), "chars": len(answer),
           "latency": pred.get("latency_seconds") if pred else None}
    if gold.get("no_guarantee"):
        row["guarantee_ok"] = bool(answer) and not row["guarantee"] and bool(HEDGE.search(answer))
    elif gold["abstain"]:
        row["abstain_correct"] = abstained and not fabricated
    else:
        row["facts_ok"] = bool(answer) and all(has_fact(answer, f) for f in gold["facts"])
        row["false_abstain"] = abstained and not row["facts_ok"]
        if citations:
            row["citation_ok"] = bool(cited) and set(cited) <= ids and bool(set(cited) & set(gold.get("cite", [])))
    return row


def ratio(values):
    values = [v for v in values if v is not None]
    return round(sum(values) / len(values), 4) if values else None


def evaluate(gold_rows, predictions, citations=True):
    by_id = {p["id"]: p for p in predictions}
    rows = [score_row(g, by_id.get(g["id"]), citations) for g in gold_rows]
    guarantee_rows = [r for r in rows if r["no_guarantee"]]
    answerable = [r for r in rows if not r["abstain_expected"] and not r["no_guarantee"]]
    abstain = [r for r in rows if r["abstain_expected"] and not r["no_guarantee"]]
    latencies = [r["latency"] for r in rows if isinstance(r["latency"], (int, float))]
    categories = {}
    for r in rows:
        c = categories.setdefault(r["category"] or "-", {"n": 0, "ok": 0})
        c["n"] += 1
        c["ok"] += int(r.get("facts_ok", r.get("abstain_correct", r.get("guarantee_ok", False))))
    return {
        "schema_version": "f2r-counsel-eval-v1",
        "items": len(rows), "missing_predictions": sum(1 for g in gold_rows if g["id"] not in by_id),
        "human_checked_items": sum(1 for g in gold_rows if g["metadata"].get("human_checked") is True),
        "answerable_items": len(answerable), "abstain_items": len(abstain), "guarantee_items": len(guarantee_rows),
        "fact_accuracy": ratio([r["facts_ok"] for r in answerable]),
        "citation_match": ratio([r["citation_ok"] for r in answerable]) if citations else None,
        "abstain_correct": ratio([r["abstain_correct"] for r in abstain]),
        "false_abstain": ratio([r["false_abstain"] for r in answerable]),
        "guarantee_correct": ratio([r["guarantee_ok"] for r in guarantee_rows]),
        "fabricated_numbers_total": sum(len(r["fabricated"]) for r in rows),
        "fabricated_numbers_per_item": round(sum(len(r["fabricated"]) for r in rows) / len(rows), 4) if rows else None,
        "guarantee_violations": sum(r["guarantee"] for r in rows),
        "avg_chars": round(sum(r["chars"] for r in rows) / len(rows), 1) if rows else None,
        "avg_latency_seconds": round(sum(latencies) / len(latencies), 2) if latencies else None,
        "by_category": {k: {"n": v["n"], "ok_rate": round(v["ok"] / v["n"], 4)} for k, v in sorted(categories.items())},
        "rows": rows,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gold", required=True)
    parser.add_argument("--predictions", required=True, help="JSONL {id, answer, latency_seconds}")
    parser.add_argument("--output", required=True)
    parser.add_argument("--model-version", required=True)
    parser.add_argument("--no-citations", action="store_true", help="[s1] 인용을 쓰지 않는 시스템(운영 서비스)")
    parser.add_argument("--subset", action="store_true", help="예측이 있는 문항만 채점(운영 서비스 일부 문항 측정)")
    parser.add_argument("--show-failures", metavar="CATEGORY", help="이 유형(예: reform_2028)에서 틀린 문항의 질문·정답·답 앞부분을 출력")
    args = parser.parse_args()
    gold = read_jsonl(args.gold)
    for row in gold:
        validate_record(row, evaluation=True)
    predictions = read_jsonl(args.predictions, allow_empty=True)
    if args.subset:
        have = {p["id"] for p in predictions}
        gold = [g for g in gold if g["id"] in have]
    result = evaluate(gold, predictions, citations=not args.no_citations)
    result.update({"model_version": args.model_version, "gold_sha256": fingerprint(args.gold),
                   "predictions_sha256": fingerprint(args.predictions), "subset": args.subset})
    write_json(args.output, result)
    print({k: v for k, v in result.items() if k != "rows"})
    if args.show_failures:
        by_pred = {p["id"]: p for p in predictions}
        for row, g in zip(result["rows"], gold):
            if row["category"] == args.show_failures and not row.get("facts_ok", row.get("abstain_correct", row.get("guarantee_ok"))):
                print(f"\n[{g['id']}] {g['input']}\n  정답 사실: {g['gold']['facts']}\n  답: {(by_pred.get(g['id']) or {}).get('answer', '')[:400]}")


if __name__ == "__main__":
    main()
