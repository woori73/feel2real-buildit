import json

with open('C:/feel2real-buildit/data/newstart-cafe/gamian/structured/cases.json', 'r', encoding='utf-8') as f:
    cases = json.load(f)

for c in cases:
    aid = c['article_id']
    frr = c.get('final_reduction_rate')
    arrf = c.get('actual_reduction_rate_from_followup')
    pred = c.get('predicted_reduction_rate')
    if frr or arrf:
        print(f'{aid}: FRR={frr} | ARRF={arrf} | PRED={pred}')