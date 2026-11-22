"""Prepare archived exchanges for content review without copying member profiles."""
import argparse
import json
import re
from pathlib import Path

from training.newstart.build_dataset import PRIVATE, write_rows

DATE = re.compile(r'\n\s*20\d\d\.\s*\d{1,2}\.\s*\d{1,2}\.\s*\d{1,2}:\d{2}')
QUESTION = re.compile(r'\?|나요|까요|문의|궁금|질문|어떻게')
TOPIC = re.compile(r'새출발|채무|부실|신복위|워크아웃|약정|실효|채권|압류|대출|연체|상환|납부|캠코|감면|보증')
MENTION = re.compile(r'\S+님|https?://|카톡|오픈채팅')


def extract(path):
    archive=json.loads(path.read_text())
    selected=[]
    excluded=0
    seen=set()
    for item in archive['records']:
        if item.get('collection_status') not in ('BODY_TEXT_CAPTURED','PREEXISTING_FULL_TEXT'):
            continue
        question=(item.get('body') or '').strip()
        if not QUESTION.search(question):
            continue
        for index,comment in enumerate(item.get('comments',[])):
            raw=comment.get('text','') if isinstance(comment,dict) else comment
            parts=raw.split('\n\n',1)
            if len(parts)!=2 or '작성자' in parts[0] or not DATE.search(parts[1]):
                continue
            answer=DATE.split(parts[1],1)[0].strip()
            if len(answer)<30 or not TOPIC.search(answer) or answer in seen:
                continue
            if PRIVATE.search(question+'\n'+answer) or MENTION.search(question+'\n'+answer):
                excluded+=1
                continue
            seen.add(answer)
            selected.append(dict(id=f"ccrs-{item['id']}-{index}",group=f"ccrs-{item['id']}",
                                 question=question,answer=answer,staff='카페스탭' in parts[0],
                                 review_status='pending'))
    return selected,excluded


if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('archive',type=Path)
    p.add_argument('--out',type=Path,default=Path('training/data/newstart/review_candidates.jsonl'))
    args=p.parse_args()
    rows,excluded=extract(args.archive)
    args.out.parent.mkdir(parents=True,exist_ok=True)
    write_rows(args.out,rows)
    print(dict(candidate_replies=len(rows),threads=len({r['group'] for r in rows}),privacy_or_mention_excluded=excluded))
