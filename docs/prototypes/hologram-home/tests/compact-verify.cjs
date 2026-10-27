// Verify measured before/after behavior and accessibility, not CSS declarations.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const out=path.resolve(__dirname,'../evidence/compact'),before=JSON.parse(fs.readFileSync(out+'/before.json')),after=JSON.parse(fs.readFileSync(out+'/after.json')),rows=[];
for(const a of after){const b=before.find(x=>x.width===a.width),row={width:a.width,checks:[]};rows.push(row);const check=(name,fn)=>{try{fn();row.checks.push({name,status:'PASS'});}catch(e){row.checks.push({name,status:'FAIL',error:String(e)});process.exitCode=1;}};
check('before and after captures complete without JS errors',()=>{assert.equal(a.status,'PASS');assert.equal(b.status,'PASS');assert.deepEqual(a.errors,[]);assert.deepEqual(b.errors,[]);});
for(const k of ['command','stage','assistant'])check(k+' width and height shrink by 20–25%',()=>{for(const d of ['width','height']){const ratio=a.metrics[k][d]/b.metrics[k][d];assert.ok(ratio>=.75&&ratio<=.8,`${k} ${d}: ${ratio}`);}});
check('zero page overflow and no clipped names/menu labels',()=>{assert.equal(a.metrics.overflow,0);assert.deepEqual(a.metrics.clipped,[]);});
check('all visible assistant and intake controls retain 44px hit targets',()=>assert.ok(a.metrics.touch.every(t=>t.width>=43.9&&t.height>=43.9)));
check('readable menu type and mobile 16px input',()=>{assert.ok(a.metrics.title.font>=14);assert.ok(a.metrics.subtitle.font>=12);if(a.width<768)assert.ok(a.metrics.input.font>=16);});
check('whole video remains contained; 3D canvas shrinks on both axes',()=>{assert.equal(a.metrics.video.fit,'contain');assert.ok(a.model.width<b.model.width&&a.model.height<b.model.height);});
check('complete conversation remains keyboard-scrollable',()=>{assert.ok(a.keyboardScroll.bottom>0);assert.equal(a.keyboardScroll.top,0);});
}
fs.writeFileSync(out+'/resize-checks.json',JSON.stringify(rows,null,2));console.log(JSON.stringify({pass:rows.flatMap(r=>r.checks).filter(c=>c.status==='PASS').length,fail:rows.flatMap(r=>r.checks).filter(c=>c.status==='FAIL').length}));
