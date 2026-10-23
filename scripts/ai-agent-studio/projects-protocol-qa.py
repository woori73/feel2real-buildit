#!/usr/bin/env python3
"""Official local MCP SDK: project/role isolation, real document/image, no model calls."""
import asyncio,importlib.util,json
from pathlib import Path
import httpx2
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client
spec=importlib.util.spec_from_file_location('media',Path(__file__).with_name('media-protocol-qa.py'));media=importlib.util.module_from_spec(spec);spec.loader.exec_module(media)
projects=json.loads(Path('/tmp/f2r-projects-browser-fixture.json').read_text())
source=next(f for f in media.catalog['files'] if f['path']=='CURRENT_STATE.md');doc_id=media.fixture['files'][source['sha256']];image_id=media.fixture['files'][media.catalog['approvedSha256'][0]]
async def run(cookie,recipient):
 token=media.connect('https://claude.ai/api/mcp/auth_callback','Project isolation SDK',True,cookie)
 async with httpx2.AsyncClient(headers={'Authorization':'Bearer '+token},trust_env=False) as http:
  async with streamable_http_client(media.BASE+'/mcp',http_client=http) as (rx,tx):
   async with ClientSession(rx,tx) as client:
    await client.initialize()
    if not recipient:
     rows=[]
     for offset in range(0,3000,100):
      r=await client.call_tool('list_catalog',{'offset':offset,'limit':100});data=json.loads(r.content[0].text);rows+=data['items']
      if len(rows)>=data['total']:break
     assert 'projectId' in next(r for r in rows if r['id']=='GET /api/studio/memory')['params']
     Path('/tmp/f2r-route-inventory.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n')
     doc=await client.call_tool('studio_document',{'fileId':doc_id,'projectId':'ai-agent-part1'});assert not doc.is_error;assert json.loads(doc.content[0].text)['data']['sourceSha256']==source['sha256']
     image=await client.call_tool('studio_image',{'fileId':image_id,'projectId':'ai-agent-part1'});assert not image.is_error;assert any(b.type=='image' for b in image.content)
     forbidden=await client.call_tool('read_studio',{'endpointId':'GET /api/studio/memory','query':{'projectId':projects['C']}});assert forbidden.is_error
     invalid=await client.call_tool('read_studio',{'endpointId':'GET /api/studio/memory','query':{'projectId':'../ai-agent-part1'}});assert invalid.is_error
     return {'sameProjectDocumentAndImageBlock':True,'foreignProjectDenied':True,'invalidProjectDenied':True}
    listed=await client.call_tool('read_studio',{'endpointId':'GET /api/studio/projects'});assert not listed.is_error;data=json.loads(listed.content[0].text)['data'];assert len(data)==1 and data[0]['id']==projects['B']
    own=await client.call_tool('read_studio',{'endpointId':'GET /api/studio/memory','query':{'projectId':projects['B']}});assert not own.is_error
    for name,file in [('studio_document',doc_id),('studio_image',image_id)]:
     for target in ['ai-agent-part1',projects['B']]:
      denied=await client.call_tool(name,{'fileId':file,'projectId':target});assert denied.is_error;assert not any(b.type=='image' for b in denied.content)
    for endpoint in ['POST /api/studio/project-state','POST /api/studio/invitations/answer','POST /api/studio/notifications/read']:
     denied=await client.call_tool('read_studio',{'endpointId':endpoint,'query':{'projectId':projects['B']}});assert denied.is_error
    return {'onlyAcceptedProjectsVisible':True,'ownProjectMemoryReadable':True,'foreignDocumentAndImageDenied':True,'lifecycleInvitationAndNotificationWritesDenied':True}
async def main():
 report={'testType':'Official MCP SDK on isolated loopback; not product account/vision test','sourceEditor':await run(media.fixture['editorCookie'],False),'invitationRecipient':await run(media.fixture['strangerCookie'],True),'externalAiCalls':0,'actualChatGptClaude':'NOT TESTED'}
 (media.ROOT/'docs/studio-collaboration/projects-protocol-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False))
asyncio.run(main())
