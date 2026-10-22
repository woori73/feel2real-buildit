const { test, expect, request } = require('@playwright/test');
const fs = require('fs');
const path = require('path');
test.describe.configure({mode:'serial'});
const API='/api/dashboard/assistant/v1';
const origin={ Origin:'http://localhost:8080' };
let admin,teacher,other,student;
const suffix=Date.now().toString(36);
let createdStudent,createdClass,consultation;
async function login(loginId) {
  const c=await request.newContext({baseURL:'http://localhost:8080',extraHTTPHeaders:origin});
  const r=await c.post('/api/auth/login',{data:{loginId,password:'admin1234'}});expect(r.ok()).toBeTruthy();return c;
}
async function ok(response) {const r=await response;expect(r.ok(),await r.text()).toBeTruthy();return r.json();}
async function draft(operation,input,academyId=7,businessId=null) {return ok(admin.post(API+'/drafts',{data:{operation,input,academyId,businessId}}));}
async function approve(d,ctx=admin) {return ok(ctx.post(API+'/drafts/'+d.id+'/approve',{data:{planHash:d.planHash}}));}
async function catalog(kind) {return ok(admin.get(API+'/catalog/'+kind));}
async function seedFixtureSql(sql) {
  // This helper only talks to the explicitly named task-local Docker DB, never arbitrary DB hosts.
  require('child_process').execFileSync('docker',['exec','-i','unified-ai-mysql','mysql','--default-character-set=utf8mb4','-uroot','-punified-local-only'],{input:sql,stdio:['pipe','pipe','pipe']});
}
test.beforeAll(async()=>{
  admin=await login('admin');teacher=await login('tutor');other=await login('test-tutor');student=await login('student');
  const context=await ok(admin.get('/academycraft/api/rea/integration/context'));
  expect(context.academyId).toBe(7);expect(context.memberId).toBe(101);
  await seedFixtureSql("DELETE FROM f2r_unified_test.unified_assistant_progress WHERE user_id IN(5,7); UPDATE f2r_unified_test.unified_assistant_drafts SET status='EXPIRED' WHERE owner_id=5 AND status='PENDING';");
});
test.afterAll(async()=>{for(const c of [admin,teacher,other,student])if(c)await c.dispose();});
test('anonymous, student and cross-origin calls cannot use assistant writes',async()=>{
  const anon=await request.newContext({baseURL:'http://localhost:8080'});
  expect((await anon.get(API+'/progress')).status()).toBe(401);
  expect((await student.get(API+'/progress')).status()).toBe(403);
  expect((await admin.post(API+'/command',{headers:{Origin:'https://evil.example'},data:{message:'학생 등록'}})).status()).toBe(403);
  expect((await teacher.post(API+'/drafts',{data:{academyId:7,operation:'STUDENT_CREATE',input:{name:'거부되어야함'}}})).status()).toBe(403);
  expect((await other.get(API+'/overview?businessId=1')).status()).toBe(403);
  expect((await admin.post(API+'/drafts',{data:{academyId:8,operation:'STUDENT_CREATE',input:{name:'다른 학원 차단'}}})).status()).toBe(403);
  await anon.dispose();
});
test('first visit, pause/resume, stale version and completion persist per user',async()=>{
  const before=await ok(teacher.get(API+'/progress'));
  expect(before.firstVisit).toBe(true);expect(before.step).toBe(0);
  const saved=await ok(teacher.put(API+'/progress',{data:{step:2,completed:false,version:before.version}}));
  const resumed=await ok(teacher.get(API+'/progress'));expect(resumed.step).toBe(2);expect(resumed.firstVisit).toBe(false);
  expect((await teacher.put(API+'/progress',{data:{step:3,completed:false,version:before.version}})).status()).toBe(409);
  const end=await ok(teacher.put(API+'/progress',{data:{step:5,completed:true,version:saved.version}}));expect(end.completed).toBe(true);
  expect((await ok(admin.get(API+'/progress'))).step).toBe(0);
});
test('student draft does not write; forged hash, concurrent approval, replay and ownership are safe',async()=>{
  const before=(await catalog('students')).length;
  const d=await draft('STUDENT_CREATE',{name:'새학생 '+suffix,grade:'중2',schoolName:'새봄중'});
  expect(d.status).toBe('PENDING');expect((await catalog('students')).length).toBe(before);
  expect((await admin.post(API+'/drafts/'+d.id+'/approve',{data:{planHash:'forged'}})).status()).toBe(409);
  const responses=await Promise.all(Array.from({length:6},()=>admin.post(API+'/drafts/'+d.id+'/approve',{data:{planHash:d.planHash}})));
  expect(responses.every(r=>[200,409].includes(r.status()))).toBe(true);
  const result=await approve(d);expect(result.status).toBe('VERIFIED');createdStudent=result.result.recordId;
  expect((await catalog('students')).length).toBe(before+1);
  expect((await other.post(API+'/drafts/'+d.id+'/approve',{data:{planHash:d.planHash}})).status()).toBe(404);
  const history=await ok(admin.get(API+'/drafts'));expect(JSON.stringify(history)).not.toContain('authKey');
  const audit=await ok(admin.get(API+'/audit'));expect(audit.filter(x=>x.draftId===d.id&&x.event==='APPROVED')).toHaveLength(1);
});
test('class creation, assignment, updates and stale target checks reuse actual APIs',async()=>{
  let result=await approve(await draft('CLASS_CREATE',{name:'영어 심화 '+suffix,teacherId:101,description:'통합 비서 검증'}));
  expect(result.status).toBe('VERIFIED');createdClass=result.result.recordId;
  result=await approve(await draft('CLASS_ASSIGN',{classId:createdClass,studentId:createdStudent}));expect(result.status).toBe('VERIFIED');
  result=await approve(await draft('STUDENT_UPDATE',{studentId:createdStudent,name:'새학생 수정 '+suffix}));expect(result.status).toBe('VERIFIED');
  const updated=await ok(admin.get('/academycraft/api/rea/7/students/'+createdStudent));expect(updated.grade).toBe('중2');expect(updated.schoolName).toBe('새봄중');
  const stale=await draft('CLASS_UPDATE',{classId:createdClass,name:'적용되면 안됨'});
  await ok(admin.put('/academycraft/api/rea/7/classes/'+createdClass,{data:{name:'수동 변경 '+suffix}}));
  expect((await approve(stale)).status).toBe('INVALIDATED');
  result=await approve(await draft('CLASS_UPDATE',{classId:createdClass,name:'최종 반 '+suffix}));expect(result.status).toBe('VERIFIED');
});
test('attendance and consultation create/message verify persisted records',async()=>{
  let result=await approve(await draft('ATTENDANCE_RECORD',{studentId:createdStudent,eventType:'ABSENT',occurredAt:new Date().toISOString().slice(0,10)+'T10:20',note:'통합 비서 출결 검증'}));
  expect(result.status).toBe('VERIFIED');
  result=await approve(await draft('CONSULT_CREATE',{studentId:createdStudent,title:'학습 상담 '+suffix,content:'다음 주 학습 계획을 확인했습니다.'}));
  expect(result.status).toBe('VERIFIED');consultation=result.result.recordId;
  result=await approve(await draft('CONSULT_MESSAGE',{consultId:consultation,content:'후속 상담 내용을 남깁니다.'}));expect(result.status).toBe('VERIFIED');
});
test('expiry, cancellation, live permission revocation and unknown outcome never replay writes',async()=>{
  let d=await draft('STUDENT_CREATE',{name:'만료 '+suffix});
  await seedFixtureSql("UPDATE f2r_unified_test.unified_assistant_drafts SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id='"+d.id+"';");
  expect((await approve(d)).status).toBe('EXPIRED');
  d=await draft('STUDENT_CREATE',{name:'취소 '+suffix});
  expect((await ok(admin.post(API+'/drafts/'+d.id+'/cancel',{data:{planHash:d.planHash}}))).status).toBe('CANCELLED');
  expect((await admin.post(API+'/drafts/'+d.id+'/approve',{data:{planHash:d.planHash}})).status()).toBe(409);
  d=await draft('STUDENT_CREATE',{name:'권한회수 '+suffix});
  await seedFixtureSql("UPDATE ac_unified_test.rea_members SET role='TEACHER' WHERE id=101;");
  try {expect((await admin.post(API+'/drafts/'+d.id+'/approve',{data:{planHash:d.planHash}})).status()).toBe(403);}
  finally {await seedFixtureSql("UPDATE ac_unified_test.rea_members SET role='ACADEMY_OWNER' WHERE id=101;");}
  await ok(admin.post(API+'/drafts/'+d.id+'/cancel',{data:{planHash:d.planHash}}));
  d=await draft('STUDENT_CREATE',{name:'중단 상태 '+suffix});
  await seedFixtureSql("UPDATE f2r_unified_test.unified_assistant_drafts SET status='EXECUTING' WHERE id='"+d.id+"';");
  expect((await admin.post(API+'/drafts/'+d.id+'/approve',{data:{planHash:d.planHash}})).status()).toBe(409);
});
test('CSV and natural ledger entry preserve evidence, store once, count reviews and enforce business access',async()=>{
  const occurredOn=new Date().toISOString().slice(0,10);
  const csv='거래일,구분,금액,거래처,적요,결제수단,원본계정,증빙유형\n'+occurredOn+',지출,350000,KT '+suffix+',전화요금,계좌이체,전화/휴대폰/인터넷,INVOICE\n';
  const d=await ok(admin.post(API+'/imports?businessId=1',{multipart:{file:{name:'test-'+suffix+'.csv',mimeType:'text/csv',buffer:Buffer.from(csv)}}}));
  expect(d.preview.rows[0].createdTxnId).toBeNull();expect(d.preview.rows[0].evidenceType).toBe('INVOICE');
  let result=await approve(d);expect(result.result.saved).toBe(1);expect(result.result.verified).toBe(true);
  expect((await approve(d)).result.saved).toBe(1);
  expect((await other.post(API+'/drafts/'+d.id+'/approve',{data:{planHash:d.planHash}})).status()).toBe(404);
  const duplicate=await ok(admin.post(API+'/imports?businessId=1',{multipart:{file:{name:'copy.csv',mimeType:'text/csv',buffer:Buffer.from(csv)}}}));
  result=await approve(duplicate);expect(result.result.duplicates).toBe(1);expect(result.result.saved).toBe(0);
  const manual=await draft('LEDGER_ENTRY',{occurredOn,direction:'EXPENSE',amount:7123,counterparty:'미분류 검증 '+suffix,description:'검토 필요 거래',evidenceType:'NONE'},null,1);
  result=await approve(manual);expect(result.result.saved).toBe(1);expect(result.result.review).toBeGreaterThan(0);expect(result.status).toBe('REVIEW_REQUIRED');
  expect((await other.post(API+'/imports?businessId=1',{multipart:{file:{name:'no.csv',mimeType:'text/csv',buffer:Buffer.from(csv)}}})).status()).toBe(403);
  const overview=await ok(admin.get(API+'/overview?businessId=1'));expect(overview.academy.studentCount).toBeGreaterThan(0);expect(overview.finance.businessId).toBe(1);expect(overview.finance.expense).toBeGreaterThanOrEqual(350000);
});
test('natural command and desktop/mobile workbench render, highlight, persist and approve through UI',async({browser})=>{
  const output=path.resolve(__dirname,'../../docs/unified-ai-assistant/screenshots');fs.mkdirSync(output,{recursive:true});
  const errors=[];
  const context=await browser.newContext({timezoneId:'Asia/Seoul',storageState:await admin.storageState(),viewport:{width:1440,height:1050}});
  const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.goto('/dashboard');await expect(page.locator('#uaRole')).toContainText('원장 연결');
  await expect(page.locator('#uaTour')).toBeVisible();await page.locator('#uaNext').click();
  await expect(page.locator('#uaStudents')).toHaveClass(/ua-tour-target/);
  await page.reload();await expect(page.locator('#uaStep')).toContainText('2 / 6');
  await page.locator('#uaCommand').fill('학생 UI하늘 등록해줘, 학교: 새봄중, 학년: 중2');
  await page.locator('#uaCommandForm button').click();await expect(page.locator('#uaField_name')).toHaveValue('UI하늘');
  await page.locator('#uaCreateDraft').click();await expect(page.locator('#uaDrafts .ua-draft').first()).toContainText('UI하늘');
  await page.screenshot({path:path.join(output,'admin-desktop-draft-1440.png'),fullPage:true});
  await page.locator('#uaDrafts .ua-draft').first().getByRole('button',{name:'승인하고 실행'}).click();
  await expect(page.locator('#uaDrafts .ua-draft').first()).toContainText('저장 검증 완료');
  await page.screenshot({path:path.join(output,'admin-desktop-result-1440.png'),fullPage:true});
  await context.close();
  for(const [role,ctx,width,height] of [['admin',admin,390,844],['tutor',teacher,390,844],['tutor',teacher,1440,1050]]) {
    const c=await browser.newContext({timezoneId:'Asia/Seoul',storageState:await ctx.storageState(),viewport:{width,height},isMobile:width<500,hasTouch:width<500});
    const p=await c.newPage();p.on('pageerror',e=>errors.push(e.message));await p.goto('/dashboard');
    await expect(p.locator('#uaRole')).toContainText(role==='admin'?'원장 연결':'강사 연결');
    await expect(p.locator('#uaSummary')).not.toContainText('불러오는 중');
    await expect(p.locator('#uaDrafts')).not.toContainText('불러오는 중');
    if(role==='admin')await expect(p.locator('#uaDrafts .ua-draft').first()).toBeVisible();
    expect(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await p.screenshot({path:path.join(output,role+'-'+width+'.png'),fullPage:true});await c.close();
  }
  expect(errors).toEqual([]);
});
test('login surfaces welcome link without moving the current service',async({browser})=>{
  const c=await browser.newContext({timezoneId:'Asia/Seoul',storageState:await admin.storageState()});const p=await c.newPage();await p.goto('/home');
  await expect(p.locator('#assistantWelcome')).toBeVisible();expect(new URL(p.url()).pathname).toBe('/home');
  await expect(p.locator('#assistantWelcome a')).toHaveAttribute('href','/dashboard');await p.locator('#assistantWelcome button').click();await expect(p.locator('#assistantWelcome')).toHaveCount(0);await c.close();
});
