#!/usr/bin/env python3
"""Two independent official SDK clients on actual imported files. NOT product account/vision tests."""
import asyncio,base64,hashlib,json,re
from pathlib import Path
from urllib.parse import urlencode,urlparse,parse_qs
import httpx,httpx2
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client
BASE='http://127.0.0.1:18090'
ROOT=Path(__file__).resolve().parents[2]
fixture=json.loads(Path('/tmp/f2r-assets-browser-fixture.json').read_text())
catalog=json.loads((ROOT/'src/main/resources/studio/production-09d62dee.json').read_text())

def connect(callback,name,originals=True):
 with httpx.Client(base_url=BASE,trust_env=False) as client:
  r=client.post('/mcp/oauth/register',json={'client_name':name,'redirect_uris':[callback],'token_endpoint_auth_method':'none'});r.raise_for_status();cid=r.json()['client_id'];verifier='local-media-'+'x'*48
  challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
  q=dict(client_id=cid,redirect_uri=callback,response_type='code',state='local-media',code_challenge_method='S256',code_challenge=challenge,resource=BASE+'/mcp')
  r=client.get('/mcp/oauth/authorize',params=q,headers={'Cookie':fixture['adminCookie']});r.raise_for_status();nonce=re.search(r'name="nonce" value="([^"]+)"',r.text).group(1)
  form=urlencode({'nonce':nonce})+'&scopes=catalog%3Aread&scopes=studio%3Aread'+('&scopes=originals%3Aread&sensitiveAcknowledged=true' if originals else '')
  r=client.post('/mcp/oauth/authorize',content=form,headers={'Cookie':fixture['adminCookie'],'Origin':BASE,'Content-Type':'application/x-www-form-urlencoded'});assert r.status_code==303
  code=parse_qs(urlparse(r.headers['location']).query)['code'][0]
  r=client.post('/mcp/oauth/token',data=dict(grant_type='authorization_code',client_id=cid,redirect_uri=callback,code=code,code_verifier=verifier,resource=BASE+'/mcp'));r.raise_for_status();return r.json()['access_token']

async def read(token,originals):
 async with httpx2.AsyncClient(headers={'Authorization':'Bearer '+token},trust_env=False) as http:
  async with streamable_http_client(BASE+'/mcp',http_client=http) as (rx,tx):
   async with ClientSession(rx,tx) as session:
    await session.initialize();tools=await session.list_tools();assert len(tools.tools)==21
    assert all(t.annotations.read_only_hint and not t.annotations.destructive_hint for t in tools.tools)
    source=next(f for f in catalog['files'] if f['path']=='CURRENT_STATE.md');doc=await session.call_tool('studio_document',{'fileId':fixture['files'][source['sha256']]});assert not doc.is_error
    data=json.loads(doc.content[0].text)['data'];assert data['sourceSha256']==source['sha256'];assert '0/68' in data['text'];assert '미생성' in data['text']
    digest=hashlib.sha256(data['text'].encode()).hexdigest()
    image_sha=catalog['approvedSha256'][0];image=await session.call_tool('studio_image',{'fileId':fixture['files'][image_sha]})
    if not originals:
     assert image.is_error and all(b.type!='image' for b in image.content)
     return {'tools':len(tools.tools),'documentSha256':digest,'imageDeniedWithoutConsent':True}
    assert not image.is_error
    assert [b.type for b in image.content]==['text','image']
    block=image.content[1];assert block.mime_type=='image/jpeg';raw=base64.b64decode(block.data);assert len(raw)<=102400 and raw[:2]==b'\xff\xd8'
    metadata=json.loads(image.content[0].text);assert metadata['sourceSha256']==image_sha;assert metadata['derivativeSha256']==hashlib.sha256(raw).hexdigest()
    denied=await session.call_tool('read_studio',{'endpointId':'POST /api/studio/source-import'});assert denied.is_error
    return {'tools':len(tools.tools),'documentSha256':digest,'originalSha256':image_sha,'derivativeSha256':metadata['derivativeSha256'],'imageContentBlock':True,'derivativeBytes':len(raw),'writeDenied':True}

async def main():
 a=await read(connect('https://chatgpt.com/connector/oauth/isolated-test-callback','Local SDK A'),True)
 b=await read(connect('https://claude.ai/api/mcp/auth_callback','Local SDK B'),True)
 basic=await read(connect('https://claude.ai/api/mcp/auth_callback','Local SDK no-originals',False),False)
 assert a['documentSha256']==b['documentSha256']==basic['documentSha256'];assert a['derivativeSha256']==b['derivativeSha256']
 report={'testType':'Official Python MCP SDK 2.1.1, 3 independent OAuth grants against actual #863 originals; NOT ChatGPT/Claude product account or model vision tests','sourceCommit':catalog['commit'],'clientA':a,'clientB':b,'withoutOriginalConsent':basic,'actualProductImageReading':'NOT TESTED','actualExternalAiCalls':0}
 (ROOT/'docs/studio-collaboration/media-protocol-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False))
if __name__=='__main__': asyncio.run(main())
