#!/usr/bin/env python3
"""Local/CI adapters only. A real ClamAV engine with a SAFE TEST signature, not production definitions."""
import os,shlex,subprocess,sys
from pathlib import Path
root=Path(sys.argv[1]).resolve();root.mkdir(parents=True,exist_ok=True)
for name in ('private','definitions'):
 (root/name).mkdir(exist_ok=True);(root/name).chmod(0o700)
eicar=br'X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*'
(root/'definitions/test.ndb').write_text('F2R.SafeEicarTest:0:*:'+eicar.hex()+'\n')
image='clamav/clamav@sha256:a01a78acf14e356f9d95c0eeb640ac9fa48a98a59933dea877f5a4d01573a0eb'
env=os.environ.copy()
for k in ('DOCKER_HOST','DOCKER_CONTEXT','DOCKER_TLS','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH'):env.pop(k,None)
docker=['docker','--host=unix:///var/run/docker.sock']
subprocess.run(docker+['pull',image],env=env,check=True,stdout=subprocess.DEVNULL)
cmd=['env','-u','DOCKER_HOST','-u','DOCKER_CONTEXT','-u','DOCKER_TLS','-u','DOCKER_TLS_VERIFY','-u','DOCKER_CERT_PATH']+docker+['run','--rm','--network','none','--entrypoint','clamscan','--mount',f'type=bind,src={root}/definitions,dst=/definitions,readonly','--mount',f'type=bind,src={root}/private,dst={root}/private,readonly',image,'--database=/definitions']
(root/'scanner').write_text('#!/bin/sh\nexec '+shlex.join(cmd)+' "$@"\n');(root/'scanner').chmod(0o700)
print('Prepared isolated ClamAV engine; production signature freshness is NOT verified.')
