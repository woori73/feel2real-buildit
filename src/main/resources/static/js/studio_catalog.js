(function(root,factory){
  const model=factory();
  if(typeof module==='object'&&module.exports)module.exports=model;
  else root.StudioCatalog=model;
})(typeof window!=='undefined'?window:this,function(){
  'use strict';
  function media(row){
    const ext=String(row.extension||'').toLowerCase();
    if(['mp4','mov'].includes(ext))return 'VIDEO';
    if(['png','jpg','jpeg','webp'].includes(ext))return 'IMAGE';
    if(['mp3','wav','flac'].includes(ext))return 'AUDIO';
    if(['zip','glb','gltf'].includes(ext))return 'PACKAGE';
    return 'DOCUMENT';
  }
  function status(row){
    if(row.entry_type==='FILE')return '검수 상태 미등록';
    if(row.approved_id&&row.id===row.approved_id)return '감독 승인본';
    if(row.last_review==='REJECT')return '반려';
    if(row.last_review==='REQUEST_CHANGES')return '수정 요청';
    if(row.work_status==='REVIEW')return '승인 대기';
    return ({WORKING:'제작 중',READY:'준비 완료',FAILED:'실패',WAITING:'작업 대기'})[row.work_status]||'미승인';
  }
  function params(values,offset=0){
    const q=new URLSearchParams({section:values.section||'materials',versions:values.versions||'latest',limit:'41',offset:String(offset)});
    for(const key of ['episode','kind','media','status','q'])if(values[key])q.set(key,values[key]);
    return q;
  }
  function partition(rows){return {media:rows.filter(r=>['VIDEO','IMAGE','AUDIO'].includes(media(r))),documents:rows.filter(r=>!['VIDEO','IMAGE','AUDIO'].includes(media(r)))};}
  return {media,status,params,partition};
});
