#!/usr/bin/env python3
"""Local UI fixtures only; no production API requests, credentials, or production writes."""
from pathlib import Path
import argparse,json,shutil,subprocess
parser=argparse.ArgumentParser();parser.add_argument('--output',required=True);parser.add_argument('--video');args=parser.parse_args()
root=Path(__file__).resolve().parents[2];project=Path(args.output).resolve();out=project/'dist';out.mkdir(parents=True,exist_ok=True)
for name in ['agent_studio.js','studio_catalog.js']:
 (out/'js').mkdir(exist_ok=True);shutil.copyfile(root/'src/main/resources/static/js'/name,out/'js'/name)
(out/'css').mkdir(exist_ok=True);shutil.copyfile(root/'src/main/resources/static/css/agent_studio.css',out/'css/agent_studio.css')
html=(root/'src/main/resources/templates/studio/index.html').read_text().replace('<script src="/js/studio_catalog.js','<script src="/fixtures.js"></script><script src="/js/studio_catalog.js')
html=html.replace('</head>','<style>html{scrollbar-width:none}html::-webkit-scrollbar{width:0;height:0}</style></head>')
(out/'app.html').write_text(html)
old=subprocess.check_output(['git','show','745b5cf127f316120785c581b7c7a1b5a18d27a7:src/main/resources/templates/studio/index.html'],cwd=root,text=True)
old=old.replace('/js/agent_studio.js','/old/agent_studio.js').replace('/css/agent_studio.css','/old/agent_studio.css').replace('<script src="/old/agent_studio.js','<script src="/fixtures.js"></script><script src="/old/agent_studio.js')
old=old.replace('</head>','<style>html{scrollbar-width:none}html::-webkit-scrollbar{width:0;height:0}</style></head>')
(out/'before.html').write_text(old);(out/'old').mkdir(exist_ok=True)
for kind,ext in [('js','js'),('css','css')]:
 (out/'old'/('agent_studio.'+ext)).write_text(subprocess.check_output(['git','show','745b5cf127f316120785c581b7c7a1b5a18d27a7:src/main/resources/static/'+kind+'/agent_studio.'+ext],cwd=root,text=True))
(project/'.openai').mkdir(exist_ok=True);(project/'.openai/hosting.json').write_text('{"static":{"directory":"dist"}}')
(out/'index.html').write_text('''<!doctype html><html lang="ko"><meta charset="utf-8"><title>Studio · 두 섹션 검수</title><style>body{margin:0;background:#151d2a;color:#e3ecfa;font:14px system-ui}header{position:static;z-index:10;padding:12px;background:#172638}button,select{font:inherit;min-height:36px;margin:4px;padding:6px 12px}p{margin:5px}iframe{display:block;border:0;background:#080f1d;margin:14px 0}#wrap{width:fit-content;min-width:100%;overflow:visible}</style><header><strong>Studio 개편 검수 · 운영 배포 전</strong><p>수정한 실제 HTML/CSS/JS + 격리 API 예시. v11만 기존 원본 영상 사용. 서수니·세븐 플레이어는 인물 없는 시험 미디어. 운영 DB와 연결되지 않습니다.</p><button data-width="1440" data-height="960">PC 1440</button><button data-width="390" data-height="844">모바일 390</button><button data-width="360" data-height="800">모바일 360</button><button data-width="412" data-height="915">모바일 412</button><button id="before">기존 11개 메뉴</button><button id="after">개편 2개 섹션</button><label>시험 역할 <select id="role"><option>APPROVER</option><option>EDITOR</option><option>VIEWER</option></select></label></header><div id="wrap"><iframe title="Studio 검수 화면" width="1440" height="960" src="/app.html?qaRole=APPROVER"></iframe></div><script>const frame=document.querySelector('iframe');document.querySelectorAll('[data-width]').forEach(b=>b.onclick=()=>{frame.width=b.dataset.width;frame.height=b.dataset.height});let page='app.html';const refresh=()=>{frame.src='/'+page+'?qaRole='+document.querySelector('#role').value};document.querySelector('#before').onclick=()=>{page='before.html';refresh()};document.querySelector('#after').onclick=()=>{page='app.html';refresh()};document.querySelector('#role').onchange=refresh;</script></html>''')
media=out/'api/studio/files';media.mkdir(parents=True,exist_ok=True)
if args.video:
 v=Path(args.video).resolve();target=media/'v11';target.mkdir(exist_ok=True);shutil.copyfile(v,target/'stream')
 test=out/'test.mp4'
 subprocess.run(['ffmpeg','-v','error','-f','lavfi','-i','color=c=0x10263e:s=640x360:r=24','-t','3','-an','-c:v','libx264','-pix_fmt','yuv420p','-movflags','+faststart','-y',str(test)],check=True)
 for name in ['suni','seven','approved','raw']:
  d=media/name;d.mkdir(exist_ok=True);shutil.copyfile(test,d/'stream')
(out/'fixtures.js').write_text(r'''"use strict";
(()=>{
 const network=window.fetch.bind(window),role=new URLSearchParams(location.search).get('qaRole')||'APPROVER';
 const files=[],rows=[],stamp=n=>'2026-10-25T'+String(n).padStart(2,'0')+':00:00Z';
 function file(id,filename,extension='mp4'){const f={id,filename,extension,size_bytes:10000,sha256:'0'.repeat(64),created_at:stamp(12)};files.push(f);return f;}
 function row(id,key,title,fileId,episode=null,label='r1',work='REVIEW',approved=null){const f=files.find(f=>f.id===fileId);const r={id,entry_type:'REVISION',item_key:key,title,file_id:fileId,episode,label,work_status:work,approved_id:approved,head_id:id,origin:'AI_PROPOSAL',kind:'VIDEO',content:'검수용 예시 메타데이터. 운영 자료와 승인 상태는 변경하지 않습니다.',dialogue:'',direction:'',created_at:stamp(13),last_review:'',...(f?{filename:f.filename,sha256:f.sha256,extension:f.extension,size_bytes:f.size_bytes}:{})};rows.push(r);return r;}
 file('v11','AI_AGENT_EP01_S01-S05_v11.mp4');file('suni','HOLO_SUNI_WEB_stage_playback.mp4');file('seven','S17_HOLO_SEQUENCE_preview.mp4');file('approved','QA_approved_example.mp4');file('raw','recent-unlinked.mp4');file('report','AI_AGENT_EP01_S01-S05_v11_REVIEW.md','md');
 const film=row('film-v11','ep01-s01s05-video','1화 S01–S05 v11 · 직접 시청 영상','v11',1,'claude-v11');film.created_at=stamp(18);
 row('suni-web','HOLO-SUNI-WEB-FINAL','서수니 홈페이지 상태창 최종 — TALK T1b v3 · IDLE T2 · 승인 포스터','suni',null,'final-r1');
 row('seven-final','HOLO-SEVEN-ENTRANCE-FINAL-CANDIDATE','1화 S17 세븐 홀로그램 등장 최종 후보','seven',1,'final-r1');
 const approved=row('approved-old','qa-approval-series','승인본 보존 시험 예시 (운영 승인 아님)','approved',1,'v1','READY','approved-old');approved.head_id='approval-new';
 const candidate=row('approval-new','qa-approval-series','새 후보와 이전 승인본을 함께 보존하는 예시','suni',1,'v2','REVIEW','approved-old');
 const report=row('film-review','ep01-s01s05-v11-work-review','1화 S01–S05 v11 Work 직접 재생 검수 · HOLD','report',1,'work-v11-review');report.kind='REVIEW';
 const prior={...film,id:'film-v10',label:'claude-v10',title:'1화 S01–S05 v10',head_id:'film-v11',created_at:stamp(11)};rows.push(prior);
 for(let i=1;i<=48;i++){file('doc-'+i,'콘티_자료_'+String(i).padStart(2,'0')+'.md','md');const r=row('record-'+i,'qa-document-'+i,'보존 문서 '+i,'doc-'+i,i%3===0?null:1,'r1','READY');r.kind='SCRIPT';r.created_at=stamp(10);}
 function all(){const linked=new Set(rows.map(r=>r.file_id));return [...rows,...files.filter(f=>!linked.has(f.id)).map(f=>({...f,entry_type:'FILE',file_id:f.id,title:f.filename,kind:'FILE',origin:'ORIGINAL',episode:null,work_status:'UPLOADED',head_id:null,approved_id:null,last_review:''}))];}
 function category(r){return ['mp4','mov'].includes(r.extension)?'VIDEO':['png','jpg','webp'].includes(r.extension)?'IMAGE':['wav','mp3','flac'].includes(r.extension)?'AUDIO':r.extension==='zip'?'PACKAGE':'DOCUMENT';}
 function selected(p){let a=all(),q=(p.get('q')||'').toLowerCase(),ep=p.get('episode'),media=p.get('media'),status=p.get('status'),kind=p.get('kind');
 if(p.get('versions')!=='all')a=a.filter(r=>r.entry_type==='FILE'||r.id===r.head_id||r.id===r.approved_id);
 if(p.get('section')==='review'&&status!=='UNASSIGNED')a=a.filter(r=>r.kind==='REVIEW'||r.work_status==='REVIEW'||r.id===r.approved_id||r.last_review||r.entry_type==='FILE'&&/review|compare|preview|^qa_|검수|비교/i.test(r.filename));
 if(ep)a=a.filter(r=>ep==='common'?!r.episode:r.episode===Number(ep));if(media)a=a.filter(r=>category(r)===media);
 if(kind)a=a.filter(r=>r.kind===kind||(kind==='VIDEO'&&category(r)==='VIDEO')||(kind==='MUSIC'&&category(r)==='AUDIO'));
 if(status==='APPROVED')a=a.filter(r=>r.id===r.approved_id);if(status==='PENDING')a=a.filter(r=>r.work_status==='REVIEW'&&r.id!==r.approved_id&&r.last_review!=='REJECT');if(status==='UNASSIGNED')a=a.filter(r=>r.entry_type==='FILE');if(status==='REJECTED')a=a.filter(r=>r.last_review==='REJECT');if(status==='CHANGES')a=a.filter(r=>r.last_review==='REQUEST_CHANGES');
 if(q)a=a.filter(r=>['title','filename','item_key','label','sha256','content','dialogue','direction'].some(k=>String(r[k]||'').toLowerCase().includes(q)));
 return a.sort((a,b)=>b.created_at.localeCompare(a.created_at)||b.id.localeCompare(a.id)).slice(Number(p.get('offset')||0),Number(p.get('offset')||0)+Number(p.get('limit')||200));}
 const response=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
 window.fetch=async(input,options={})=>{const u=new URL(typeof input==='string'?input:input.url,location.origin);if(!u.pathname.startsWith('/api/studio/'))return network(input,options);
 const path=u.pathname.slice('/api/studio/'.length),p=u.searchParams;let data=options.body&&typeof options.body==='string'?JSON.parse(options.body):null;
 if(p.get('projectId')!=='ai-agent-part1')return response({message:'다른 프로젝트 접근 불가'},403);
 if(path==='project')return response({title:'《AI AGENT》 1부',initialized:true,permission:role,canManage:role==='APPROVER',files:files.length,archived:false});
 if(path==='source-import')return response({registeredSourcePaths:536});
 if(path==='catalog')return response(selected(p));
 if(path==='files'&&options.method==='POST'){if(role==='VIEWER')return response({message:'열람 권한은 업로드할 수 없습니다.'},403);const uploaded=options.body.get('file');const f=file('uploaded-'+Date.now(),uploaded.name,uploaded.name.split('.').pop());f.created_at='2026-10-25T23:00:00Z';f.size_bytes=uploaded.size;return response({duplicate:false,file:f});}
 if(path==='files')return response(files.slice(Number(p.get('offset')||0),Number(p.get('offset')||0)+Number(p.get('limit')||200)));
 if(path.startsWith('files/')){const id=path.split('/')[1],f=files.find(f=>f.id===id);if(!f)return response({message:'파일 없음'},404);if(path.endsWith('/preview'))return new Response('<!doctype html><html lang="ko"><h1>검수용 문서</h1><p>원본·버전·승인 이력은 보존합니다.</p></html>',{headers:{'Content-Type':'text/html'}});return response(f);}
 if(path==='revisions'&&options.method==='POST'){if(role==='VIEWER')return response({message:'권한 없음'},403);const previous=rows.find(r=>r.id===data.parentId);if(previous&&previous.head_id!==previous.id)return response({message:'새 버전이 있습니다.'},409);const r=row('new-'+Date.now(),data.itemKey,data.title,data.fileId,data.episode,data.label,data.workStatus);Object.assign(r,{content:data.content,kind:data.kind,origin:data.origin,created_at:'2026-10-25T23:30:00Z'});if(previous)rows.filter(x=>x.item_key===previous.item_key).forEach(x=>x.head_id=r.id);return response(r);}
 if(path==='revisions')return response(rows.filter(r=>(!p.get('episode')||r.episode===Number(p.get('episode')))&&(!p.get('kind')||r.kind===p.get('kind'))&&(!p.get('q')||[r.title,r.content].join(' ').includes(p.get('q')))).slice(Number(p.get('offset')||0),Number(p.get('offset')||0)+Number(p.get('limit')||200)));
 if(path.endsWith('/comments'))return response([]);if(path.startsWith('revisions/'))return response(rows.find(r=>r.id===path.split('/')[1]));
 if(path==='pending')return response(rows.filter(r=>r.work_status==='REVIEW'&&r.id!==r.approved_id));
 if(path==='cuts')return response([]);if(['tasks','reviews','events','members','users'].includes(path))return response([]);if(path==='notifications')return response({items:[],readThrough:0,nextBeforeId:null});if(path==='memory')return response({});
 return response({message:'검수용 API에 없는 요청 '+path},404);
 };
})();''')
print(project)
