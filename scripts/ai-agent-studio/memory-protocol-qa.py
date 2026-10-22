#!/usr/bin/env python3
"""Restore shared work state in two independently authenticated local MCP clients."""
import asyncio,json,hashlib,importlib.util
from pathlib import Path
import httpx2
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client
spec=importlib.util.spec_from_file_location('studio_media_qa',Path(__file__).with_name('media-protocol-qa.py'))
media=importlib.util.module_from_spec(spec);spec.loader.exec_module(media)
async def restore(token,capture=False):
 async with httpx2.AsyncClient(headers={'Authorization':'Bearer '+token},trust_env=False) as http:
  async with streamable_http_client(media.BASE+'/mcp',http_client=http) as (rx,tx):
   async with ClientSession(rx,tx) as session:
    await session.initialize()
    if capture:
     inventory=[]
     for offset in range(0,3000,100):
      page=await session.call_tool('list_catalog',{'offset':offset,'limit':100});result=json.loads(page.content[0].text);inventory+=result['items']
      if len(inventory)>=result['total']:break
     Path('/tmp/f2r-route-inventory.json').write_text(json.dumps(inventory,ensure_ascii=False,indent=2)+'\n')
    response=await session.call_tool('read_studio',{'endpointId':'GET /api/studio/memory'});assert not response.is_error
    data=json.loads(response.content[0].text)['data'];assert data['revisionId'];assert len(data['files'])==4
    assert '536' in data['memory']['lastDecision'];assert data['memory']['pr']==866;assert '미확보' in data['memory']['openIssues']
    rejected=await session.call_tool('read_studio',{'endpointId':'POST /api/studio/memory'});assert rejected.is_error
    return {'revisionId':data['revisionId'],'memorySha256':hashlib.sha256(json.dumps(data,sort_keys=True,ensure_ascii=False).encode()).hexdigest(),'linkedFiles':len(data['files']),'writeDenied':True}
async def main():
 a=await restore(media.connect('https://claude.ai/api/mcp/auth_callback','Memory SDK A',False),True)
 b=await restore(media.connect('https://chatgpt.com/connector/oauth/isolated-test-callback','Memory SDK B',False))
 assert a==b
 report={'testType':'Independent official MCP SDK sessions on actual project state; not external ChatGPT/Claude model calls','sessionA':a,'sessionB':b,'externalAiCalls':0,'actualProductSessions':'NOT TESTED'}
 (media.ROOT/'docs/studio-collaboration/memory-protocol-results.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
asyncio.run(main())
