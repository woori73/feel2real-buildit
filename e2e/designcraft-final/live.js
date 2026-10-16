// Real browser + Spring/MySQL/CLI/Muse Spark. No request/response bodies, secrets,
// document text, console messages or raw exception messages are printed to logs.
const { chromium } = require('playwright-core');
const fs = require('fs'), path = require('path'), crypto = require('crypto');
const { spawnSync } = require('child_process');
const OUT=path.join(__dirname,'artifacts'); fs.mkdirSync(OUT,{recursive:true});
const BASE='http://127.0.0.1:8080';
const BEFORE='이 문장은 수정 전입니다.', AFTER='이 문장은 AI 승인 후 수정되었습니다.';
const rows=[]; let stage='launch';
const must=(v,label)=>{if(!v)throw new Error(label);};
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const cli=(args)=>{const r=spawnSync(process.env.PUBLISHING_CLI_PATH,args,{encoding:'utf8'});must(r.status===0,'document_state_read_failed');return JSON.parse(r.stdout);};
function texts(doc){const info=cli(['run','--in',doc,'--cmd','document.inspect']);return info.stories.map(s=>cli(['run','--in',doc,'--cmd','story.get='+JSON.stringify({story:s.id})]).text).join('\n');}
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH,headless:true,args:['--no-sandbox']});
 try {
  for(const width of [1440,390]){
   stage=width+':login';
   const ctx=await browser.newContext({viewport:{width,height:width===390?844:1000},acceptDownloads:true});
   // All browser requests stay local: Muse Spark is invoked by Spring, never directly by browser.
   await ctx.route(u=>!['localhost','127.0.0.1'].includes(u.hostname),r=>r.abort());
   const page=await ctx.newPage();const evidence={width,height:width===390?844:1000,api:[],consoleErrors:0,pageErrors:0,checks:[]};
   rows.push(evidence);
   page.on('console',m=>{if(m.type()==='error')evidence.consoleErrors++;});
   page.on('pageerror',()=>evidence.pageErrors++);
   page.on('response',r=>{const u=new URL(r.url());if(u.pathname.startsWith('/api/'))evidence.api.push({method:r.request().method(),path:u.pathname,status:r.status()});});
   await page.goto(BASE+'/?login=required&returnUrl=/designcraft/editor');
   await page.waitForSelector('[data-login-modal]:not([hidden])');
   await page.screenshot({path:path.join(OUT,width+'-01-login.png')});
   await page.fill('[data-login-modal-form] [name="loginId"]',process.env.E2E_LOGIN_ID);
   await page.fill('[data-login-modal-form] [name="password"]',process.env.E2E_LOGIN_PW);
   await Promise.all([page.waitForURL('**/designcraft/editor'),page.click('.login-modal-submit')]);
   await page.waitForSelector('#btn-new-project');evidence.checks.push('real_login_PASS');
   stage=width+':import';
   await page.click('#btn-new-project');await page.fill('#np-name','격리 통합 검증 '+width);
   await page.check('input[name="np-template"][value="ko-textbook-a4"]');
   await page.setInputFiles('#np-file',{name:'integration.txt',mimeType:'text/plain',buffer:Buffer.from('통합 검증 제목\n'+BEFORE+'\n다음 문단은 유지되어야 합니다.\n')});
   await page.click('#np-create');
   await page.waitForFunction(()=>document.getElementById('upload-last').textContent.includes('integration.txt'),null,{timeout:90000});
   await page.waitForFunction(()=>{const p=document.getElementById('page-img');return p&&p.complete&&p.naturalWidth>0;},null,{timeout:30000});
   const jobId=await page.evaluate(()=>window.PublishingEditor.jobId);
   const doc=path.join(process.env.PUBLISHING_STORAGE_ROOT,'jobs',String(jobId),'job.designcraft');
   must(texts(doc).includes(BEFORE),'import_content_missing');evidence.checks.push('import_typeset_preview_PASS');
   await page.screenshot({path:path.join(OUT,width+'-02-import-preview.png')});
   stage=width+':ai_plan';
   if(width===390){await page.click('#ai-fab');must(await page.isVisible('#ai-input'),'mobile_AI_open_failed');await page.click('#btn-ai-close');must(!(await page.isVisible('#ai-input')),'mobile_AI_close_failed');await page.click('#ai-fab');evidence.checks.push('mobile_panel_open_close_PASS');}
   else must(await page.isVisible('#ai-input'),'desktop_AI_panel_hidden');
   const beforeHash=hash(doc);
   await page.fill('#ai-input',`문서에서 "${BEFORE}" 문장을 "${AFTER}"로 바꿔줘. document.replaceText 명령 한 건으로 계획해줘. 다른 내용은 유지해줘.`);
   const planResponse=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/publishing/ai/plan');
   await page.click('#ai-send');const response=await planResponse;must(response.status()===200,'real_AI_HTTP_'+response.status());
   const plan=await response.json();must(plan.needsApproval&&!plan.blocked,'AI_plan_blocked');
   must(plan.plan.length===1&&plan.plan[0].command==='document.replaceText','AI_plan_wrong_command');
   must(plan.plan[0].params.before===BEFORE&&plan.plan[0].params.after===AFTER,'AI_plan_wrong_text');
   await page.waitForSelector('.plan-card .plan-actions button');
   must(hash(doc)===beforeHash,'changed_before_user_approval');evidence.checks.push('real_Muse_plan_no_mutation_PASS');
   await page.screenshot({path:path.join(OUT,width+'-03-plan-before-approval.png')});
   stage=width+':approve';
   const appliedResponse=page.waitForResponse(r=>/\/revisions$/.test(new URL(r.url()).pathname));
   await page.getByRole('button',{name:'승인 후 적용',exact:true}).click();
   const applied=await appliedResponse;must(applied.status()===200,'approval_HTTP_'+applied.status());
   const revision=await applied.json();must(revision.applied,'approval_not_applied');
   must(hash(doc)!==beforeHash,'approved_document_unchanged');
   const changed=texts(doc);must(changed.includes(AFTER)&&!changed.includes(BEFORE),'approved_text_mismatch');evidence.checks.push('approved_edit_PASS');
   await page.waitForFunction(()=>document.getElementById('ai-log').textContent.includes('적용 완료'));
   if(width===390)await page.click('#btn-ai-close');
   await page.waitForFunction(()=>document.getElementById('preview-loading').hidden);
   await page.screenshot({path:path.join(OUT,width+'-04-approved-preview.png')});
   stage=width+':pdf_download';
   await page.click('#btn-preflight-top');
   await page.waitForFunction(()=>document.getElementById('preflight-result').textContent.includes('검수 통과'),null,{timeout:45000});
   await page.click('#btn-export-top');await page.check('input[name="ex-standard"][value="none"]');
   const exportedResponse=page.waitForResponse(r=>/\/export$/.test(new URL(r.url()).pathname));
   await page.click('#ex-start');must((await exportedResponse).status()===202,'PDF_export_failed');
   const download=page.waitForEvent('download');await page.getByRole('link',{name:'다운로드',exact:true}).click();
   const pdf=path.join(OUT,width+'-download.pdf');await(await download).saveAs(pdf);
   const extracted=spawnSync('pdftotext',[pdf,'-'],{encoding:'utf8'});must(extracted.status===0,'download_PDF_reopen_failed');
   must(extracted.stdout.replace(/\s/g,'').includes(AFTER.replace(/\s/g,'')),'download_PDF_content_failed');evidence.checks.push('PDF_download_reopen_PASS');
   await page.screenshot({path:path.join(OUT,width+'-05-download.png')});
   // Restore through the authenticated version API, not a file overwrite.
   stage=width+':restore';
   const restore=await ctx.request.post(BASE+`/api/publishing/jobs/${jobId}/versions/${revision.snapshotId}/restore`);
   must(restore.ok(),'version_restore_HTTP_'+restore.status());must(texts(doc).includes(BEFORE),'version_restore_content_failed');evidence.checks.push('version_restore_PASS');
   must(evidence.pageErrors===0,'browser_page_error');evidence.result='PASS';await ctx.close();
  }
 }finally{await browser.close();fs.writeFileSync(path.join(OUT,'results.json'),JSON.stringify({target:'0dc3d3e49def356147f4aaf5a4bdfcfff7a3b972',rows},null,2));}
 console.log('DesignCraft final integration PASS: 1440px and 390px; real AI, approval, PDF, restore. Bodies withheld.');
})().catch(()=>{fs.writeFileSync(path.join(OUT,'failure-stage.json'),JSON.stringify({failed_stage:stage}));console.error('DesignCraft integration FAILED at stage '+stage+'; raw exception/body withheld');process.exitCode=1;});
