#!/usr/bin/env python3
"""Read-only deletion planning. Contains no deletion or backup mutation commands."""
import collections,json,os,re,shlex,subprocess,sys,tempfile
from pathlib import Path

def require(ok,msg):
 if not ok:raise SystemExit(msg)
def env(path):
 out={}
 for line in Path(path).read_text().splitlines():
  if line.strip() and not line.lstrip().startswith('#') and '=' in line:
   k,v=line.split('=',1);parts=shlex.split(v);out[k.strip()]=parts[0] if parts else ''
 return out
def identifier(value):
 require(re.fullmatch(r'[A-Za-z0-9_]+',value),'Unexpected SQL identifier; stopped')
 return '`'+value+'`'
def scoped(table,academy,seeds,edges,path=(),depth=0):
 if table in path:raise ValueError('FK cycle: '+' -> '.join(path+(table,)))
 alias='s'+str(depth);parts=[alias+'.`academy_id`='+str(academy)] if table in seeds else []
 for parent,pairs in edges.get(table,[]):
  pred=scoped(parent,academy,seeds,edges,path+(table,),depth+1)
  if pred:
   parent_alias='s'+str(depth+1)
   links=' AND '.join(alias+'.'+identifier(c)+'='+parent_alias+'.'+identifier(r) for c,r in pairs)
   parts.append('EXISTS (SELECT 1 FROM '+identifier(parent)+' '+parent_alias+' WHERE '+links+' AND ('+pred+'))')
 result=' OR '.join('('+p+')' for p in parts)
 if len(result)>100000:raise ValueError('Dependency query too complex: '+table)
 return result

def main():
 require(os.geteuid()==0 and sys.stdin.isatty() and sys.stdout.isatty(),'Run sudo in private NEW EC2 SSM terminal')
 os.umask(0o077)
 identity=subprocess.run(['aws','sts','get-caller-identity','--output','json'],capture_output=True,text=True)
 require(identity.returncode==0 and json.loads(identity.stdout).get('Account')=='425805163960','New AWS account check failed; stopped')
 settings=env('/etc/f2r/env');db=settings.get('DB_NAME','f2r_db');identifier(db)
 require(db!='academycraft','Unexpected BuildIt database')
 with tempfile.TemporaryDirectory(prefix='academy11-readonly-') as folder:
  cnf=Path(folder)/'mysql.cnf'
  def quote(v):return '"'+v.replace('\\','\\\\').replace('"','\\"').replace('\n','\\n')+'"'
  cnf.write_text('[client]\nhost='+quote(settings['DB_HOST'])+'\nuser='+quote(settings.get('DB_USER') or settings['DB_USERNAME'])+'\npassword='+quote(settings['DB_PASSWORD'])+'\ndefault-character-set=utf8mb4\nconnect-timeout=10\n');cnf.chmod(0o600)
  def read(query):
   require(query.startswith('SELECT '),'Non-SELECT rejected')
   result=subprocess.run(['mysql','--defaults-extra-file='+str(cnf),'--batch','--skip-column-names',db],input='START TRANSACTION READ ONLY;\n'+query+'\nROLLBACK;',capture_output=True,text=True)
   require(result.returncode==0,'Read-only query failed; stop planning. Credentials/SQL errors suppressed.')
   return result.stdout.strip()
  def report(label,query):
   print(label,flush=True);print(read(query) or '(none)',flush=True)
  report('ACADEMIES: id, owner_user_id, login, slug, handle, status',"SELECT a.academy_id,a.tutor_user_id,u.login_id,a.slug,a.handle,a.status FROM academies a LEFT JOIN users u ON u.user_id=a.tutor_user_id WHERE a.academy_id IN (11,12) ORDER BY a.academy_id;")
  require(read("SELECT COUNT(*) FROM academies a JOIN users u ON u.user_id=a.tutor_user_id WHERE a.academy_id=11 AND a.tutor_user_id=53 AND u.login_id='edu-holic' AND a.handle='edu-holic';")=='1','ID 11 fingerprint mismatch; stopped')
  report('PRESERVE users: user_id, login_id, role',"SELECT user_id,login_id,role FROM users WHERE user_id IN (4,53,54) ORDER BY user_id;")
  report('PRESERVE businesses: id, code, name, active','SELECT business_id,code,name,active FROM easy_businesses WHERE business_id IN (1,2) ORDER BY business_id;')
  report('BUSINESS LINKS: academy_id, business_id','SELECT academy_id,business_id FROM academy_easy_business_links WHERE academy_id IN (11,12) ORDER BY academy_id,business_id;')
  raw=read("SELECT c.TABLE_NAME FROM information_schema.COLUMNS c JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME WHERE c.TABLE_SCHEMA=DATABASE() AND c.COLUMN_NAME='academy_id' AND t.TABLE_TYPE='BASE TABLE' ORDER BY c.TABLE_NAME;")
  seeds=set(raw.splitlines());edges=collections.defaultdict(list);groups=collections.defaultdict(list)
  raw=read("SELECT k.TABLE_SCHEMA,k.TABLE_NAME,k.CONSTRAINT_NAME,k.COLUMN_NAME,k.REFERENCED_TABLE_SCHEMA,k.REFERENCED_TABLE_NAME,k.REFERENCED_COLUMN_NAME,r.DELETE_RULE FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.TABLE_NAME=k.TABLE_NAME AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.REFERENCED_TABLE_SCHEMA=DATABASE() ORDER BY k.TABLE_SCHEMA,k.TABLE_NAME,k.CONSTRAINT_NAME,k.ORDINAL_POSITION;")
  print('FK METADATA: child_schema, table, constraint, column, parent_schema, table, column, delete_rule',flush=True);print(raw or '(none)',flush=True)
  for row in raw.splitlines():
   schema,child,key,col,ps,parent,ref,rule=row.split('\t')
   require(schema==db,'Cross-schema incoming FK found; stop for manual review')
   groups[(child,key,parent)].append((col,ref))
  for (child,key,parent),pairs in groups.items():edges[child].append((parent,pairs))
  # Keep only tables that can reach a seed through parent references; avoids unrelated FK cycles.
  reachable=set(seeds)
  while True:
   expanded=reachable|{c for c,parents in edges.items() if any(p in reachable for p,_ in parents)}
   if expanded==reachable:break
   reachable=expanded
  edges={c:[(p,pairs) for p,pairs in parents if p in reachable] for c,parents in edges.items() if c in reachable}
  report('TRIGGERS: name, event, table, timing (definition requires review if relevant)',
         'SELECT TRIGGER_NAME,EVENT_MANIPULATION,EVENT_OBJECT_TABLE,ACTION_TIMING FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() ORDER BY EVENT_OBJECT_TABLE,TRIGGER_NAME;')
  for table in sorted(reachable):
   try:p11=scoped(table,11,seeds,edges);p12=scoped(table,12,seeds,edges)
   except ValueError as error:print('BLOCKED:',str(error),flush=True);continue
   foreign='COALESCE(SUM(('+p11+') AND s0.academy_id<>11),0)' if table in seeds else '0'
   report('SCOPE COUNTS '+table+' (from_11, from_12, overlap_SHARED_REVIEW, other_tenant_via_11)',
          'SELECT /*+ MAX_EXECUTION_TIME(3000) */ COALESCE(SUM('+p11+'),0),COALESCE(SUM('+p12+'),0),COALESCE(SUM(('+p11+') AND ('+p12+')),0),'+foreign+' FROM '+identifier(table)+' s0 WHERE ('+p11+') OR ('+p12+');')
  instances=subprocess.run(['aws','rds','describe-db-instances','--region','ap-northeast-2','--output','json'],capture_output=True,text=True)
  if instances.returncode:
   print('BACKUP METADATA BLOCKED: RDS Describe permission unavailable; no permission changes attempted.',flush=True)
  else:
   matches=[x for x in json.loads(instances.stdout).get('DBInstances',[]) if x.get('Endpoint',{}).get('Address')==settings['DB_HOST']]
   require(len(matches)==1,'RDS host match failed; backup unverified')
   instance=matches[0];print('RDS BACKUP:',json.dumps({k:instance.get(k) for k in ('DBInstanceIdentifier','DBInstanceStatus','BackupRetentionPeriod','LatestRestorableTime')},default=str),flush=True)
   snapshots=subprocess.run(['aws','rds','describe-db-snapshots','--db-instance-identifier',instance['DBInstanceIdentifier'],'--region','ap-northeast-2','--output','json'],capture_output=True,text=True)
   if snapshots.returncode:print('SNAPSHOT metadata unavailable; backup unverified.',flush=True)
   else:
    ready=[x for x in json.loads(snapshots.stdout).get('DBSnapshots',[]) if x.get('Status')=='available']
    ready.sort(key=lambda x:x.get('SnapshotCreateTime',''),reverse=True)
    print('LATEST AVAILABLE SNAPSHOTS:',json.dumps([{k:x.get(k) for k in ('DBSnapshotIdentifier','SnapshotCreateTime','Status')} for x in ready[:2]]),flush=True)
  print('RECOVERY: metadata alone is not restore verification; isolated restoration path/evidence required before delete.',flush=True)
  print('BLOCKED FOR DELETE: inspect FK/count output, logical/JSON references, shared storage and backup evidence; obtain final execution approval. No mutation performed.',flush=True)
if __name__=='__main__':
 try:main()
 except (KeyError,ValueError,OSError):raise SystemExit('Preflight stopped. No credentials printed; no mutation performed.')
