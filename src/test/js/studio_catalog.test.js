const {test}=require('node:test');const assert=require('node:assert/strict');
const catalog=require('../../main/resources/static/js/studio_catalog.js');
test('given media and MD when grouped then reports never enter the viewing list',()=>{
 const rows=[{id:'v',kind:'REVIEW',extension:'mp4'},{id:'md',kind:'REVIEW',extension:'md'},{id:'i',extension:'webp'},{id:'a',extension:'flac'},{id:'z',extension:'zip'}];
 const grouped=catalog.partition(rows);assert.deepEqual(grouped.media.map(r=>r.id),['v','i','a']);assert.deepEqual(grouped.documents.map(r=>r.id),['md','z']);
});
test('given AI review or new candidate when labelled then no inferred approval',()=>{
 assert.equal(catalog.status({id:'new',approved_id:'old',work_status:'REVIEW',origin:'AI_PROPOSAL'}),'승인 대기');
 assert.equal(catalog.status({id:'old',approved_id:'old',work_status:'REVIEW'}),'감독 승인본');
 assert.equal(catalog.status({id:'x',entry_type:'FILE',work_status:'REVIEW'}),'검수 상태 미등록');
 assert.equal(catalog.status({id:'new',work_status:'REVIEW',last_review:'REJECT'}),'반려');
});
test('given project catalogue filters when paged then common scope, filename, status and cursor survive',()=>{
 const q=catalog.params({section:'review',episode:'common',q:'S1 & S5 v11.mp4',status:'PENDING',versions:'all'},40);
 assert.equal(q.get('q'),'S1 & S5 v11.mp4');assert.equal(q.get('episode'),'common');assert.equal(q.get('status'),'PENDING');assert.equal(q.get('versions'),'all');assert.equal(q.get('offset'),'40');assert.equal(q.get('limit'),'41');
});
