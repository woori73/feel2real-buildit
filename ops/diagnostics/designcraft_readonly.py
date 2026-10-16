"""Read-only preflight: hashes/configured path/process counts. No CLI/document/DB execution."""
import hashlib,json,os,re,subprocess,time
from pathlib import Path
def textcmd(a):
 return subprocess.run(a,capture_output=True,text=True,timeout=10).stdout.strip()
def digest(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
pid=int(textcmd(['systemctl','show','f2r-app.service','-p','MainPID','--value']) or 0)
config={}
if pid:
 for item in Path('/proc/%d/environ'%pid).read_bytes().split(b'\0'):
  k,sep,v=item.partition(b'=')
  if k==b'PUBLISHING_CLI_PATH':config['cli']=v.decode()
 for arg in Path('/proc/%d/cmdline'%pid).read_bytes().split(b'\0'):
  arg=arg.decode(errors='replace')
  for prefix in ['--publishing.cli-path=','-Dpublishing.cli-path=']:
   if arg.startswith(prefix):config['cli']=arg[len(prefix):]
cli=Path(config.get('cli','/opt/designcraft/designcraft-cli'))
def processes():
 found=[]
 for proc in Path('/proc').iterdir():
  if not proc.name.isdigit():continue
  try:
   name=(proc/'comm').read_text().strip()
   exe=os.readlink(proc/'exe')
   if name.startswith('designcraft') or Path(exe.replace(' (deleted)','')).name=='designcraft-cli':
    found.append({'pid':int(proc.name),'name':name,'exe':exe})
  except (OSError,ValueError):pass
 return found
samples=[]
for i in range(3):
 samples.append({'at_utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'processes':processes()})
 if i<2:time.sleep(2)
print(json.dumps({'checked_at_utc':samples[-1]['at_utc'],'service_running':pid>0,'service_user':textcmd(['systemctl','show','f2r-app.service','-p','User','--value']),'configured_cli':str(cli),'path_source':'explicit publishing env/args or Spring default; no unrelated values retained','resolved_cli':str(cli.resolve()),'cli_exists':cli.is_file(),'cli_sha256':digest(cli) if cli.is_file() else None,'cli_mode':oct(cli.stat().st_mode&0o777) if cli.exists() else None,'process_samples':samples,'queue_status':'not queried: no DB credentials or user documents accessed','new_work_gate':'no gate installed or activated by this probe'}))
