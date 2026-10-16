"""Read-only OS/runtime/service-path metadata only; no env, DB, logs or CLI execution."""
import json,platform,subprocess,shutil
from pathlib import Path
osinfo={}
for line in Path('/etc/os-release').read_text().splitlines():
    k,_,v=line.partition('=')
    if k in {'ID','VERSION_ID','PRETTY_NAME'}:osinfo[k]=v.strip('"')
def command(args):
    return subprocess.run(args,capture_output=True,text=True,timeout=10).stdout.strip()
props={}
for key in ['User','FragmentPath','DropInPaths','EnvironmentFiles','WorkingDirectory']:
    props[key]=command(['systemctl','show','f2r-app.service','-p',key,'--value'])
mem={}
for line in Path('/proc/meminfo').read_text().splitlines():
    k,_,v=line.partition(':')
    if k in {'MemTotal','MemAvailable','SwapTotal'}:mem[k]=v.strip()
disk=shutil.disk_usage('/opt')
print(json.dumps({'os':osinfo,'arch':platform.machine(),'glibc':command(['getconf','GNU_LIBC_VERSION']),'service_paths':props,'memory':mem,'opt_disk_bytes':{'total':disk.total,'free':disk.free}},ensure_ascii=False))
