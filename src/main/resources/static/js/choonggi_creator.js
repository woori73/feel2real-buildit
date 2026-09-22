(()=>{
  const root=document.querySelector('.editor');if(!root)return;
  let id=root.dataset.postId||null;
  const $=s=>document.querySelector(s), msg=t=>{$('#message').textContent=t};
  const payload=()=>({episode:$('#episode').value,title:$('#title').value,slug:$('#slug').value,body:$('#body').value});

  async function call(url,opt={}){
    const r=await fetch(url,{credentials:'same-origin',...opt,headers:{'Content-Type':'application/json',...(opt.headers||{})}});
    if(!r.ok)throw new Error((await r.text())||'요청 실패');
    return r.status===204?null:r.json();
  }
  async function save(){
    const p=await call(id?`/api/admin/choonggi/posts/${id}`:'/api/admin/choonggi/posts',{method:id?'PUT':'POST',body:JSON.stringify(payload())});
    id=p.id;root.dataset.postId=id;
    history.replaceState(null,'',`/choonggi/admin/posts/${id}/edit`);
    return p;
  }
  // Uploads every file currently selected and waits for each to finish persisting
  // before returning, so callers (publish, preview) never act on a post whose
  // just-picked media hasn't landed in storage yet.
  async function uploadPending(){
    const input=$('#media');
    const files=[...input.files];
    for(const file of files){
      const fd=new FormData();fd.append('file',file);
      const r=await fetch(`/api/admin/choonggi/posts/${id}/media`,{method:'POST',body:fd,credentials:'same-origin'});
      if(!r.ok)throw new Error(await r.text());
    }
    input.value='';$('#preview').innerHTML='';
    return files.length;
  }
  // Disables the clicked button and ignores re-clicks while a request from it is
  // already in flight, so double-clicking publish (or anything else) can't fire twice.
  function withBusy(btn,fn){
    return async()=>{
      if(!btn||btn.dataset.busy)return;
      btn.dataset.busy='1';btn.disabled=true;
      try{await fn()}catch(e){msg(e.message)}
      finally{delete btn.dataset.busy;btn.disabled=false}
    };
  }

  $('#media').onchange=()=>{
    const box=$('#preview');box.innerHTML='';
    [...$('#media').files].forEach(f=>{
      const fig=document.createElement('figure'),el=document.createElement(f.type.startsWith('video/')?'video':'img');
      el.src=URL.createObjectURL(f);if(el.tagName==='VIDEO')el.controls=true;
      fig.append(el);box.append(fig);
    });
  };
  $('#save').onclick=withBusy($('#save'),async()=>{
    await save();
    const uploaded=await uploadPending();
    if(uploaded>0){location.reload();return}
    msg('임시저장 완료');
  });
  $('#publish')?.addEventListener('click',withBusy($('#publish'),async()=>{
    await save();
    await uploadPending();
    await call(`/api/admin/choonggi/posts/${id}/publish`,{method:'POST'});
    location.reload();
  }));
  $('#unpublish')?.addEventListener('click',withBusy($('#unpublish'),async()=>{
    await call(`/api/admin/choonggi/posts/${id}/unpublish`,{method:'POST'});
    location.reload();
  }));
  $('#open-preview').onclick=withBusy($('#open-preview'),async()=>{
    await save();
    await uploadPending();
    window.open(`/choonggi/admin/posts/${id}/preview`,'_blank');
  });

  $('#existing-media')?.addEventListener('click',ev=>{
    const btn=ev.target.closest('button');if(!btn)return;
    const fig=btn.closest('figure');const mediaId=fig&&fig.dataset.mediaId;if(!mediaId)return;
    withBusy(btn,async()=>{
      if(btn.classList.contains('set-cover'))await call(`/api/admin/choonggi/posts/${id}/cover/${mediaId}`,{method:'POST'});
      else if(btn.classList.contains('move-up'))await call(`/api/admin/choonggi/posts/${id}/media/${mediaId}/move-up`,{method:'POST'});
      else if(btn.classList.contains('move-down'))await call(`/api/admin/choonggi/posts/${id}/media/${mediaId}/move-down`,{method:'POST'});
      else if(btn.classList.contains('delete-media')){
        if(!confirm('이 미디어를 삭제할까요?'))return;
        await call(`/api/admin/choonggi/posts/${id}/media/${mediaId}`,{method:'DELETE'});
      } else return;
      location.reload();
    })();
  });
})();
