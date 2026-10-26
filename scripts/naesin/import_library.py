"""Resumable, opt-in original uploads. Never approves, publishes, deletes or initializes data."""
import argparse
import hashlib
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

RIGHTS = "Library 기존 자료 · 이용 권한 미확인 · 내부 검수용 · 자동 게시 금지"

def sha_file(path):
    h=hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda:f.read(1024*1024),b""):h.update(b)
    return h.hexdigest()

def target_guard(base, execute, production_authorized, deployment_sha):
    u=urllib.parse.urlsplit(base)
    if u.username or u.password or u.query or u.fragment:raise ValueError("Use a plain service base URL")
    local=u.scheme=="http" and u.hostname in {"localhost","127.0.0.1","::1"}
    production=u.scheme=="https" and u.hostname=="feel2real.co.kr" and u.path in {"","/"}
    if not local and not production:raise ValueError("Target must be isolated loopback or the approved Feel2Real origin")
    if execute and production and (not production_authorized or not deployment_sha or len(deployment_sha)!=40 or any(c not in "0123456789abcdef" for c in deployment_sha)):
        raise ValueError("Production execution requires separate deployment approval and a verified deployed SHA")
    return production

class Api:
    def __init__(self,base,token):
        self.base=base.rstrip("/");self.token=token
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self,*args,**kwargs):return None
        self.open=urllib.request.build_opener(NoRedirect()).open
    def request(self,path,division,project=None,payload=None,raw=None,query=None):
        q={"division":division,**(query or {})}
        if project:q["projectId"]=project
        url=self.base+"/api/naesin-production/"+path+"?"+urllib.parse.urlencode(q)
        headers={"Authorization":"Bearer "+self.token,"Origin":self.base}
        body=None
        if raw:
            headers.update({"Content-Type":"application/octet-stream","Content-Length":str(raw.stat().st_size)})
            body=raw.open("rb")
        elif payload is not None:
            headers["Content-Type"]="application/json";body=json.dumps(payload,ensure_ascii=False).encode()
        try:
            with self.open(urllib.request.Request(url,data=body,headers=headers,method="POST" if body is not None else "GET"),timeout=600) as r:
                return json.load(r)
        finally:
            if raw and body:body.close()
    def verify(self,file_id,division,project,expected):
        query=urllib.parse.urlencode({"division":division,"projectId":project})
        url=self.base+"/api/naesin-production/files/"+file_id+"/download?"+query
        digest=hashlib.sha256()
        with self.open(urllib.request.Request(url,headers={"Authorization":"Bearer "+self.token}),timeout=600) as r:
            for chunk in iter(lambda:r.read(1024*1024),b""):digest.update(chunk)
        if digest.hexdigest()!=expected:raise ValueError("Server original SHA mismatch")

def candidates(manifest):
    return [r for r in manifest["records"] if r["division"] in {"MIDDLE","HIGH"} and r["bytes_verified"] and r["status"]=="READY_FOR_ISOLATED_STAGE"]

def run(a):
    manifest=json.loads(Path(a.manifest).read_text());rows=candidates(manifest)
    production=target_guard(a.base_url,a.execute,a.production_authorized,a.deployment_sha)
    roots=Path(a.originals).resolve();summary={level:{"found":manifest["summary"][level]["found"],"registered":0,"reused":0,"duplicate_excluded":0,"failed":0,"verified":0} for level in ("MIDDLE","HIGH")}
    # Validate every eligible local original before the first network write.
    for r in rows:
        p=(roots/r["local_relative_path"]).resolve()
        if not p.is_relative_to(roots) or not p.is_file() or sha_file(p)!=r["sha256"]:raise ValueError("Local original integrity check failed: "+r["name"])
    if not a.execute:
        return {"dry_run":True,"production":production,"eligible":len(rows),"held":len(manifest["records"])-len(rows),"summary":summary,"network_writes":0}
    token=Path(a.token_file).read_text().strip() if a.token_file else os.getenv("F2R_IMPORT_BEARER_TOKEN","")
    if not token:raise ValueError("Authenticated operator credential is required; never place it in a manifest")
    api=Api(a.base_url,token);journal_path=Path(a.journal)
    journal=json.loads(journal_path.read_text()) if journal_path.exists() else {"files":{},"projects":{},"handoffs":{},"failures":{}}
    if journal.get("target_origin",a.base_url)!=a.base_url or journal.get("manifest_sha256",manifest["manifest_sha256"])!=manifest["manifest_sha256"]:
        raise ValueError("Use a separate journal for a different target or manifest")
    journal["target_origin"]=a.base_url;journal["manifest_sha256"]=manifest["manifest_sha256"]
    def checkpoint():
        tmp=journal_path.with_suffix(".tmp");tmp.parent.mkdir(parents=True,exist_ok=True);tmp.write_text(json.dumps(journal,ensure_ascii=False,indent=2));tmp.replace(journal_path)
    groups={}
    for r in rows:groups.setdefault((r["division"],r["school"],r["grade"],r["exam_range"]),[]).append(r)
    for key,materials in groups.items():
        division,school,grade,exam_range=key;group_hash=hashlib.sha256(json.dumps(key,ensure_ascii=False).encode()).hexdigest()[:24]
        payload={"title":("Library · "+" · ".join(x for x in [school,grade,exam_range] if x))[:150],"school":school,"grade":grade,"examRange":exam_range,"division":division,"importKey":"library-"+group_hash}
        project=api.request("projects",division,payload=payload)["id"];journal["projects"][group_hash]=project;checkpoint()
        originals_by_sha={};linked=[]
        for r in materials:
            identity=r["library_file_id"]+":"+project
            try:
                if identity in journal["files"]:
                    result=journal["files"][identity];api.verify(result["file_id"],division,project,r["sha256"]);summary[division]["reused"]+=1
                elif r["sha256"] in originals_by_sha:
                    result=originals_by_sha[r["sha256"]];summary[division]["duplicate_excluded"]+=1
                else:
                    source=json.dumps({"library_file_id":r["library_file_id"],"file_id":r["file_id"],"path":r["path"],"manifest":manifest["manifest_sha256"]},ensure_ascii=False)
                    if len(source)>1000:raise ValueError("Provenance field exceeds server limit")
                    result=api.request("raw-files",division,project,raw=roots/r["local_relative_path"],query={"filename":r["name"],"source":source,"rights":RIGHTS})
                    saved=result;result={"file_id":saved["file"]["id"],"revision_id":saved["revision"]["id"],"sha256":saved["file"]["sha256"]}
                    if result["sha256"]!=r["sha256"]:raise ValueError("Upload SHA mismatch")
                    api.verify(result["file_id"],division,project,r["sha256"])
                    summary[division]["registered"]+=not saved["duplicate"];summary[division]["reused"]+=saved["duplicate"]
                journal["files"][identity]=result;originals_by_sha[r["sha256"]]=result;summary[division]["verified"]+=1
                linked.append({"original":r,"registered":result});checkpoint()
            except Exception as error:
                summary[division]["failed"]+=1;journal["failures"][identity]={"name":r["name"],"error_type":type(error).__name__};checkpoint()
        # Bounded handoff records preserve aliases, proposed question/answer/version relationships.
        for at in range(0,len(linked),40):
            chunk=linked[at:at+40]
            chunk_hash=hashlib.sha256(json.dumps(chunk,ensure_ascii=False,sort_keys=True).encode()).hexdigest()[:12]
            item="library-handoff-"+manifest["manifest_sha256"][:16]+"-"+group_hash+"-"+str(at//40)+"-"+chunk_hash
            if item in journal["handoffs"]:continue
            title="Library 인계 "+manifest["manifest_sha256"][:16]+" "+group_hash+" "+str(at//40)+" "+chunk_hash
            state=api.request("state",division,project,query={"q":title,"offset":0})
            prior=next((r for r in state["revisions"] if r["item_key"]==item and r["label"]=="v1"),None)
            if not prior:
                task={"itemKey":item,"parentId":None,"label":"v1","category":"HANDOFF","title":title,"ai":"HUMAN","goal":"원본·정답·수정본 관계를 보존하여 Claude·Work에 인계","prompt":"2026-10-26 사용자 지시: 기존 자료 재사용, 원본 보존, 학교급 분리, 권한 미확인 자료 자동 게시 금지","model":"Library import; no model generation","tools":"SHA-256 / authenticated original upload / byte re-read","result":json.dumps(chunk,ensure_ascii=False),"errors":"학교급·버전 관계와 이용 권한은 별도 검수 필요","nextWork":"최신 원본과 인계 기록을 먼저 확인하고 유효한 추출·검수를 반복하지 않는다. 제안 관계를 대조하고 관리자 승인 전 게시하지 않는다.","status":"REVIEW","fileId":None,"sourceFileIds":list(dict.fromkeys(c["registered"]["file_id"] for c in chunk)),"referenceRevisionIds":list(dict.fromkeys(c["registered"]["revision_id"] for c in chunk))}
                prior=api.request("work",division,project,payload=task)
            reread=api.request("revisions/"+prior["id"],division,project)
            if reread["revision"]["id"]!=prior["id"]:raise ValueError("Handoff reread failed")
            journal["handoffs"][item]=prior["id"];checkpoint()
    return {"dry_run":False,"production":production,"deployment_sha":a.deployment_sha,"summary":summary,"held":len(manifest["records"])-len(rows),"approvals_or_publications":0}

def main():
    p=argparse.ArgumentParser();p.add_argument("--manifest",required=True);p.add_argument("--originals",required=True);p.add_argument("--base-url",default="http://127.0.0.1:8080");p.add_argument("--execute",action="store_true");p.add_argument("--production-authorized",action="store_true");p.add_argument("--deployment-sha");p.add_argument("--token-file");p.add_argument("--journal",default="naesin-import-journal.json");p.add_argument("--report",required=True)
    a=p.parse_args()
    try:result=run(a);Path(a.report).write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False))
    except Exception as e:print(type(e).__name__+": "+str(e),file=sys.stderr);sys.exit(1)
if __name__=="__main__":main()
