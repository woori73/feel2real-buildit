"""Offline import adapter. Produces proposals and a lossless sidecar; never uploads."""
import argparse
import hashlib
import json
import re
from pathlib import Path

LEVEL = {'중1':'M1','중2':'M2','중3':'M3','고1':'H1','고2':'H2','고3':'H3'}
DIFFICULTY = {'기본':'EASY','응용':'MEDIUM','심화':'HARD'}
TAGS = {'시제':'시제','조동사':'조동사','태':'수동태','관계사':'관계대명사·관계부사',
        '부사절·접속':'접속사','접속·병렬':'접속사','명사절':'접속사','가정법':'가정법',
        '비교':'비교구문','수 일치':'일치·화법','의문문':'의문문·간접의문문',
        '문장 구조':'문장 구조·형식','강조·도치':'문장 구조·형식'}

def digest(text):
    normalized=re.sub(r'[\s!"#$%&\'()*+,\-./:;<=>?@\[\\\]^_`{|}~·—–…]+','',text.lower())
    return hashlib.sha256(normalized.encode()).hexdigest()

def tags_for(q):
    c=q['primary_grammar'];d=q['detail_type']
    if c=='준동사':
        if '분사' in d and '부정사' not in d:return ['분사·분사구문']
        if '동명사' in d and '부정사' not in d:return ['동명사']
        if '동명사' in d and '부정사' in d:return ['부정사','동명사']
        return ['부정사']
    return [TAGS.get(c,'기타 문법')]

def adapt(items, existing=None):
    existing=existing or {}; seen=set();hashes=set();records=[];metadata=[];duplicates=[];conflicts=[]
    for q in items:
        ident=q['external_id']
        if ident in seen:raise ValueError('duplicate external_id: '+ident)
        seen.add(ident)
        choices=q['choices']; labels=[x['label'] for x in choices]
        if labels!=['1','2','3','4','5']:raise ValueError('invalid choice labels: '+ident)
        if q['answer'] not in labels:raise ValueError('invalid answer: '+ident)
        if q['answer_text']!=choices[labels.index(q['answer'])]['text']:raise ValueError('answer mismatch: '+ident)
        if len({x['text'].casefold().strip() for x in choices})!=5:raise ValueError('duplicate choices: '+ident)
        if q['recommended_difficulty'] not in DIFFICULTY:raise ValueError('invalid difficulty: '+ident)
        if q['recommended_grade'] is not None and q['recommended_grade'] not in LEVEL:raise ValueError('invalid recommended grade: '+ident)
        if q['grade'] not in LEVEL:raise ValueError('invalid original grade: '+ident)
        h=digest(q['new_question'])
        if h in hashes:raise ValueError('existing API stem hash collision: '+ident)
        hashes.add(h)
        canonical=json.dumps(q,ensure_ascii=False,sort_keys=True,separators=(',',':'))
        record_sha=hashlib.sha256(canonical.encode()).hexdigest()
        if ident in existing:
            (duplicates if existing[ident]==record_sha else conflicts).append(ident)
            continue
        r={'external_ref':ident,'stem':q['new_question'],'choices':[x['text'] for x in choices],
           'answer':q['answer'],'explanation':q['explanation']+'\n\n'+'\n'.join(x['label']+'번: '+x['text'] for x in q['choice_explanations']),
           'school_level':LEVEL[q['grade']], 'difficulty':DIFFICULTY[q['recommended_difficulty']],
           'format':'MULTIPLE_CHOICE','grammar_tags':tags_for(q),'confidence':0.50,
           'keywords':' '.join([ident,q['primary_grammar'],q['detail_type'],q['concept_code']]),'rights_status':'UNKNOWN'}
        records.append(r)
        metadata.append({'external_ref':ident,'record_sha256':record_sha,'metadata':q})
    return {'defaultRightsStatus':'UNKNOWN','records':records},metadata,{'duplicates':duplicates,'conflicts':conflicts,
        'registration_ready':False,'note':'current API ignores separate recommended grade and metadata; sidecar persistence/integration is required before upload'}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input',type=Path);parser.add_argument('output',type=Path)
    parser.add_argument('--existing-manifest',type=Path)
    args=parser.parse_args()
    items=[json.loads(x) for x in args.input.read_text(encoding='utf-8').splitlines() if x.strip()]
    existing=json.loads(args.existing_manifest.read_text()) if args.existing_manifest else {}
    payload,metadata,report=adapt(items,existing)
    args.output.mkdir(parents=True,exist_ok=True)
    (args.output/'api_import_PROPOSAL_NOT_APPROVED.json').write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding='utf-8')
    (args.output/'metadata_lossless.jsonl').write_text(''.join(json.dumps(x,ensure_ascii=False)+'\n' for x in metadata),encoding='utf-8')
    (args.output/'adapter_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    (args.output/'manifest.json').write_text(json.dumps({x['external_ref']:x['record_sha256'] for x in metadata},ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'records':len(payload['records']),'metadata':len(metadata),**report},ensure_ascii=False))

if __name__=='__main__':main()
