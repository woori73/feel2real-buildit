"""One synthetic PDF operation for resource/permission evidence, not an E2E rerun."""
import hashlib,json,os,subprocess,tempfile,time
from pathlib import Path
root=Path('/w/compat-output');binary=root/'package/bin/designcraft-cli';out=root/'benchmark';out.mkdir(exist_ok=True);out.chmod(0o777)
# A resumed artifact owns old output as the runner; create a fresh output for UID 65534.
(out/'unprivileged.pdf').unlink(missing_ok=True)
def drop_privileges():
    os.setgroups([]);os.setgid(65534);os.setuid(65534)
def run(args,cwd):
    with tempfile.TemporaryFile(mode='w+t') as stdout,tempfile.TemporaryFile(mode='w+t') as stderr:
        p=subprocess.Popen([str(x) for x in args],cwd=cwd,preexec_fn=drop_privileges,stdout=stdout,stderr=stderr)
        deadline=time.monotonic()+90
        while True:
            pid,status,usage=os.wait4(p.pid,os.WNOHANG)
            if pid:break
            if time.monotonic()>deadline:
                p.kill();os.wait4(p.pid,0);raise RuntimeError('measurement-timeout')
            time.sleep(.01)
        p.returncode=os.waitstatus_to_exitcode(status)
        assert p.returncode==0,('unprivileged-cli-exit',p.returncode)
        stdout.seek(0)
        return stdout.read(),usage.ru_maxrss
started=time.monotonic()
version,version_rss=run([binary,'--version'],out);version=version.strip();assert version=='designcraft-cli 0.8.0'
_,export_rss=run([binary,'run','--in','legacy.designcraft','--cmd','file.exportPdf='+json.dumps({'path':str(out/'unprivileged.pdf'),'standard':'none','tagged':True})],root/'smoke-ko-textbook-a4')
result={'uid':65534,'gid':65534,'glibc':subprocess.check_output(['getconf','GNU_LIBC_VERSION'],text=True).strip(),'version':version,'binary_sha256':hashlib.sha256(binary.read_bytes()).hexdigest(),'binary_bytes':binary.stat().st_size,'elapsed_seconds':round(time.monotonic()-started,3),'peak_cli_rss_kib':max(version_rss,export_rss),'export_peak_rss_kib':export_rss,'measurement':'os.wait4 per CLI child; excludes inherited shell/dnf child usage','fixture':'synthetic legacy 0.6 native with Korean paragraphs/table','operation':'file.exportPdf standard=none','result':'PASS'}
(root/'benchmark.json').write_text(json.dumps(result,indent=2));out.chmod(0o755)
print(json.dumps(result))
