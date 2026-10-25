const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {policy}=require('../../main/resources/static/js/f2r_spatial.js');
test('given 저사양·데이터 절약·모션 감소 설정 when 렌더 정책을 정하면 then 3D를 자동으로 불러오지 않는다',()=>{
  for(const env of [{memory:2},{cores:2},{saveData:true},{connection:'slow-2g'},{connection:'2g'},{reduced:true},{preference:'2d'},{webgl:false}])assert.ok(policy(env));
  assert.equal(policy({memory:8,cores:8,connection:'4g',preference:'3d'}),null);
});
test('given 실제 GLB 환경 when 파일을 읽으면 then 네 모델·6개 패널·3개 링과 경량 예산을 충족한다',()=>{
  const dir=path.resolve(__dirname,'../../main/resources/static/models/f2r-space');
  const b=fs.readFileSync(path.join(dir,'f2r-environment.glb'));
  assert.equal(b.toString('ascii',0,4),'glTF');assert.equal(b.readUInt32LE(4),2);assert.equal(b.readUInt32LE(8),b.length);
  const g=JSON.parse(b.toString('utf8',20,20+b.readUInt32LE(12)).trim());
  assert.equal(g.nodes.filter(n=>/^0[1-4]_/.test(n.name||'')).length,4);
  assert.equal(g.nodes.filter(n=>/^service_[0-5]$/.test(n.name||'')).length,6);
  assert.equal(g.nodes.filter(n=>/^orbit_[0-2]$/.test(n.name||'')).length,3);
  assert.equal(g.textures?.length||0,0);assert.ok(b.length<400000);
  assert.ok(g.nodes.filter(n=>n.mesh!==undefined).length<=32);
});
