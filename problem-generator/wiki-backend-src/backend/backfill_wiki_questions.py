"""Wiki legacy question canonical backfill. 기본 실행은 read-only dry-run이다."""
import argparse
import json

import main


def run():
    parser=argparse.ArgumentParser(description='legacy Wiki Question을 wiki-question-v1로 분석/변환')
    parser.add_argument('--apply',action='store_true',help='dry-run 결과를 확인한 뒤 실제 DB에 적용')
    args=parser.parse_args()
    result=main.legacy_canonical_backfill(dry_run=not args.apply)
    print(json.dumps({'mode':'apply' if args.apply else 'dry-run',**result},ensure_ascii=False,indent=2))


if __name__=='__main__': run()
