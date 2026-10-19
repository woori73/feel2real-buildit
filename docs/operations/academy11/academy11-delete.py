#!/usr/bin/env python3
"""Prepared executor. Default is read-only capture; execute requires reviewed manifest and exact digest."""
import argparse,hashlib,json,os,re,shlex,subprocess,sys
from pathlib import Path
EXPECTED={'academy_handle_histories':1,'academy_regions':1,'academy_subjects':1,'academy_target_groups':3,'academies':1}
ORDER=list(EXPECTED)

def require(ok,msg):
 if not ok:raise SystemExit(msg)
def ident(x):
 require(re.fullmatch(r'[A-Za-z0-9_]+',x),'Invalid identifier');return '`'+x+'`'
def digest(value):return hashlib.sha256(json.dumps(value,sort_keys=True,ensure_ascii=False,default=str).encode()).hexdigest()
def checked_manifest(value):
 require(value.get('account')=='425805163960' and value.get('target')==11,'Wrong scope')
 require(set(value.get('delete',{}))==set(EXPECTED),'Unexpected table scope')
 for table,count in EXPECTED.items():
  row=value['delete'][table]
  require(len(row['keys'])==count and len({json.dumps(k,sort_keys=True) for k in row['keys']})==count,'PK/count mismatch: '+table)
  require(row.get('count')==count and re.fullmatch('[a-f0-9]{64}',row.get('rows_sha256','')),'Invalid count/hash: '+table)
 return value

def validate_snapshot(current,manifest):
 require(current['delete']==manifest['delete'] and current['preserve']==manifest.get('preserve'),'PK/count/content changed; rollback')

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--manifest',required=True);parser.add_argument('--execute',action='store_true');parser.add_argument('--approved-sha256');args=parser.parse_args()
 require(os.geteuid()==0 and sys.stdin.isatty() and sys.stdout.isatty(),'Private EC2 root terminal required')
 os.umask(0o077)
 caller=subprocess.run(['aws','sts','get-caller-identity','--output','json'],capture_output=True,text=True)
 require(caller.returncode==0 and json.loads(caller.stdout).get('Account')=='425805163960','Wrong AWS account')
 try:import pymysql
 except ImportError:raise SystemExit('PyMySQL unavailable; stopped before DB access. No automatic installation.')
 cfg={}
 for line in Path('/etc/f2r/env').read_text().splitlines():
  if line.strip() and not line.lstrip().startswith('#') and '=' in line:
   k,v=line.split('=',1);values=shlex.split(v);cfg[k.strip()]=values[0] if values else ''
 db=cfg.get('DB_NAME','f2r_db');ident(db);require(db!='academycraft','Wrong DB')
 manifest=None
 if args.execute:
  raw=Path(args.manifest).read_bytes()
  require(args.approved_sha256==hashlib.sha256(raw).hexdigest(),'Approved manifest SHA mismatch')
  manifest=checked_manifest(json.loads(raw))
  require(manifest.get('review')=={'logical_json_shared_reviewed':True,'backup_and_selective_restore_verified':True,'complete_dependency_scope_reviewed':True},'Review/restore incomplete; execution blocked')
  require(manifest.get('backup_evidence') and manifest.get('restore_evidence'),'Missing recovery evidence')
  require(input('Final approved deletion of academy 11 only? Type DELETE-ACADEMY-11: ')=='DELETE-ACADEMY-11','Cancelled')
 conn=pymysql.connect(host=cfg['DB_HOST'],user=cfg.get('DB_USER') or cfg['DB_USERNAME'],password=cfg['DB_PASSWORD'],database=db,charset='utf8mb4',autocommit=False,connect_timeout=10,read_timeout=20,write_timeout=10)
 try:
  with conn.cursor() as cur:
   cur.execute('SET SESSION innodb_lock_wait_timeout=5')
   cur.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ')
   cur.execute('START TRANSACTION' if args.execute else 'START TRANSACTION READ ONLY')
   def rows(query,params=()):cur.execute(query,params);return cur.fetchall()
   def capture(table,where,params):
    pk=[r[0] for r in rows("SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND CONSTRAINT_NAME='PRIMARY' ORDER BY ORDINAL_POSITION",(table,))]
    require(pk,'No primary key: '+table)
    lock=' FOR UPDATE' if args.execute else ''
    cur.execute('SELECT * FROM '+ident(table)+' WHERE '+where+' ORDER BY '+','.join(map(ident,pk))+lock,params)
    names=[v[0] for v in cur.description];data=cur.fetchall()
    keys=[[row[names.index(k)] for k in pk] for row in data]
    return {'primary_key':pk,'keys':keys,'count':len(data),'rows_sha256':digest(data)}
   require(rows("SELECT COUNT(*) FROM academies a JOIN users u ON u.user_id=a.tutor_user_id WHERE a.academy_id=11 AND a.tutor_user_id=53 AND a.handle='edu-holic' AND u.login_id='edu-holic'")[0][0]==1,'Target identity mismatch')
   current={'account':'425805163960','target':11,'delete':{},'preserve':{}}
   for table,count in EXPECTED.items():
    current['delete'][table]=capture(table,'academy_id=11',())
    require(current['delete'][table]['count']==count,'Unexpected row count: '+table)
   # Protected identities, including ADMIN draft20; hashes stay in private manifest, never stdout.
   for key,table,where in [('users','users','user_id IN (4,53,54)'),('businesses','easy_businesses','business_id IN (1,2)'),('academies','academies','academy_id IN (12,20)')]:
    current['preserve'][key]=capture(table,where,())
   require(current['preserve']['users']['count']==3 and current['preserve']['businesses']['count']==2 and current['preserve']['academies']['count']==2,'Protected identity missing')
   # Preserve all directly scoped12 content rows, with lock/digest comparison in execution.
   tables=[r[0] for r in rows("SELECT c.TABLE_NAME FROM information_schema.COLUMNS c JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME WHERE c.TABLE_SCHEMA=DATABASE() AND c.COLUMN_NAME='academy_id' AND t.TABLE_TYPE='BASE TABLE'")]
   for table in tables:
    current['preserve']['academy12:'+table]=capture(table,'academy_id=12',())
    if table not in EXPECTED:require(rows('SELECT COUNT(*) FROM '+ident(table)+' WHERE academy_id=11')[0][0]==0,'Unlisted direct rows: '+table)
   triggers=rows('SELECT EVENT_OBJECT_TABLE FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()')
   require(not any(r[0] in EXPECTED for r in triggers),'Target trigger requires separate review')
   # Incoming FK to any deleted row must either belong to the five reviewed tables, or have zero matches.
   fks=rows("SELECT TABLE_SCHEMA,TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IN ('academies','academy_handle_histories','academy_regions','academy_subjects','academy_target_groups') ORDER BY TABLE_SCHEMA,TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION")
   groups={}
   for schema,child,key,col,parent,ref in fks:
    require(schema==db,'Cross-schema incoming FK blocked')
    groups.setdefault((child,key,parent),[]).append((col,ref))
   for (child,key,parent),pairs in groups.items():
    join=' AND '.join('c.'+ident(c)+'=p.'+ident(r) for c,r in pairs)
    predicate='p.academy_id=11'+(' AND c.academy_id<>11' if child in EXPECTED else '')
    require(rows('SELECT COUNT(*) FROM '+ident(child)+' c JOIN '+ident(parent)+' p ON '+join+' WHERE '+predicate)[0][0]==0,'Unreviewed/shared FK rows: '+child)
   if not args.execute:
    current.update(review={'logical_json_shared_reviewed':False,'backup_and_selective_restore_verified':False,'complete_dependency_scope_reviewed':False},backup_evidence=None,restore_evidence=None)
    checked_manifest(current)
    with Path(args.manifest).open('x') as out:json.dump(current,out,ensure_ascii=False,indent=2,default=str)
    Path(args.manifest).chmod(0o600)
    print('CAPTURED: private PK/count/hash manifest. No DB changes. Execution blocked pending review and final approval.')
   else:
    validate_snapshot(current,manifest)
    for table in ORDER:
     pk=current['delete'][table]['primary_key']
     clause=' OR '.join('('+' AND '.join(ident(k)+'=%s' for k in pk)+')' for _ in current['delete'][table]['keys'])
     params=tuple(v for key in current['delete'][table]['keys'] for v in key)
     cur.execute('DELETE FROM '+ident(table)+' WHERE academy_id=11 AND ('+clause+')',params)
     require(cur.rowcount==EXPECTED[table],'Affected row mismatch; rollback')
    for key,before in current['preserve'].items():
     if key=='users':after=capture('users','user_id IN (4,53,54)',())
     elif key=='businesses':after=capture('easy_businesses','business_id IN (1,2)',())
     elif key=='academies':after=capture('academies','academy_id IN (12,20)',())
     else:after=capture(key.split(':',1)[1],'academy_id=12',())
     require(after==before,'Protected content changed; rollback')
    require(rows('SELECT COUNT(*) FROM academies WHERE academy_id=11')[0][0]==0,'Target remains; rollback')
    conn.commit();print('COMMITTED: approved seven rows only; protected row digests unchanged. Verify live homepage separately.')
 finally:
  conn.rollback();conn.close()
if __name__=='__main__':
 try:main()
 except Exception:raise SystemExit('Stopped; transaction rolled back if connected. Credentials and database errors suppressed.')
