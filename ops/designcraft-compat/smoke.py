"""New compatibility binary only; no Spring/AI/previous regression suite rerun."""
import hashlib,json,os,re,shutil,subprocess
from pathlib import Path
ROOT=Path('/w');OUT=ROOT/'compat-output';CLI=OUT/'package/bin/designcraft-cli'
def run(args,cwd=None,stdin=None):
    p=subprocess.run([str(x) for x in args],cwd=cwd,input=stdin,text=True,capture_output=True,timeout=90)
    if p.returncode:raise RuntimeError('compat-command-failed:'+str(p.returncode))
    return p.stdout
assert run(['getconf','GNU_LIBC_VERSION']).strip()=='glibc 2.34'
assert run([CLI,'--version']).strip()=='designcraft-cli 0.8.0'
ldd=run(['ldd',CLI]);assert 'not found' not in ldd
(OUT/'runtime-ldd.txt').write_text(ldd)
# readelf evidence is generated at build time; fail if newer symbols sneaked in.
symbols=sorted(set(re.findall(r'GLIBC_([0-9.]+)',(OUT/'elf-version-info.txt').read_text())),key=lambda s:tuple(map(int,s.split('.'))))
assert all(tuple(map(int,s.split('.')))<=(2,34) for s in symbols)
rows=[]
for code in ['ko-book-46','ko-textbook-a4','ko-workbook-b5']:
    d=OUT/('smoke-'+code);shutil.copytree(ROOT/'ops/designcraft/templates'/code,d)
    (d/'manuscript.txt').write_text('한글 조판 검증\n첫 번째 문장입니다.\n두 번째 문장입니다.\n')
    def cli(args,stdin=None):
        s=run([CLI,*args],d,stdin);return json.loads(s) if s.strip() else None
    imp=cli(['script','-','--in','template.designcraft','--save','job.designcraft'],'file.place={"path":"manuscript.txt","autoflow":true}\nedit.selectAll={}\nstyle.paragraph.apply={"name":"ko-body"}\n')
    story=imp['results'][0]['story'];before=cli(['run','--in','job.designcraft','--cmd','story.get='+json.dumps({'story':story})])['text'];assert '첫 번째 문장입니다.' in before
    cli(['run','--in','job.designcraft','--cmd','document.inspect'])
    cli(['run','--in','job.designcraft','--cmd','file.exportPdf={"path":"result.pdf","standard":"none","tagged":true}'])
    cli(['run','--in','job.designcraft','--page','0','--scale','1','--export','page.png'])
    # Existing 0.6.0-created native file, including its DOCX table, must reopen in the new build.
    if code=='ko-textbook-a4':
        shutil.copy(ROOT/'ops/designcraft-compat/fixtures/legacy-060.designcraft',d/'legacy.designcraft')
        legacy=cli(['run','--in','legacy.designcraft','--cmd','document.inspect'])
        assert legacy['stories']
        cli(['run','--in','legacy.designcraft','--cmd','file.exportPdf={"path":"legacy.pdf","standard":"none","tagged":true}'])
        cli(['run','--in','legacy.designcraft','--page','0','--scale','1','--export','legacy.png'])
    rows.append({'template':code,'import_inspect_preview_pdf':'PASS','font_files':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (d/'Document Fonts').glob('*.ttf')}})
out={'glibc':run(['getconf','GNU_LIBC_VERSION']).strip(),'version':run([CLI,'--version']).strip(),'binary_sha256':hashlib.sha256(CLI.read_bytes()).hexdigest(),'glibc_symbols':symbols,'runtime_dependencies':ldd.splitlines(),'rows':rows,'legacy_fixture_sha256':hashlib.sha256((ROOT/'ops/designcraft-compat/fixtures/legacy-060.designcraft').read_bytes()).hexdigest()}
(OUT/'compat-results.json').write_text(json.dumps(out,ensure_ascii=False,indent=2))
print('New glibc 2.34 binary: version/dependencies/3 Korean templates/legacy native reopen PASS')
