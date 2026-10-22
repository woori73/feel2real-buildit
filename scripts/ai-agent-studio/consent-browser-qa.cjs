"use strict";
// Local synthetic OAuth UI only. Does not contact ChatGPT, Claude, or production.
const fs=require("node:fs"), path=require("node:path"), crypto=require("node:crypto"), assert=require("node:assert/strict");
const {chromium}=require(path.join(process.env.F2R_QA_MODULES||"/tmp/f2r-qa-deps/node_modules","playwright"));
const base="http://127.0.0.1:18088",out=path.resolve(__dirname,"../../docs/ai-agent-studio");
const fixture=JSON.parse(fs.readFileSync("/tmp/f2r-browser-fixture.json","utf8"));
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||"/usr/bin/chromium",args:["--no-sandbox"]});
  const checks=[];
  try {
    for(const [name,width,height,cookie] of [["pc",1440,1000,fixture.adminCookie],["mobile",390,844,fixture.editorCookie]]) {
      const context=await browser.newContext({viewport:{width,height}});
      const [key,...value]=cookie.split("=");await context.addCookies([{name:key,value:value.join("="),url:base}]);
      const registration=await context.request.post(base+"/mcp/oauth/register",{data:{client_name:"Synthetic consent browser QA",redirect_uris:["https://claude.ai/api/mcp/auth_callback"]}});
      assert.equal(registration.status(),200);const client=await registration.json();
      const verifier="synthetic-browser-"+"a".repeat(43),challenge=crypto.createHash("sha256").update(verifier).digest("base64url");
      const q=new URLSearchParams({client_id:client.client_id,redirect_uri:"https://claude.ai/api/mcp/auth_callback",response_type:"code",state:"local-synthetic",code_challenge_method:"S256",code_challenge:challenge,resource:base+"/mcp"});
      const page=await context.newPage();
      // Abort any accidental non-loopback navigation; this is not a live provider connection test.
      await page.route("**/*",route=>new URL(route.request().url()).hostname==="127.0.0.1"?route.continue():route.abort());
      await page.goto(base+"/mcp/oauth/authorize?"+q);
      await page.waitForLoadState("networkidle");
      const welcome=page.locator("#assistantWelcome");
      if(await welcome.isVisible())await welcome.getByRole("button").click();
      const labels=await page.locator('input[name="scopes"]').evaluateAll(inputs=>inputs.map(input=>input.closest("label").innerText.trim()));
      assert.ok(labels.length>=16 && labels.every(label=>label.length>3));
      checks.push({name:name+" every permission has a visible description",pass:true});
      const original=page.locator('input[name="scopes"][value="originals:read"]');
      assert.equal(await original.isChecked(),false);checks.push({name:name+" original scope defaults off",pass:true});
      await page.getByText("원본 파일의 외부 제공 주의",{exact:true}).scrollIntoViewIfNeeded();
      assert.equal(await page.locator('input[name="sensitiveAcknowledged"]').count(),1);
      assert.equal(await page.locator('input[name="sensitiveAcknowledged"]').isChecked(),false);
      checks.push({name:name+" separate acknowledgement and warning",pass:true});
      await page.screenshot({path:path.join(out,"screens/consent-"+name+"-"+width+".png"),fullPage:true});
      const nonce=await page.locator('input[name="nonce"]').inputValue();
      const denied=await context.request.post(base+"/mcp/oauth/authorize",{data:new URLSearchParams([["nonce",nonce],["scopes","studio:read"],["scopes","originals:read"],["sensitiveAcknowledged","false"]]).toString(),headers:{Origin:base,"Content-Type":"application/x-www-form-urlencoded"},maxRedirects:0});
      assert.equal(denied.status(),400);checks.push({name:name+" original scope without acknowledgement rejected",pass:true});
      await context.close();
    }
    fs.writeFileSync(path.join(out,"consent-browser-results.json"),JSON.stringify({syntheticOnly:true,liveProvidersVerified:false,checks},null,2)+"\n");
    process.stdout.write(JSON.stringify({checks:checks.length,pass:checks.every(x=>x.pass)})+"\n");
  } finally {await browser.close();}
})().catch(e=>{process.stderr.write(e.message+"\n");process.exitCode=1;});
