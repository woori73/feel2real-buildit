"use strict";
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const deps=process.env.F2R_QA_MODULES||'/workspace/scratch/studio-review-deps/node_modules';
const {chromium}=require(path.join(deps,'playwright'));
const base='http://127.0.0.1:18088',out=path.resolve(__dirname,'../../docs/studio-collaboration');
const fixture=JSON.parse(fs.readFileSync('/tmp/f2r-browser-fixture.json'));
const results=[];const pass=(name,detail)=>(results.push({name,pass:true,detail}),console.log(name));
(async()=>{
 const generated=require("node:child_process").spawnSync("python3",[path.join(__dirname,"make-readable-fixtures.py")],{stdio:"inherit"});assert.equal(generated.status,0);
 const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/chromium',args:['--no-sandbox']});
 try {
  const context=await browser.newContext({viewport:{width:1440,height:1000}});
  const [name,...rest]=fixture.adminCookie.split('=');await context.addCookies([{name,value:rest.join('='),url:base}]);
  const post=async(url,data)=>{const r=await context.request.post(base+'/api/studio/'+url,{headers:{Origin:base},data});assert.equal(r.status(),200,await r.text());return (await r.text())?await r.json():null;};
  const upload=async(p,name)=>{const r=await context.request.post(base+'/api/studio/files',{headers:{Origin:base},multipart:{file:{name,mimeType:'application/octet-stream',buffer:fs.readFileSync(p)}}});assert.equal(r.status(),200,await r.text());return (await r.json()).file;};
  const archive=await upload('/tmp/f2r-stage1-synthetic.zip','QA-SYNTHETIC-68-cuts.zip');
  const registration=await context.request.post(base+'/api/studio/imports/ep01',{headers:{Origin:base},data:{fileId:archive.id}});assert.ok([200,409].includes(registration.status()),await registration.text());if(registration.status()===200)assert.equal((await registration.json()).registered,68);pass('실제 MySQL에 합성 68컷 ZIP 일괄 등록');
  const unsafeArchive=await upload('/tmp/f2r-stage1-unsafe.zip','QA-SYNTHETIC-unsafe.zip');
  const png=await upload('/tmp/f2r-qa-assets/qa-synthetic.png','qa-synthetic.png');
  const original=await post('revisions',{itemKey:'readable-qa-character-'+Date.now(),label:'original',kind:'CHARACTER',origin:'ORIGINAL',title:'합성 QA 캐릭터 · 실제 작품 아님',content:'캐릭터 이미지·설명 검증',workStatus:'REVIEW',fileId:png.id});
  await post('revisions/'+original.id+'/review',{action:'APPROVE',note:'합성 UI 테스트 승인'});
  const cuts=await (await context.request.get(base+'/api/studio/cuts?episode=1')).json();
  await post('revisions/'+cuts[0].id+'/review',{action:'APPROVE',note:'비교 테스트 원본 승인'});
  const candidate=await post('revisions',{itemKey:cuts[0].item_key,parentId:cuts[0].id,label:'qa-'+Date.now(),kind:'STORYBOARD',episode:1,cutNumber:1,origin:'AI_PROPOSAL',title:'합성 QA · 수정 후보',content:'승인 원본은 그대로 보존됩니다.',workStatus:'REVIEW',fileId:png.id});
  const documents={};for(const ext of ['html','md','pdf'])documents[ext]=await upload('/tmp/f2r-qa-assets/qa-synthetic.'+ext,'qa-synthetic.'+ext);
  const page=await context.newPage(),errors=[],external=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',route=>{if(!route.request().url().startsWith(base)&&!route.request().url().startsWith('blob:')){external.push(route.request().url());route.abort();}else route.continue();});
  const dismiss=async()=>{const b=page.locator('#assistantWelcome button');if(await b.isVisible())await b.click();};
  await page.goto(base+'/ai-agent-studio');await page.locator('#storyboard .story-card').nth(67).waitFor();await page.waitForLoadState('networkidle');await dismiss();
  assert.equal(await page.locator('#storyboard .story-card').count(),68);
  await page.locator('#storyboard img').first().waitFor();await page.waitForFunction(()=>document.querySelector('#storyboard img').naturalWidth>0);
  pass('68컷 이미지·설명 함께 표시, 실제 이미지 디코딩');
  await page.locator('#board-heading').evaluate(e=>e.scrollIntoView({block:'start'}));await page.screenshot({path:out+'/screens/board-1440.png'});
  await page.locator('#storyboard .story-card').first().getByRole('button',{name:'컷 상세·검수'}).click();
  await page.locator('#detail').getByRole('button',{name:'다음 컷',exact:true}).click();assert.match(await page.locator('#detail').innerText(),/02컷/);
  await page.keyboard.press('ArrowLeft');assert.match(await page.locator('#detail').innerText(),/수정 후보/);pass('이전·다음 컷 버튼과 방향키');
  await page.locator('#detail').getByRole('button',{name:'이미지 확대'}).click();await page.locator('#image-zoom[open]').waitFor();assert.ok(await page.locator('#zoom-image').evaluate(e=>e.src.includes('/preview')));await page.screenshot({path:out+'/screens/zoom-1440.png'});await page.locator('#close-zoom').click();pass('이미지 확대 모달');
  await page.locator('#detail').getByRole('button',{name:'승인본과 수정 후보 비교'}).click();await page.locator('#cut-comparison img').nth(1).waitFor();assert.match(await page.locator('#cut-comparison').innerText(),/사용자 승인본/);assert.match(await page.locator('#cut-comparison').innerText(),/미승인/);await page.screenshot({path:out+'/screens/compare-1440.png'});await page.locator('#close-cut-compare').click();pass('승인본·수정 후보 이미지와 설명 비교');
  await page.locator('[data-tab=characters]').click();await page.locator('#character-gallery img').first().waitFor();pass('캐릭터 최신 head 이미지 갤러리');
  await page.locator('[data-tab=files]').click();
  const open=async filename=>{
    const expectedFile=filename.includes("unsafe.zip")?unsafeArchive:documents[filename.split('.').pop()];
    const expected=filename.endsWith('.zip')?'/entries':'/preview';
    const received=page.waitForResponse(r=>r.url().includes('/files/'+expectedFile.id+expected)&&r.request().method()==='GET');received.catch(()=>{});
    await page.locator('#file-list .card').filter({hasText:expectedFile.sha256}).getByRole('button',{name:'자료 열람'}).click();
    const response=await received;assert.equal(response.status(),200);
  };
  for(const ext of ['html','md']){
   await open('qa-synthetic.'+ext);await page.locator('#file-preview iframe').waitFor();
   assert.equal(await page.locator('#file-preview iframe').getAttribute('sandbox'),'');
   const frame=page.frameLocator('#file-preview iframe');await frame.locator('body').waitFor();assert.equal(await frame.locator('script,img,form,iframe').count(),0);assert.equal(await page.evaluate(()=>window.f2rUnsafeExecuted),undefined);pass(ext+' 안전 렌더링 및 스크립트·외부 이미지 제거');
   await page.locator('#file-preview').evaluate(e=>e.scrollIntoView({block:'start'}));await page.screenshot({path:out+'/screens/document-'+ext+'-1440.png'});
  }
  await open('qa-synthetic.pdf');await page.locator('#file-preview .pdf-page').waitFor();await page.waitForFunction(()=>document.querySelector('#file-preview .pdf-page').naturalWidth>0);const pdf=await context.request.get(base+'/api/studio/files/'+documents.pdf.id+'/preview');assert.equal(pdf.headers()['content-type'],'application/pdf');assert.ok((await pdf.body()).subarray(0,5).equals(Buffer.from('%PDF-')));pass('PDF 브라우저 뷰어와 원문 PDF 응답');
  await page.locator('#file-preview').evaluate(e=>e.scrollIntoView({block:'start'}));await page.screenshot({path:out+'/screens/pdf-1440.png'});
  await open('QA-SYNTHETIC-unsafe.zip');await page.locator('.zip-list button').first().waitFor();assert.equal(await page.locator('.zip-list button').filter({hasText:'../blocked.md'}).count(),0);
  await dismiss();await page.locator('.zip-list button').filter({hasText:'document.md'}).click();await page.frameLocator('#zip-preview iframe').getByRole('heading',{name:'합성 QA 문서'}).waitFor();
  const traversal=await context.request.get(base+'/api/studio/files/'+unsafeArchive.id+'/preview?entry=../blocked.md');assert.equal(traversal.status(),400);pass('ZIP 내부 목록·문서 미리보기·경로 탈출 거부');await page.screenshot({path:out+'/screens/zip-1440.png'});
  const before=await context.request.get(base+'/api/studio/files/'+png.id+'/content');assert.equal(crypto.createHash('sha256').update(await before.body()).digest('hex'),png.sha256);pass('모든 미리보기 후 원본 SHA 불변');
  await page.setViewportSize({width:390,height:844});await page.locator('[data-tab=materials]').click();await page.locator('#board-heading').evaluate(e=>e.scrollIntoView({block:'start'}));await page.screenshot({path:out+'/screens/board-390.png'});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await page.locator('#storyboard .story-card').first().getByRole('button',{name:'컷 상세·검수'}).click();await page.locator('#detail').getByRole('button',{name:'검수·사용자 승인'}).click();await page.locator('#review-form [name=note]').fill('합성 QA 모바일 승인');await page.locator('#review-form button').filter({hasText:'검수 결과 저장'}).click();await page.waitForFunction(()=>document.querySelector('#detail').textContent.includes('사용자 승인본'));const saved=await (await context.request.get(base+'/api/studio/revisions/'+candidate.id)).json();assert.equal(saved.approved_id,candidate.id);pass('390px 실제 승인 저장 후 API 재조회 확인');await page.screenshot({path:out+'/screens/approval-390.png'});
  for(const width of [1440,390]){
   await page.setViewportSize({width,height:width===1440?1000:844});
   for(const tab of ['materials','characters','files','compare']){
    await page.locator('[data-tab='+tab+']').click();await page.addScriptTag({path:require.resolve('axe-core/axe.min.js',{paths:[deps]})});const audit=await page.evaluate(async tab=>{const result=await axe.run(document.querySelector('#'+tab),{iframes:false,runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa']},rules:{'color-contrast':{enabled:false}}});return {violations:result.violations};},tab);assert.equal(audit.violations.length,0,JSON.stringify(audit.violations));
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
   }
  }pass('PC1440·모바일390 네 영역 axe 구조 검사 위반 0·가로 넘침 없음 (색 대비 별도)');
  const denied=await browser.newContext();const [sn,...sv]=fixture.strangerCookie.split('=');await denied.addCookies([{name:sn,value:sv.join('='),url:base}]);
  for(const path of ['preview','entries'])assert.equal((await denied.request.get(base+'/api/studio/files/'+archive.id+'/'+path)).status(),403);pass('무권한 사용자 파생본·ZIP 목록 403');
  assert.deepEqual(errors,[]);assert.deepEqual(external,[]);pass('브라우저 JS 오류 0·외부 네트워크 요청 0');
  fs.writeFileSync(out+'/stage1-browser-results.json',JSON.stringify({syntheticOnly:true,actualExternalAiCalls:0,results},null,2)+'\n');console.log(JSON.stringify({passed:results.length}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
