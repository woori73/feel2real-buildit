"""Server-only reference examples/index for Question Wiki generation."""
import json
import re
from pathlib import Path

_BANK_PATH = Path(__file__).with_name('private_reference_bank.json')
_EXACT_PATHS = [Path(__file__).with_name('private_reference_exact_g2.json')]
_INDEX_PATHS = {1: Path(__file__).with_name('private_reference_corpus_g1.json'), 2: Path(__file__).with_name('private_reference_corpus_g2.json')}
_MARKER_RE = re.compile(r'QUESTION_WIKI_PRIVATE_REFERENCE_REQUEST\s*:\s*(\{[^\n]+\})')
_TYPE_ALIASES = {'similar':'other','grammar':'grammar','vocabulary':'vocabulary','blank':'blank','order':'order','insertion':'insertion','title-topic':'title_topic','title_topic':'title_topic','descriptive':'written','subjective':'written','written':'written','summary':'summary'}
_TYPE_MECHANICS = {'grammar':'Mark the tested forms inside the passage. Never repeat five whole passage excerpts below as choices.','vocabulary':'Place ①-⑤ directly on target words/phrases inside the passage. Do not duplicate whole sentences as choices.','blank':'Use an idea-bearing blank and five discriminating choices.','order':'Use coherent (A)/(B)/(C) discourse blocks and five order combinations.','insertion':'Use one sentence and five in-passage insertion positions ①-⑤.','title_topic':'Use five concise title/topic choices with plausible distractors.','written':'Follow the teacher-bank constructed-response mechanics and explicit conditions.','summary':'Follow the teacher-bank summary mechanics.','other':'Follow the selected source question bank mechanics.'}

def _read_entries(path):
 try:return json.loads(path.read_text(encoding='utf-8')).get('entries') or []
 except (OSError,json.JSONDecodeError):return []

def _load_entries():
 entries=[]
 for path in _EXACT_PATHS:entries.extend(_read_entries(path))
 entries.extend(_read_entries(_BANK_PATH))
 return entries

def _load_index(grade):
 path=_INDEX_PATHS.get(grade)
 if not path:return {'totalVariants':0,'counts':{}}
 try:return json.loads(path.read_text(encoding='utf-8'))
 except (OSError,json.JSONDecodeError):return {'totalVariants':0,'counts':{}}

def _request(prompt):
 match=_MARKER_RE.search(prompt or '')
 if not match:return None
 try:value=json.loads(match.group(1))
 except json.JSONDecodeError:return None
 return value if value.get('questionWiki') else None

def _fields(prompt):
 req=_request(prompt)
 if not req:return None,0,0,''
 try:grade=int(req.get('grade') or 0)
 except (TypeError,ValueError):grade=0
 try:qno=int(req.get('questionNo') or 0)
 except (TypeError,ValueError):qno=0
 raw=str(req.get('type') or '').strip();return req,grade,qno,_TYPE_ALIASES.get(raw,raw)

def select(prompt,limit=2):
 """STRICT: exact grade/source question only; exact requested type first."""
 req,grade,qno,wanted=_fields(prompt)
 if not req:return []
 same=[x for x in _load_entries() if int(x.get('grade') or 0)==grade and int(x.get('sourceQuestionNo') or 0)==qno]
 exact=[x for x in same if str(x.get('type') or '')==wanted]
 return (exact or same)[:max(1,min(limit,2))]

def _corpus_note(prompt):
 req,grade,qno,wanted=_fields(prompt)
 if not req:return ''
 idx=_load_index(grade);counts=idx.get('counts') or {};exact=int(counts.get(f'{qno}:{wanted}') or 0);same=sum(int(v or 0) for k,v in counts.items() if k.startswith(f'{qno}:'))
 return (f'PRIVATE_REFERENCE_SCOPE: grade={grade}, sourceNo={qno}, requestedType={wanted}, exactTypeCount={exact}, sameQuestionCount={same}.\nMECHANICS: {_TYPE_MECHANICS.get(wanted,_TYPE_MECHANICS["other"])}\nHARD RULE: references from any other sourceQuestionNo are forbidden. Generate by transforming a reference from THIS source question only. If exact type is unavailable, transform another reference from this same source question into the requested type. Never search another question.')

def enrich_prompt(prompt):
 if not _request(prompt):return prompt
 refs=select(prompt);blocks=[]
 for i,item in enumerate(refs,1):blocks.append(f"EXACT-SOURCE REFERENCE {i} (grade={item.get('grade')}, sourceNo={item.get('sourceQuestionNo')}, type={item.get('type')}):\n"+str(item.get('referenceText') or '').strip())
 guidance='''\n\nPRIVATE_REFERENCE_PROBLEMS (SERVER-ONLY):\nThe selected source-question reference is the PRIMARY generation basis. Make a new variant by preserving its exam mechanics and difficulty while changing the tested detail/distractors enough to be a new problem. Never use a reference from another question. Do not expose database metadata. REFERENCE_CONTEXT/상세 해설지/인사이트 are not problem text and must never be emitted into passage.\n\n'''
 suffix=_corpus_note(prompt)
 if blocks:suffix+='\n\n'+'\n\n'.join(blocks)
 return prompt+guidance+suffix

def has_request(prompt):return _request(prompt) is not None
