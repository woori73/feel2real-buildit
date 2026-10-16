"""One synthetic PDF operation for resource/permission evidence, not an E2E rerun."""
import hashlib,json,os,resource,subprocess,time
from pathlib import Path
root=Path('/w/compat-output');binary=root/'package/bin/designcraft-cli';out=root/'benchmark';out.mkdir(exist_ok=True);out.chmod(0o777)
def drop_privileges():
    os.setgroups([]);os.setgid(65534);os.setuid(65534)
def run(args,cwd):
    p=subprocess.run([str(x) for x in args],cwd=cwd,preexec_fn=drop_privileges,capture_output=True,text=True,timeout=90)
    assert p.returncode==0,('unprivileged-cli-exit',p.returncode)
    return p
started=time.monotonic()
version=run([binary,'--version'],out).stdout.strip();assert version=='designcraft-cli 0.8.0'
run([binary,'run','--in','legacy.designcraft','--cmd','file.exportPdf='+json.dumps({'path':str(out/'unprivileged.pdf'),'standard':'none','tagged':True})],root/'smoke-ko-textbook-a4')
result={'uid':65534,'gid':65534,'glibc':subprocess.check_output(['getconf','GNU_LIBC_VERSION'],text=True).strip(),'version':version,'binary_sha256':hashlib.sha256(binary.read_bytes()).hexdigest(),'binary_bytes':binary.stat().st_size,'elapsed_seconds':round(time.monotonic()-started,3),'peak_child_rss_kib':resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss,'fixture':'synthetic legacy 0.6 native with Korean paragraphs/table','operation':'file.exportPdf standard=none','result':'PASS'}
(root/'benchmark.json').write_text(json.dumps(result,indent=2));out.chmod(0o755)
print(json.dumps(result))
