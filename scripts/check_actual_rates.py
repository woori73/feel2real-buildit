import json

with open('C:/feel2real-buildit/data/newstart-cafe/gamian/structured/cases.json', 'r', encoding='utf-8') as f:
    cases = json.load(f)

for c in cases:
    aid = c['article_id']
    afr = c.get('author_followup_result')
    arrf = c.get('actual_reduction_rate_from_followup')
    frr = c.get('final_reduction_rate')
    if afr or arrf:
        print(f'{aid}: AFR={afr[:80] if afr else "None"} | ARRF={arrf} | FRR={frr}')