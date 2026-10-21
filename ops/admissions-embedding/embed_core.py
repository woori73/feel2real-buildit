"""임베딩 서버의 순수 로직(요청 검증·프롬프트·정규화·유사도). 외부 패키지 없이 단위 테스트한다."""
import json
import math

DOC_PREFIX = "title: none | text: "
QUERY_PREFIX = "task: search result | query: "


class BadRequest(ValueError):
    pass


def parse_request(body, max_batch, max_chars):
    """POST /embed 본문 {"texts":[...], "task":"document|query"} → (texts, task)."""
    try:
        data = json.loads(body)
    except (ValueError, TypeError):
        raise BadRequest("invalid json")
    texts = data.get("texts") if isinstance(data, dict) else None
    if not isinstance(texts, list) or not texts:
        raise BadRequest("texts must be a non-empty list")
    if len(texts) > max_batch:
        raise BadRequest("too many texts")
    if not all(isinstance(t, str) and t.strip() for t in texts):
        raise BadRequest("texts must be non-blank strings")
    task = data.get("task", "document")
    if task not in ("document", "query"):
        raise BadRequest("task must be document or query")
    return [t[:max_chars] for t in texts], task


def with_prefix(texts, task):
    prefix = QUERY_PREFIX if task == "query" else DOC_PREFIX
    return [prefix + t for t in texts]


def l2_normalize(vec):
    norm = math.sqrt(sum(x * x for x in vec))
    if norm == 0:
        raise ValueError("zero vector")
    return [x / norm for x in vec]


def cosine(a, b):
    return sum(x * y for x, y in zip(a, b)) / (
        math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b)))


def suggest_floor(relevant_sims, irrelevant_sims):
    """관련 질문 최소값과 무관 질문 최대값의 중간. 분리되지 않으면 None (하한으로 걸러낼 수 없다)."""
    lo, hi = min(relevant_sims), max(irrelevant_sims)
    return round((lo + hi) / 2, 3) if lo > hi else None
