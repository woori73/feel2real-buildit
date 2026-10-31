#!/usr/bin/env python3
"""Prepare a new migration only after a real read-only schema report. Never apply SQL."""
import argparse, json, re, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def prepare(report, output):
    schema=report.get('schema',{})
    if not report.get('readOnly') or schema.get('status')!='ok' or not schema.get('history'):
        raise ValueError('Successful production read-only schema evidence is required')
    if any(not h.get('success') for h in schema['history']):raise ValueError('Resolve failed Flyway history before preparing installation')
    if schema.get('presentTables') or schema.get('columns') or schema.get('indexes'):
        raise ValueError('Existing intake tables require schema comparison/adoption; refusing create or overwrite')
    if 'presentTables' not in schema:raise ValueError('Intake table absence was not checked')
    files=list((ROOT/'src/main/resources/db/migration').glob('V*__*.sql'))
    versions=[int(re.match(r'V(\d+)__', f.name)[1]) for f in files]
    applied=[h['version'] for h in schema['history'] if h.get('version') is not None]
    if not applied or max(applied)!=max(versions):raise ValueError('Production/repository migration heads differ; reconcile first')
    candidate=output/f'V{max(versions)+1}__exam_intake.sql'
    if candidate.exists():raise ValueError('Candidate already exists; refusing overwrite')
    output.mkdir(parents=True,exist_ok=True)
    sql=(ROOT/'docs/home-exam/proposed-schema.sql').read_text().split('\n',1)[1]
    with candidate.open('x') as f:f.write('-- Additive intake tables; prepared from verified empty intake schema.\n'+sql)
    return candidate

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('evidence',type=Path);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    try:print(prepare(json.loads(a.evidence.read_text()),a.output))
    except (ValueError,OSError,KeyError) as e:print(str(e),file=sys.stderr);sys.exit(2)
