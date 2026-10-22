#!/usr/bin/env python3
"""Compare two official SDK sessions with actual stored project memory, including the full Git SHA."""
import asyncio,hashlib,importlib.util,json,os
from pathlib import Path
import httpx,httpx2
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client
p=Path(__file__).with_name('media-protocol-qa.py');spec=importlib.util.spec_from_file_location('media',p);media=importlib.util.module_from_spec(spec);spec.loader.exec_module(media)
with httpx.Client(base_url=media.BASE,trust_env=False,headers={'Cookie':media.fixture['adminCookie']}) as c:
 response=c.get('/api/studio/memory');response.raise_for_status();expected=response.json()
async def restore(name):
 token=media.connect('https://claude.ai/api/mcp/auth_callback',name,False)
 async with httpx2.AsyncClient(headers={'Authorization':'Bearer '+token},trust_env=False) as http:
  async with streamable_http_client(media.BASE+'/mcp',http_client=http) as (rx,tx):
   async with ClientSession(rx,tx) as client:
    await client.initialize();r=await client.call_tool('read_studio',{'endpointId':'GET /api/studio/memory','query':{'projectId':'ai-agent-part1'}});assert not r.is_error;v=json.loads(r.content[0].text)['data']
    assert v['memory']['commitSha']==expected['memory']['commitSha'],'Git SHA must survive PII redaction exactly'
    assert v['revisionId']==expected['revisionId'] and v['memory']['pr']==expected['memory']['pr'];assert len(v['files'])==4
    denied=await client.call_tool('read_studio',{'endpointId':'POST /api/studio/memory'});assert denied.is_error
    return {'revisionId':v['revisionId'],'snapshotSha256':hashlib.sha256(json.dumps(v,sort_keys=True,ensure_ascii=False).encode()).hexdigest(),'pr':v['memory']['pr'],'commitSha':v['memory']['commitSha'],'linkedFiles':len(v['files']),'writeDenied':True}
async def main():
 a=await restore('Final local handoff SDK A');b=await restore('Final local handoff SDK B');assert a==b
 report={'testType':'Two independent local official SDK sessions against persisted memory, not product accounts','sessionA':a,'sessionB':b,'actualProductConnection':'NOT TESTED','externalAiCalls':0}
 target=Path(os.environ.get('F2R_HANDOFF_RECEIPT',str(media.ROOT/'docs/studio-collaboration/final-handoff-results.json')));target.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
asyncio.run(main())
