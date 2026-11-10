"""T08: pure offline metrics; no DB, model calls, or automatic release decisions."""
import math
import unicodedata
from .question_contract import validate_transform


def _answer(value):
    if isinstance(value, list) and value and all(type(v) is int and v > 0 for v in value):
        return tuple(sorted(value)) if len(set(value)) == len(value) else None
    if isinstance(value, str) and value.strip():
        return " ".join(unicodedata.normalize("NFKC", value).split())
    return None


def _key(question):
    return question.get("answer") if question.get("type") == "mc" else question.get("sampleAnswer")


def _gold_matches(question, gold):
    actual = _answer(_key(question))
    if question.get("type") == "mc":
        expected = _answer(gold.get("answer"))
        return expected is not None and actual == expected
    accepted = gold.get("acceptedAnswers", [])
    return actual is not None and any(actual == _answer(v) for v in accepted)


def _format_valid(question):
    try:
        validate_transform({"examTitle": "offline-eval", "questions": [question]})
        return True
    except (ValueError, TypeError):
        return False


def _evidence_matches(prediction, sources):
    refs = prediction.get("evidence_refs")
    if not isinstance(refs, list) or not refs:
        return False
    texts = {c["id"]: c["text"] for c in sources}
    for ref in refs:
        if not isinstance(ref, dict):
            return False
        quote = ref.get("quote")
        if not isinstance(quote, str) or not quote.strip() or ref.get("id") not in texts:
            return False
        if " ".join(quote.split()) not in " ".join(texts[ref["id"]].split()):
            return False
    return True


def _metric(values):
    return {"numerator": sum(values), "denominator": len(values),
            "value": sum(values) / len(values) if values else None}


def _number(v, integer=False):
    return (not isinstance(v, bool) and isinstance(v, (int, float)) and
            math.isfinite(v) and v >= 0 and (not integer or isinstance(v, int)))


def evaluate(gold_rows, prediction_rows):
    """Missing predictions count as failures. Missing observations stay null.

    Accuracy depends on the row's task: ``solve`` compares the model's answer with the teacher gold;
    ``generate`` (default) has no reference answer for a new question, so only a teacher review
    (``review.answer_key_correct``) counts — unreviewed valid items are reported, not guessed.

    Evidence is lexical provenance, not semantic proof. Audit rate's denominator
    is reviewed auto-pass samples, never all auto-pass items. Do not claim zero
    errors or service readiness from a small/unaudited sample.
    """
    expected = {r["id"]: r for r in gold_rows}
    predictions = {r["id"]: r for r in prediction_rows}
    if not expected or len(expected) != len(gold_rows) or len(predictions) != len(prediction_rows):
        raise ValueError("nonempty gold and unique ids required")
    if set(predictions) - set(expected):
        raise ValueError("unexpected prediction id")
    accuracy, evidence, forms, independent, audit, tokens, latency = ([] for _ in range(7))
    auto_pass, ignored_verifiers, unreviewed_generated = 0, 0, 0
    for rid, row in expected.items():
        pred = predictions.get(rid, {})
        question = pred.get("question")
        valid = isinstance(question, dict) and _format_valid(question) and not pred.get("error")
        forms.append(valid)
        if row.get("task", "generate") == "solve":
            # 풀이 과제: 교사 확인 문항의 기준 정답과 모델 답을 비교한다.
            accuracy.append(valid and _gold_matches(question, row["gold"]))
        else:
            # 생성 과제: 새 문항이라 기준 정답이 없다. 교사가 정답 키를 검수한 결과만 센다.
            review = pred.get("review")
            verdict = review.get("answer_key_correct") if isinstance(review, dict) else None
            if not valid:
                accuracy.append(False)
            elif type(verdict) is bool:
                accuracy.append(verdict)
            else:
                unreviewed_generated += 1
        evidence.append(valid and _evidence_matches(pred, row.get("source_chunks", [])))
        verify = pred.get("verification", {})
        answer = _answer(verify.get("answer"))
        if answer is not None:
            generator, verifier = pred.get("generator_model"), verify.get("model")
            if generator and verifier and generator != verifier and valid:
                independent.append(answer != _answer(_key(question)))
            else:
                ignored_verifiers += 1
        if pred.get("auto_pass") is True:
            auto_pass += 1
            observed = pred.get("audit", {})
            if observed.get("selected") is True and type(observed.get("correct")) is bool:
                audit.append(not observed["correct"])
        usage = pred.get("usage", {})
        pt, ct = usage.get("prompt_tokens"), usage.get("completion_tokens")
        if _number(pt, True) and _number(ct, True):
            tokens.append(pt + ct)
        elapsed = pred.get("latency_seconds")
        if _number(elapsed):
            latency.append(elapsed)
    return {
        "schema_version": "f2r-eval-v1", "questions": len(expected),
        "missing_predictions": len(set(expected) - set(predictions)),
        "accuracy": _metric(accuracy), "unreviewed_generated_count": unreviewed_generated,
        "evidence_match_rate": _metric(evidence),
        "format_compliance_rate": _metric(forms),
        "independent_disagreement_rate": _metric(independent),
        "auto_pass_audited_error_rate": _metric(audit),
        "auto_pass_count": auto_pass, "unreviewed_auto_pass_count": auto_pass - len(audit),
        "excluded_nonindependent_or_invalid_verifications": ignored_verifiers,
        "mean_tokens": sum(tokens) / len(tokens) if tokens else None,
        "tokens_observed": len(tokens),
        "mean_latency_seconds": sum(latency) / len(latency) if latency else None,
        "latency_observed": len(latency),
    }
