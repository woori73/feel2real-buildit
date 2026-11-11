const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {webcrypto} = require('node:crypto');
const corePath = process.env.GRAMMAR_BATCH_CORE || path.join(__dirname, '../../main/resources/static/problem-generator/grammar-review-batch.js');
const sandbox = {module:{exports:{}}, crypto:webcrypto, TextEncoder, Uint8Array, Set, Map};
vm.runInNewContext(fs.readFileSync(corePath,'utf8'), sandbox);
const core = sandbox.module.exports;

function fixture() {
  let question = {questionId:1,externalRef:'CHECK-1',questionText:'Write a complete sentence.',choices:[],correctAnswer:'A clear answer',explanation:'Source note',questionFormat:'WRITING',recommendedGrade:null,reviewStatus:'NEEDS_REVIEW'};
  const writes = [];
  let failProfile = false;
  async function request(url, options) {
    if (url.startsWith('/questions?')) return {content:url.includes('REJECTED')?[]:[{questionId:1,externalRef:'CHECK-1'}],totalPages:1};
    assert.equal(url.split('/')[2], '1');
    if (!options) return structuredClone(question);
    writes.push({url,method:options.method});
    if (url.endsWith('/profile') && failProfile) { failProfile=false; throw new Error('temporary profile error'); }
    const patch = JSON.parse(options.body);
    question = {...question,...patch,reviewStatus:url.endsWith('/review')?patch.status:'NEEDS_REVIEW'};
    return structuredClone(question);
  }
  return {request,writes,get question(){return question;},set question(value){question=value;},failNextProfile(){failProfile=true;}};
}
async function entryFor(f) {
  const patch = {correctAnswer:'A normalized answer',recommendedGrade:'H2',status:'APPROVED'};
  return {externalRef:'CHECK-1',expectedHash:await core.fingerprint(f.question),resultHash:await core.fingerprint({...f.question,...patch}),expectedStatus:'NEEDS_REVIEW',expectedProfile:{recommendedGrade:'UNKNOWN'},patch};
}

test('given reviewed content, saves content and grade before approval and safely replays',async()=>{
  const f=fixture(), e=await entryFor(f);
  const result=await core.applyPlan([e],f.request,()=>{},()=>false);
  assert.equal(result[0].outcome,'APPLIED');
  assert.deepEqual(f.writes.map(x=>x.url),['/questions/1','/questions/1/profile','/questions/1/review']);
  assert.equal(f.question.reviewStatus,'APPROVED');
  const replay=await core.applyPlan([e],f.request,()=>{},()=>false);
  assert.equal(replay[0].outcome,'UNCHANGED');
  assert.equal(f.writes.length,3);
});
test('given a human edit after review, refuses overwriting even an already approved target',async()=>{
  const f=fixture(), e=await entryFor(f);
  f.question={...f.question,...e.patch,reviewStatus:'APPROVED',questionText:'A later human edit'};
  const result=await core.applyPlan([e],f.request,()=>{},()=>false);
  assert.equal(result[0].outcome,'FAILED');
  assert.equal(f.writes.length,0);
});
test('given a partial failure, keeps needs-review and resumes without repeating the content edit',async()=>{
  const f=fixture(), e=await entryFor(f);
  f.failNextProfile();
  assert.equal((await core.applyPlan([e],f.request,()=>{},()=>false))[0].outcome,'FAILED');
  assert.equal(f.question.reviewStatus,'NEEDS_REVIEW');
  assert.equal((await core.applyPlan([e],f.request,()=>{},()=>false))[0].outcome,'APPLIED');
  assert.equal(f.writes.filter(x=>x.url==='/questions/1').length,1);
});
test('given a different reviewer decision, reports conflict before writes',async()=>{
  const f=fixture(), e=await entryFor(f);
  f.question={...f.question,reviewStatus:'REJECTED'};
  assert.equal((await core.applyPlan([e],f.request,()=>{},()=>false))[0].outcome,'FAILED');
  assert.equal(f.writes.length,0);
});
test('rejects duplicate IDs and permission fields during validation',async()=>{
  const f=fixture(), e=await entryFor(f);
  assert.throws(()=>core.validatePlan([e,e]));
  assert.throws(()=>core.validatePlan([{...e,patch:{rightsStatus:'OWNED'}}]));
});
