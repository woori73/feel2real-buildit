import os
from github_release import repo
run=os.environ['SOURCE_RUN_ID']
assert run.isdigit()
jobs=repo('actions/runs/'+run+'/jobs?per_page=100')['jobs']
attempted=any(s['name']=='Deploy verified resource-only JAR' and s.get('started_at') and s.get('conclusion')!='skipped' for j in jobs if j['name']=='deploy' for s in j.get('steps',[]))
recovered=any(s['name']=="Restore verified df425588 (only this run's deployed JAR)" and s.get('conclusion')=='success' for j in jobs if j['name']=='rollback' for s in j.get('steps',[]))
with open(os.environ['GITHUB_OUTPUT'],'a') as f:f.write('required='+str(attempted and not recovered).lower()+'\n')
