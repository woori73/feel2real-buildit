"""Server-only, exact-source Question Wiki private reference selector."""
import json
import gzip
import re
from functools import lru_cache
from pathlib import Path

_CORPUS_DIR = Path(__file__).with_name("private-reference")
_MARKER_RE = re.compile(r"QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST\s*:\s*(\{[^\n]+\})")
_TYPE_ALIASES = {"similar":"other","grammar":"grammar","vocabulary":"vocabulary",
 "blank":"blank","order":"order","insertion":"insertion","title-topic":"title_topic",
 "title_topic":"title_topic","descriptive":"written","subjective":"written",
 "written":"written","summary":"summary"}
_TYPE_MECHANICS = {"grammar":"Mark tested forms in the passage.",
 "vocabulary":"Place ①-⑤ directly on tested words/phrases.",
 "blank":"Use an idea-bearing blank and five discriminating choices.",
 "order":"Use (A)/(B)/(C) blocks and five order combinations.",
 "insertion":"Use one given sentence and five in-passage positions.",
 "title_topic":"Use five concise title/topic choices.",
 "written":"Preserve the constructed-response conditions.",
 "summary":"Preserve the summary blanks and conditions.","other":"Preserve this source question's mechanics."}


@lru_cache(maxsize=2)
def _load_manifest(grade):
    try:
        return json.loads((_CORPUS_DIR/f"grade{grade}-manifest.json").read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def resolve_shard(grade,qno):
    """Resolve exactly one shard. Only 41/42 share the explicit common shard."""
    source="41-42" if qno in (41,42) else str(qno)
    for shard in _load_manifest(grade).get("shards") or []:
        if str(shard.get("sourceQuestionNo"))==source:
            return _CORPUS_DIR/shard["file"]
    return None


@lru_cache(maxsize=64)
def _load_question(grade,qno):
    path=resolve_shard(grade,qno)
    if not path:return ()
    try:
        with gzip.open(path,"rt",encoding="utf-8") as stream:
            return tuple(json.load(stream).get("records") or ())
    except (OSError,json.JSONDecodeError,KeyError):
        return ()


@lru_cache(maxsize=64)
def _same_question(grade, qno):
    # 41-42 is a single common-passage source and explicitly aliases both Wiki
    # question numbers; no other cross-question lookup is permitted.
    return tuple(r for r in _load_question(grade,qno)
                 if qno in (r.get("sourceQuestionNos") or [r.get("sourceQuestionNo")]))


def _request(prompt):
    match = _MARKER_RE.search(prompt or "")
    if not match:
        return None
    try:
        value = json.loads(match.group(1))
    except json.JSONDecodeError:
        return None
    return value if value.get("questionWiki") else None


def _fields(prompt):
    req = _request(prompt)
    if not req:
        return None, 0, 0, ""
    try:
        grade, qno = int(req.get("grade") or 0), int(req.get("questionNo") or 0)
    except (TypeError, ValueError):
        grade, qno = 0, 0
    raw = str(req.get("type") or "").strip()
    return req, grade, qno, _TYPE_ALIASES.get(raw, raw)


def select(prompt, limit=2):
    """STRICT: exact grade/source question only; exact requested type first."""
    req, grade, qno, wanted = _fields(prompt)
    if not req:
        return []
    same = list(_same_question(grade, qno))
    exact = [r for r in same if r.get("type") == wanted]
    return (exact or same)[:max(1, min(int(limit), 2))]


def _reference_text(item):
    # rawLayout/provenance remain in the server DB for reconstruction and QA,
    # but only the chosen problem text is placed in the model prompt.
    answer = item.get("answer") or {}
    return (f"Instruction: {item.get('instruction','')}\n"
            f"Problem:\n{item.get('rawText','')}\n"
            f"Original answer/explanation: {answer.get('raw','')}").strip()


def enrich_prompt(prompt):
    req, grade, qno, wanted = _fields(prompt)
    if not req:
        return prompt
    refs = select(prompt)
    blocks = [f"EXACT-SOURCE REFERENCE {i} (grade={r['grade']}, "
              f"sourceNo={r['sourceQuestionNo']}, type={r['type']}):\n{_reference_text(r)}"
              for i, r in enumerate(refs, 1)]
    guidance = ("\n\nPRIVATE_REFERENCE_PROBLEMS (SERVER-ONLY):\n"
      "CURRENT QUESTION is the only content source. PRIVATE REFERENCE controls only "
      "exam mechanics, structure, difficulty and distractor design. Never copy its topic, "
      "facts, names, sentences, choices or answer. Never expose database metadata.\n"
      f"PRIVATE_REFERENCE_SCOPE: grade={grade}, sourceNo={qno}, requestedType={wanted}.\n"
      f"MECHANICS: {_TYPE_MECHANICS.get(wanted, _TYPE_MECHANICS['other'])}\n"
      "HARD RULE: references from any other sourceQuestionNo are forbidden. "
      "If exact type is unavailable, transform only another variant from this same source question.")
    return prompt + guidance + (("\n\n" + "\n\n".join(blocks)) if blocks else "")


def has_request(prompt):
    return _request(prompt) is not None
