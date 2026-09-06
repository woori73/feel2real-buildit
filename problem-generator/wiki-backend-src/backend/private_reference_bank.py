"""Server-only, exact-source Question Wiki private reference selector."""
import json,gzip,re,logging,time
from functools import lru_cache
from pathlib import Path
_LOG=logging.getLogger(__name__)
class ReferenceNotFoundError(Exception):pass
_CORPUS_DIR=Path(__file__).with_name("private-reference")
_MARKER_RE=re.compile(r"QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST\s*:\s*(\{[^\n]+\})")
_TYPE_ALIASES={"similar":"other","grammar":"grammar","vocabulary":"vocabulary","blank":"blank","order":"order","insertion":"insertion","title-topic":"title_topic","title_topic":"title_topic","descriptive":"written","subjective":"written","written":"written","summary":"summary"}
_TYPE_MECHANICS={"grammar":"Mark tested forms in the passage and ask exactly one grammar/usage task.","vocabulary":"Place ①-⑤ directly on tested words/phrases and ask exactly one vocabulary task.","blank":"Use an idea-bearing visible blank in the passage and exactly five discriminating choices.","order":"Use (A)/(B)/(C) blocks and exactly five order combinations.","insertion":"Use one given sentence and exactly five in-passage insertion positions.","title_topic":"Ask exactly one title/topic task with five concise choices.","written":"CONSTRUCTED RESPONSE ONLY: no multiple-choice options, no circled choice numbers, no five-choice task. The instruction must require the student to write/supply an answer, and answer/explanation must solve that exact instruction.","summary":"Preserve the summary blanks and conditions.","other":"Preserve this source question's mechanics."}
@lru_cache(maxsize=2)
def _load_manifest(grade):
    try:return json.loads((_CORPUS_DIR/f"grade{grade}-manifest.json").read_text(encoding="utf-8"))
    except (OSError,json.JSONDecodeError):return {}
def resolve_shard(grade,qno):
    source="41-42" if qno in (41,42) else str(qno)
    for shard in _load_manifest(grade).get("shards") or []:
        if str(shard.get("sourceQuestionNo"))==source:return _CORPUS_DIR/shard["file"]
    return None
@lru_cache(maxsize=64)
def _load_question(grade,qno):
    path=resolve_shard(grade,qno)
    if not path:return ()
    try:
        with gzip.open(path,"rt",encoding="utf-8") as stream:return tuple(json.load(stream).get("records") or ())
    except (OSError,json.JSONDecodeError,KeyError):return ()
@lru_cache(maxsize=64)
def _same_question(grade, qno):return tuple(r for r in _load_question(grade,qno) if qno in (r.get("sourceQuestionNos") or [r.get("sourceQuestionNo")]))
def _request(prompt):
    match=_MARKER_RE.search(prompt or "")
    if not match:return None
    try:value=json.loads(match.group(1))
    except json.JSONDecodeError:return None
    return value if value.get("questionWiki") else None
def _fields(prompt):
    req=_request(prompt)
    if not req:return None,0,0,""
    try:grade,qno=int(req.get("grade") or 0),int(req.get("questionNo") or 0)
    except (TypeError,ValueError):grade,qno=0,0
    raw=str(req.get("type") or "").strip();return req,grade,qno,_TYPE_ALIASES.get(raw,raw)
def select(prompt,limit=2):
    """STRICT: exact grade/source question only; exact requested type first."""
    req,grade,qno,wanted=_fields(prompt)
    if not req:return []
    same=list(_same_question(grade,qno));exact=[r for r in same if r.get("type")==wanted]
    return (exact or same)[:max(1,min(int(limit),2))]
def _reference_text(item):return (f"Instruction: {item.get('instruction','')}\nType: {item.get('type','')}\nProblem mechanics example:\n{item.get('rawText','')}").strip()
def enrich_prompt(prompt):
    req,grade,qno,wanted=_fields(prompt)
    if not req:return prompt
    started=time.perf_counter();refs=select(prompt,limit=1);select_ms=(time.perf_counter()-started)*1000
    if not refs:raise ReferenceNotFoundError(f"No exact reference for grade={grade}, sourceNo={qno}, type={wanted}")
    blocks=[f"EXACT-SOURCE REFERENCE {i} (grade={r['grade']}, sourceNo={r['sourceQuestionNo']}, type={r['type']}):\n{_reference_text(r)}" for i,r in enumerate(refs,1)]
    guidance=("\n\nPRIVATE_REFERENCE_PROBLEMS (SERVER-ONLY):\nCURRENT QUESTION is the only content source. PRIVATE REFERENCE is a DESIGN REFERENCE ONLY. Learn its exam mechanics, structure, difficulty, tested relationship and distractor strategy, then CREATE A NEW VARIANT from CURRENT QUESTION. Do NOT reproduce or lightly edit the database problem. Never copy its passage wording, topic-specific sentences, names, facts, answer, choices, question number, citation/source line, bracketed attribution, or explanation. Never expose database metadata.\n"+f"PRIVATE_REFERENCE_SCOPE: grade={grade}, sourceNo={qno}, requestedType={wanted}.\nMECHANICS: {_TYPE_MECHANICS.get(wanted,_TYPE_MECHANICS['other'])}\n"+"HARD RULE: references from any other sourceQuestionNo are forbidden. If exact type is unavailable, use another variant from this same source question only to infer mechanics, then independently transform CURRENT QUESTION.\nOUTPUT FORMAT RULES: Never add a question-number-only line such as [31]. Never add a citation, source, author/book attribution, or bracketed source line below the passage. If the generated passage uses labeled blanks such as (A), (B), (C), EVERY answer choice must explicitly show every label used in the passage, e.g. '(A) ... - (B) ... - (C) ...'.\nTYPE CONTRACT OVERRIDES the reference example when the reference is only a same-question fallback. The final problem, answer, and explanation must all implement the requested type and the same student task.")
    enriched=prompt+guidance+"\n\n"+"\n\n".join(blocks);shard=resolve_shard(grade,qno)
    _LOG.info("question_wiki_reference grade=%s source_no=%s type=%s shard=%s refs=%s variants=%s reference_select_ms=%.2f prompt_build_ms=%.2f final_prompt_chars=%s estimated_tokens=%s",grade,qno,wanted,shard.name if shard else None,len(refs),[r.get('variantIndex') for r in refs],select_ms,(time.perf_counter()-started)*1000-select_ms,len(enriched),(len(enriched)+3)//4)
    return enriched
def has_request(prompt):return _request(prompt) is not None
_SCHEMA_MC_TYPES={"similar","grammar","vocabulary","blank","order","insertion","title-topic"};_WRITTEN_TYPES={"written","descriptive","subjective"}
def question_wiki_schema(prompt):
    req=_request(prompt)
    if not req:return None
    requested_type=str(req.get("type") or "").strip();type_schema={"type":"string","enum":[requested_type]} if requested_type else {"type":"string"};source_schema={"type":"integer"}
    try:source_schema={"type":"integer","enum":[int(req.get("questionWikiId"))]}
    except (TypeError,ValueError):pass
    choices={"type":"array","items":{"type":"string"}}
    if requested_type in _SCHEMA_MC_TYPES:choices.update({"minItems":5,"maxItems":5})
    elif requested_type in _WRITTEN_TYPES:choices.update({"maxItems":0})
    return {"type":"object","additionalProperties":False,"properties":{"type":type_schema,"instruction":{"type":"string","minLength":1},"passage":{"type":"string","minLength":1},"choices":choices,"answer":{"type":"string","minLength":1},"explanation":{"type":"string","minLength":1},"sourceQuestionId":source_schema,"generationMode":{"type":"string","enum":["QWEN_3_7_FLASH"]}},"required":["type","instruction","passage","choices","answer","explanation","sourceQuestionId","generationMode"]}
def output_issues(prompt,text):
    """Server-side format/quality guard before a Question Wiki result reaches Preview."""
    req=_request(prompt)
    if not req:return []
    try:q=json.loads(str(text).strip().removeprefix('```json').removeprefix('```').removesuffix('```').strip())
    except (json.JSONDecodeError,AttributeError):return ['INVALID_JSON']
    wanted=str(req.get('type') or '').strip();issues=[];choices=q.get('choices') if isinstance(q.get('choices'),list) else []
    if q.get('type')!=wanted:issues.append('TYPE_MISMATCH')
    try:
        if int(q.get('sourceQuestionId'))!=int(req.get('questionWikiId')):issues.append('SOURCE_QUESTION_ID_MISMATCH')
    except (TypeError,ValueError):issues.append('SOURCE_QUESTION_ID_MISMATCH')
    if q.get('generationMode')!='QWEN_3_7_FLASH':issues.append('GENERATION_MODE_MISMATCH')
    for key in ('instruction','passage','answer','explanation'):
        if not str(q.get(key) or '').strip():issues.append(key.upper()+'_EMPTY')
    passage=str(q.get('passage') or '');instruction=str(q.get('instruction') or '')
    if re.search(r'(?m)^\s*\[\s*\d{1,3}\s*\]\s*$',passage):issues.append('QUESTION_NUMBER_LEAK')
    if re.search(r'(?im)^\s*\[(?:from|source|adapted\s+from|출처)\b[^\]]*\]\s*$',passage) or re.search(r'(?im)^\s*(?:source|출처)\s*:',passage):issues.append('CITATION_NOT_ALLOWED')
    labels=[label for label in ('(A)','(B)','(C)','(D)','(E)') if label in passage]
    if labels and choices and any(any(label not in str(c) for label in labels) for c in choices):issues.append('BLANK_LABELS_MISSING_IN_CHOICES')
    if wanted in _SCHEMA_MC_TYPES and len(choices)!=5:issues.append('CHOICES_COUNT')
    if wanted in _WRITTEN_TYPES:
        if choices:issues.append('WRITTEN_HAS_CHOICES')
        if re.search(r'[①②③④⑤]|(?:5|다섯)\s*(?:개|가지).*선택|고르시오|적절한\s*것',instruction):issues.append('WRITTEN_MC_INSTRUCTION')
    if wanted=='blank' and not re.search(r'_{3,}|\[\s*blank\s*\]|\(\s*blank\s*\)|…{2,}|□{2,}',passage,re.I):issues.append('BLANK_MARKER_MISSING')
    if wanted=='order' and not all(x in passage for x in ('(A)','(B)','(C)')):issues.append('ORDER_BLOCKS_MISSING')
    if wanted=='insertion' and not (re.search(r'[①②③④⑤]',passage) or len(choices)==5):issues.append('INSERTION_POSITIONS_MISSING')
    return issues
