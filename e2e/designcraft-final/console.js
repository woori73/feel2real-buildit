// Console-only investigation. No documents, AI requests or export flow repeated.
const {chromium}=require('playwright-core');
const fs=require('fs'),path=require('path');
const OUT=path.join(__dirname,'artifacts');fs.mkdirSync(OUT,{recursive:true});
const BASE='http://127.0.0.1:8080';const rows=[];
function location(raw){try{const u=new URL(raw);return {host:u.hostname,path:u.pathname};}catch{return {host:'unknown',path:'unknown'};}}
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH,headless:true,args:['--no-sandbox']});
 try{for(const policy of (process.env.CONSOLE_POLICIES||'normal-network').split(','))for(const width of [1440,390]){
  const ctx=await browser.newContext({viewport:{width,height:width===390?844:1000}});
  if(policy==='previous-local-only')await ctx.route(u=>!['localhost','127.0.0.1'].includes(u.hostname),r=>r.abort());
  const page=await ctx.newPage(),row={policy,width,console:[],failedRequests:[],httpErrors:[],pageErrors:[],checks:[]};rows.push(row);let stage='login';
  page.on('console',m=>{if(m.type()!=='error')return;const t=m.text();row.console.push({stage,category:/Failed to load resource/.test(t)?'resource-load':/CORS|Access-Control/.test(t)?'cors':'other',networkCode:(t.match(/net::[A-Z_]+/)||[])[0]||null,httpStatus:(t.match(/status of (\d{3})/)||[])[1]||null,source:location(m.location().url)});});
  page.on('requestfailed',r=>row.failedRequests.push({stage,...location(r.url()),type:r.resourceType(),error:(r.failure()?.errorText.match(/net::[A-Z_]+/)||[])[0]||'unspecified'}));
  page.on('response',r=>{if(r.status()>=400)row.httpErrors.push({stage,...location(r.url()),status:r.status()});});
  page.on('pageerror',e=>row.pageErrors.push({stage,name:e.name}));
  await page.goto(BASE+'/?login=required&returnUrl=/designcraft/editor',{waitUntil:'domcontentloaded'});
  await page.waitForSelector('[data-login-modal]:not([hidden])');await page.waitForTimeout(2000);
  await page.fill('[data-login-modal-form] [name="loginId"]',process.env.E2E_LOGIN_ID);
  await page.fill('[data-login-modal-form] [name="password"]',process.env.E2E_LOGIN_PW);
  await Promise.all([page.waitForURL('**/designcraft/editor',{waitUntil:'domcontentloaded'}),page.click('.login-modal-submit')]);stage='editor';
  await page.waitForSelector('#btn-new-project');await page.waitForTimeout(3000);
  row.checks.push('authenticated-editor-rendered');
  await page.screenshot({path:path.join(OUT,`console-${policy}-${width}.png`)});
  await ctx.close();
 }}finally{await browser.close();fs.writeFileSync(path.join(OUT,'console-results.json'),JSON.stringify({target:'f244717581c5dce7413858407ca80fb95d833f03',rows},null,2));}
 console.log('Console-only probe completed; bodies/messages withheld');
})().catch(()=>{console.error('Console-only probe failed; raw error withheld');process.exitCode=1;});
