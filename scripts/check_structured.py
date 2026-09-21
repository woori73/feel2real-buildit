import json

with open('C:/feel2real-buildit/data/newstart-cafe/gamian/structured/cases.json', 'r', encoding='utf-8') as f:
    cases = json.load(f)

for c in cases:
    aid = c['article_id']
    afr = c.get('author_followup_result')
    if afr:
        print(f'{aid}: AFR={afr[:120]}')
    mgr = c.get('manager_prediction', {})
    if mgr.get('predicted_rate'):
        print(f'{aid} MGR={mgr["predicted_rate"]}')
    pred = c.get('predicted_reduction_rate')
    if pred:
        print(f'{aid} PRED={pred}')
    temp = c.get('temp_reduction_rate')
    if temp:
        print(f'{aid} TEMP={temp}')
    final = c.get('final_reduction_rate')
    if final:
        print(f'{aid} FINAL={final}')