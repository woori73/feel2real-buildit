"""python -m training.eval.run --gold ... --predictions ... --output ..."""
import argparse
import sys
from pathlib import Path
from training.common import fingerprint, read_jsonl, validate_record, write_json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gold", required=True)
    parser.add_argument("--predictions", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--model-version", required=True)
    parser.add_argument("--retrieval-mode", choices=["D", "bm25", "embedding", "hybrid"], required=True)
    args = parser.parse_args()
    gold = read_jsonl(args.gold)
    for row in gold:
        validate_record(row, evaluation=True)
    predictions = read_jsonl(args.predictions, allow_empty=True)
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "problem-generator/wiki-backend-src"))
    from backend.eval_harness import evaluate
    result = evaluate(gold, predictions)
    result.update({"model_version": args.model_version, "retrieval_mode": args.retrieval_mode,
                   "gold_sha256": fingerprint(args.gold), "predictions_sha256": fingerprint(args.predictions)})
    write_json(args.output, result)


if __name__ == "__main__":
    main()
