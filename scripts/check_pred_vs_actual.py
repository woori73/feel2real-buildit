import json

with open('C:/feel2real-buildit/data/newstart-cafe/gamian/structured/cases.json', 'r', encoding='utf-8') as f:
    cases = json.load(f)

for c in cases:
    aid = c['article_id']
    mgr = c.get('manager_prediction', {})
    pred = mgr.get('predicted_rate')
    frr = c.get('final_reduction_rate')
    arrf = c.get('actual_reduction_rate_from_followup')
    pv_actual = c.get('prediction_vs_actual')
    
    if frr or arrf:
        print(f'{aid}: PRED={pred} | FRR={c.get("final_reduction_rate")} | ARRF={c.get("actual_reduction_rate_from_followup")} | PVS={pv_actual}')