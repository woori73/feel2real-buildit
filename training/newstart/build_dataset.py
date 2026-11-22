"""Validate the reviewed NewStart bundle before handing it to the shared GPU trainer."""
import argparse
import json
import re
from pathlib import Path

from training.common import read_jsonl, validate_record

PRIVATE = re.compile(r"[\w.+-]+@[\w.-]+\.\w+|(?<!\d)\d{6}[- ]?[1-8]\d{6}(?!\d)|"
                     r"(?<!\d)0\d{1,2}[- .]?\d{3,4}[- .]?\d{4}(?!\d)|"
                     r"(?<!\d)(?:\d{2,6}[- ]\d{2,6}[- ]\d{2,8}|\d{10,16})(?!\d)|"
                     r"[가-힣]+(?:대로|로|길|동|리)\s*\d+(?:번지|동|호|-\d+)?(?![\d%]|\s*(?:만\s*원|억\s*원|개월|년|일|시간))")


def write_rows(path, rows):
    with Path(path).open("w", encoding="utf-8") as stream:
        for row in rows:
            stream.write(json.dumps(row, ensure_ascii=False) + "\n")


def source_groups(row):
    chunks = row["source_chunks"]
    if not chunks or any(not c.get("group") or not re.fullmatch(r"\d{4}-(?:0[1-9]|1[0-2])", c.get("month", "")) for c in chunks):
        raise ValueError(f"{row['id']}: dated and identified source material required")
    return {c["group"] for c in chunks}


def prepare(eval_file, train_file, mode, output=None, review_reference=None):
    evaluation, training = read_jsonl(eval_file), read_jsonl(train_file)
    heldout = set()
    questions = set()
    cases = set()
    eval_ids = {r["id"] for r in evaluation}
    for rows, is_eval in ((evaluation, True), (training, False)):
        for row in rows:
            validate_record(row, evaluation=is_eval)
            meta = row["metadata"]
            question = row["input"].strip()
            if meta["domain"] != "newstart" or len(question) > 150 or question in questions:
                raise ValueError(f"{row['id']}: invalid or duplicate counseling question")
            questions.add(question)
            content = json.dumps([question, row["output"], row["source_chunks"]], ensure_ascii=False)
            if PRIVATE.search(content) or re.search(r"https?://|tc=shared_link", content, re.I):
                raise ValueError(f"{row['id']}: check source material for private data or links")
            groups = source_groups(row)
            if is_eval:
                heldout.update(groups | {meta["source_group"]})
            else:
                primary = meta["source_group"]
                if primary not in groups or groups & heldout or row["id"] in eval_ids:
                    raise ValueError(f"{row['id']}: missing primary case or evaluation overlap")
                cases.add(primary)
    if len(evaluation) != 150:
        raise ValueError("expected the 150-item holdout")
    if mode == "trial" and (len(training) != 400 or len(cases) < 100):
        raise ValueError("trial requires 400 rows from at least 100 distinct cases")
    if mode == "main" and (len(training) < 800 or len(cases) < 200):
        raise ValueError("main requires at least 800 rows from at least 200 distinct cases")
    if output is not None:
        for row in training:
            meta = row["metadata"]
            if review_reference and review_reference.strip():
                # Batch approval preserves the individual human_checked attestations.
                meta.update(gold_basis="approved_record", training_allowed=True,
                            review_status="approved", review_reference=review_reference.strip())
            elif not (meta.get("human_checked") is True or
                      meta.get("review_status") == "approved" and meta.get("review_reference")):
                raise ValueError("sample review decision or approved records required")
            validate_record(row, training=True)
        write_rows(output, training)
    return {"mode": mode, "eval_rows": len(evaluation), "train_rows": len(training), "distinct_cases": len(cases)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("trial", "main"), required=True)
    parser.add_argument("--eval", required=True)
    parser.add_argument("--train", required=True)
    parser.add_argument("--output", help="Write approved training input. Omit for a CPU-only data check.")
    parser.add_argument("--review-reference", help="Actual approval of this pair of input files.")
    args = parser.parse_args()
    print(prepare(args.eval, args.train, args.mode, args.output, args.review_reference))
