"""Server-only, exact-source Question Wiki private reference selector."""
import json,gzip,re,logging,time
from functools import lru_cache
from pathlib import Path
_LOG=logging.getLogger(__name__)
class ReferenceNotFoundError(Exception):pass
_CORPUS_DIR=Path(__file__).with_name("private-reference")
_MARKER_RE=re.compile(r"QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST\s*:\s*(\{[^\n]+\})")
_TYPE_ALIASES={"similar":"other","grammar":"grammar","vocabulary":"vocabulary","blank":"blank","order":"order","insertion":"insertion","title-topic":"title_topic","title_topic":"title_topic","descriptive":"written","subjective":"written","written":"written","summary":"summary"}
_TYPE_MECHANICS={
"grammar":"Create a five-choice grammar/usage task from CURRENT QUESTION. Mark exactly five tested forms/phrases in the passage with ①-⑤. The student must judge the marked language, and answer/explanation must identify the correct numbered item.",
"vocabulary":"Create a five-choice vocabulary task from CURRENT QUESTION. If the task uses five directly tested words/phrases, mark them ①-⑤ in the passage. If the task uses multiple labelled positions such as (A)/(B)/(C), keep those labels in the passage and use them as choice-column headers only; each choice contains the answer combination in label order.",
"blank":"Create a five-choice blank task from CURRENT QUESTION. Put a real visible blank at an idea-bearing position in the passage and make all five choices compete for that same blank. If multiple labelled blanks (A)/(B)/(C) are used, labels are column headers once above choices, not repeated in every choice.",
"order":"Create a five-choice paragraph-order task. The passage must contain actual (A), (B), (C) blocks derived from CURRENT QUESTION, and the five choices must be order combinations of those blocks.",
"insertion":"Create a five-choice sentence-insertion task. Provide one sentence to insert and exactly five clearly identifiable insertion positions ①-⑤ in the passage; answer/explanation must point to one of those positions.",
"title_topic":"Create exactly one title/topic/main-idea task from CURRENT QUESTION with five concise, discriminating choices. Do not invent a different topic.",
"written":"CONSTRUCTED RESPONSE ONLY. Create a genuine written-response instruction based on CURRENT QUESTION. No multiple-choice choices, no ①-⑤ choice numbers, and no 'choose the best answer' wording. Provide a model answer and explanation that solve that exact instruction.",
"summary":"Create a summary-completion task from CURRENT QUESTION while preserving visible summary blanks/conditions and making the answer/explanation match them.",
"other":"Create a genuinely new similar variant from CURRENT QUESTION while preserving the selected same-question reference's exam mechanics, difficulty and distractor strategy. Do not copy the reference problem itself."
}
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
def _same_question(grade,qno):return tuple(r for r in _load_question(grade,qno) if qno in (r.get("sourceQuestionNos") or [r.get("sourceQuestionNo")]))
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
    guidance=("\n\nPRIVATE_REFERENCE_PROBLEMS (SERVER-ONLY):\n"
      "CONTENT CONTRACT: CURRENT QUESTION is the ONLY content/material source for the new problem. PRIVATE REFERENCE is DESIGN REFERENCE ONLY. Learn only its question mechanics, layout relationship, difficulty, tested relationship, and distractor strategy. Then independently CREATE A NEW VARIANT from CURRENT QUESTION. Do NOT reproduce, paraphrase line-by-line, lightly edit, or re-send the database problem. Never copy reference passage wording, topic-specific sentences, names, facts, question number, choices, answer, explanation, citation/source line, author/book attribution, or database metadata.\n"
      +f"PRIVATE_REFERENCE_SCOPE: grade={grade}, sourceNo={qno}, requestedType={wanted}.\n"
      +f"REQUESTED TYPE CONTRACT: {_TYPE_MECHANICS.get(wanted,_TYPE_MECHANICS['other'])}\n"
      +"SOURCE SCOPE: references from any other sourceQuestionNo are forbidden. If the exact requested type is unavailable, another variant from THIS SAME source question may be used only to infer mechanics; the final problem must still implement REQUESTED TYPE CONTRACT.\n"
      +"CLEAN OUTPUT CONTRACT: Never add a standalone original question number such as [31], 31., or 'Question 31'. Never add [from ...], [source ...], adapted-from, 출처, author/book attribution, or any citation/source line below the passage. Do not expose PRIVATE_REFERENCE, REFERENCE_CONTEXT, DB labels, provenance, rawLayout, bbox, metadata, 상세 해설지, or 변형문제 인사이트.\n"
      +"MULTIPLE-LABEL CHOICE CONTRACT: When the passage contains two or more labelled positions such as (A), (B), (C), those labels must appear as ONE header row above the five numbered choices. Do NOT repeat (A)/(B)/(C) inside each choice. Each choice string contains only the corresponding answers in label order separated by ' - '. Example visual layout: '(A)        (B)        (C)' followed by '① clear - volatility - feasible', '② unclear - stability - feasible'.\n"
      +"ANSWER CONTRACT: answer and explanation must solve the NEWLY GENERATED problem exactly. Never reuse the reference DB answer/explanation merely because the mechanics are similar. For multiple choice, the answer must resolve to one of the five generated choices; for written response, provide the model response required by the generated instruction.\n"
      +"FINAL SELF-CHECK BEFORE OUTPUT: verify requested type, current-question-only content, no source/question-number leakage, correct five-choice structure where applicable, correct (A)/(B)/(C) column layout where applicable, and answer/explanation consistency. If any check fails, fix the generated problem before returning JSON.")
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
    passage=str(q.get('passage') or '');instruction=str(q.get('instruction') or '');answer=str(q.get('answer') or '');explanation=str(q.get('explanation') or '')
    if re.search(r'(?im)^\s*(?:\[\s*\d{1,3}\s*\]|question\s+\d{1,3}|\d{1,3}\.)\s*$',passage):issues.append('QUESTION_NUMBER_LEAK')
    if re.search(r'(?im)^\s*\[(?:from|source|adapted\s+from|출처)\b[^\]]*\]\s*$',passage) or re.search(r'(?im)^\s*(?:source|출처)\s*:',passage):issues.append('CITATION_NOT_ALLOWED')
    if re.search(r'(?:PRIVATE_REFERENCE|REFERENCE_CONTEXT|rawLayout|bbox|provenance|metadata|상세\s*해설지|변형문제\s*인사이트)',passage+' '+instruction,re.I):issues.append('REFERENCE_METADATA_LEAK')
    labels=[label for label in ('(A)','(B)','(C)','(D)','(E)') if label in passage]
    if len(labels)>=2 and choices and any(any(label in str(c) for label in labels) for c in choices):issues.append('BLANK_LABELS_REPEATED_IN_CHOICES')
    if len(labels)>=2 and choices and any(str(c).count(' - ')<len(labels)-1 for c in choices):issues.append('BLANK_CHOICE_COLUMNS_MISSING')
    if wanted in _SCHEMA_MC_TYPES:
        if len(choices)!=5:issues.append('CHOICES_COUNT')
        if not answer.strip():issues.append('ANSWER_EMPTY')
    if wanted in _WRITTEN_TYPES:
        if choices:issues.append('WRITTEN_HAS_CHOICES')
        if re.search(r'[①②③④⑤]|(?:5|다섯)\s*(?:개|가지).*선택|고르시오|적절한\s*것',instruction):issues.append('WRITTEN_MC_INSTRUCTION')
    if wanted=='blank' and not re.search(r'_{3,}|\[\s*blank\s*\]|\(\s*blank\s*\)|…{2,}|□{2,}|\([A-E]\)',passage,re.I):issues.append('BLANK_MARKER_MISSING')
    if wanted=='order' and not all(x in passage for x in ('(A)','(B)','(C)')):issues.append('ORDER_BLOCKS_MISSING')
    if wanted=='insertion' and not re.search(r'[①②③④⑤]',passage):issues.append('INSERTION_POSITIONS_MISSING')
    if wanted in ('grammar','vocabulary') and not (re.search(r'[①②③④⑤]',passage) or len(labels)>=2):issues.append('TESTED_POSITIONS_MISSING')
    if not explanation.strip():issues.append('EXPLANATION_EMPTY')
    return list(dict.fromkeys(issues))
