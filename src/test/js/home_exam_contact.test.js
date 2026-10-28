const test = require('node:test');
const assert = require('node:assert/strict');
const {copyAddress} = require('../../main/resources/static/js/home_exam_contact.js');
test('given working clipboard when copied then reports success only after completion', async () => {
  let done; let result = false;
  const promise = copyAddress('woorinaesin@naver.com', {writeText: address => {assert.equal(address,'woorinaesin@naver.com');return new Promise(resolve=>{done=resolve;});}}).then(value=>result=value);
  await Promise.resolve(); assert.equal(result,false); done(); await promise; assert.equal(result,true);
});
for (const [name,clipboard] of [['missing',undefined],['rejected',{writeText:()=>Promise.reject(new Error('denied'))}],['throws',{writeText:()=>{throw new Error('denied');}}]]) {
  test(`given ${name} clipboard when copying then returns false without an unhandled error`,async()=>assert.equal(await copyAddress('woorinaesin@naver.com',clipboard),false));
}
