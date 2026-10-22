#!/usr/bin/env python3
"""Official Python MCP SDK smoke test against an isolated local app.
This is a protocol-client test, NOT a ChatGPT/Claude subscription account test.
Reads synthetic-test session cookies from /tmp; never writes tokens to the repository.
"""
import asyncio
import base64
import hashlib
import json
import os
import re
import time
from pathlib import Path
from urllib.parse import urlencode, urlparse, parse_qs
import httpx
import httpx2
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client
BASE='http://127.0.0.1:18088'
fixture=json.loads(Path('/tmp/f2r-browser-fixture.json').read_text())
OUT=Path(__file__).resolve().parents[2]/'docs/ai-agent-studio/protocol-results.json'

def connect(callback,name):
    with httpx.Client(base_url=BASE,trust_env=False) as client:
        registration=client.post('/mcp/oauth/register',json={'client_name':name,'redirect_uris':[callback],'token_endpoint_auth_method':'none'})
        registration.raise_for_status();cid=registration.json()['client_id'];verifier='sdk-test-'+'z'*50
        challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
        q={'client_id':cid,'redirect_uri':callback,'response_type':'code','state':'sdk-state','code_challenge_method':'S256','code_challenge':challenge,'resource':BASE+'/mcp'}
        page=client.get('/mcp/oauth/authorize',params=q,headers={'Cookie':fixture['adminCookie']});page.raise_for_status()
        nonce=re.search(r'name="nonce" value="([^"]+)"',page.text).group(1)
        form=urlencode({'nonce':nonce})+'&scopes=catalog%3Aread&scopes=studio%3Aread'
        consent=client.post('/mcp/oauth/authorize',content=form,headers={'Cookie':fixture['adminCookie'],'Origin':BASE,'Content-Type':'application/x-www-form-urlencoded'})
        assert consent.status_code==303,consent.text
        code=parse_qs(urlparse(consent.headers['location']).query)['code'][0]
        response=client.post('/mcp/oauth/token',data={'grant_type':'authorization_code','client_id':cid,'redirect_uri':callback,'code':code,'code_verifier':verifier,'resource':BASE+'/mcp'})
        response.raise_for_status();return response.json()['access_token']

async def read(token):
    async with httpx2.AsyncClient(headers={'Authorization':'Bearer '+token},trust_env=False) as http:
        async with streamable_http_client(BASE+'/mcp',http_client=http) as (rx,tx):
            async with ClientSession(rx,tx) as session:
                init=await session.initialize()
                tools=await session.list_tools()
                assert len(tools.tools)>=19
                response=await session.call_tool('read_studio',{'endpointId':'GET /api/studio/revisions/{id}','pathParams':{'id':fixture['originalId']}})
                assert not response.is_error,response
                original=await session.call_tool('read_studio',{'endpointId':'GET /api/studio/files/{id}/content','pathParams':{'id':fixture['fileId']},'offset':0,'limit':65536})
                assert not original.is_error,original
                original_data=json.loads(original.content[0].text);raw=base64.b64decode(original_data['base64'])
                assert hashlib.sha256(raw).hexdigest()==original_data['chunkSha256']
                assert b'SYNTHETIC TEST ONLY' in raw
                search=await session.call_tool('search',{'query':'테스트','service':'studio'})
                assert not search.is_error
                denied=await session.call_tool('read_studio',{'endpointId':'POST /api/studio/revisions'})
                assert denied.is_error
                inventory=[]
                for offset in range(0,2000,100):
                    result=await session.call_tool('list_catalog',{'offset':offset,'limit':100})
                    body=json.loads(result.content[0].text);inventory+=body['items']
                    if len(inventory)>=body['total']:break
                Path('/tmp/f2r-route-inventory.json').write_text(json.dumps(inventory,ensure_ascii=False,indent=2)+'\n')
                timings=[]
                for _ in range(10):
                    start=time.perf_counter()
                    result=await session.call_tool('read_studio',{'endpointId':'GET /api/studio/project'})
                    assert not result.is_error;timings.append(round((time.perf_counter()-start)*1000,2))
                return {'protocolVersion':init.protocol_version,'toolCount':len(tools.tools),'catalogRows':len(inventory),
                    'readContent':response.content[0].text,'readLatencyMs':timings,'search':not search.is_error,'writeDenied':denied.is_error}

async def main():
    a=await read(connect('https://chatgpt.com/connector/oauth/isolated-test-callback','Protocol SDK client A'))
    b=await read(connect('https://claude.ai/api/mcp/auth_callback','Protocol SDK client B'))
    assert a['readContent']==b['readContent']
    digest=hashlib.sha256(a.pop('readContent').encode()).hexdigest();b.pop('readContent')
    report={'testType':'Official Python MCP SDK 2.1.1 with two independently authorized OAuth clients; NOT actual ChatGPT/Claude account connections',
        'sameContent':True,'responseSha256':digest,'clientA':a,'clientB':b,
        'actualAccountTests':'NOT RUN: no public staging endpoint or authenticated subscription sessions were supplied; no production deployment authorized.'}
    OUT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False,indent=2))
asyncio.run(main())
