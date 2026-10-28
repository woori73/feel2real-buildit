"""Fixed SSM client. No arbitrary command input. Never logs credentials/stderr bodies."""
import json, os, subprocess, sys, time
INSTANCE = 'i-0414de33d2803c723'
DOCUMENTS = {'audit':'F2R-914-MediaAudit','backup':'F2R-914-AutoBackup','verify':'F2R-914-AutoRestore','deploy':'F2R-914-AutoDeploy','restore':'F2R-914-AutoRestore'}


def aws(*args):
    p=subprocess.run(['aws','--region','ap-northeast-2',*args,'--output','json'],capture_output=True,text=True)
    if p.returncode: raise RuntimeError('AWS request failed: '+args[0]+'/'+args[1]+' (credentials and response body suppressed)')
    return json.loads(p.stdout)


def execute(operation):
    params={}
    if operation in ('verify','restore'):
        params={'Operation':[operation], 'ReleaseRunId':[os.environ.get('RELEASE_RUN_ID','0')]}
    if operation=='deploy':
        params={key:[os.environ[name]] for key,name in [('ReleaseSha','RELEASE_SHA'),('PatchSha256','PATCH_SHA256'),('ReleaseRunId','RELEASE_RUN_ID')]}
    result=aws('ssm','send-command','--instance-ids',INSTANCE,'--document-name',DOCUMENTS[operation], '--document-version','1','--timeout-seconds','60','--parameters',json.dumps(params))
    cid=result['Command']['CommandId']
    print(json.dumps({'operation':operation,'command_id':cid}),flush=True)
    deadline=time.monotonic()+1500
    while time.monotonic()<deadline:
        try: out=aws('ssm','get-command-invocation','--command-id',cid,'--instance-id',INSTANCE)
        except RuntimeError: time.sleep(5); continue
        if out['Status'] in ('Success','Failed','Cancelled','TimedOut','Cancelling'):
            os.makedirs('release-evidence',exist_ok=True)
            evidence={'operation':operation,'command_id':cid,'status':out['Status'],'response_code':out.get('ResponseCode'),'output':out.get('StandardOutputContent','')}
            with open('release-evidence/'+operation+'.json','w') as f: json.dump(evidence,f,indent=2)
            print(json.dumps({'operation':operation,'status':out['Status'],'response_code':out.get('ResponseCode')}))
            if out['Status']!='Success' or out.get('ResponseCode')!=0: raise RuntimeError('Fixed SSM operation failed; inspect safe evidence artifact')
            return evidence
        time.sleep(5)
    raise RuntimeError('SSM completion unknown; do not assume success. Recovery waits for the host lock.')

if __name__=='__main__':
    try: execute(sys.argv[1])
    except Exception as e:
        print(str(e) if isinstance(e,RuntimeError) else type(e).__name__);sys.exit(1)
