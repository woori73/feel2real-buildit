(()=>{
  const root=document.querySelector('.editor');if(!root)return;
  let id=root.dataset.postId||null;
  const $=s=>document.querySelector(s);
  const msg=(html)=>{$('#message').innerHTML=html};
  const payload=()=>({title:$('#title').value,body:$('#body').value,youtubeUrl:$('#youtube').value});

  async function errorMessage(r,fallback){
    try{const j=await r.clone().json();if(j&&j.message)return j.message}catch(e){}
    try{const t=await r.text();if(t)return t}catch(e){}
    return fallback;
  }
  async function call(url,opt={}){
    const r=await fetch(url,{credentials:'same-origin',...opt,headers:{'Content-Type':'application/json',...(opt.headers||{})}});
    if(!r.ok)throw new Error(await errorMessage(r,'요청 실패'));
    return r.status===204?null:r.json();
  }
  // Saves the post first, then uploads every newly picked photo and waits for each to
  // finish persisting before returning, so publish/draft never act on media that hasn't
  // landed in storage yet.
  async function save(){
    const p=await call(id?`/api/admin/choonggi/posts/${id}`:'/api/admin/choonggi/posts',{method:id?'PUT':'POST',body:JSON.stringify(payload())});
    id=p.id;root.dataset.postId=id;
    history.replaceState(null,'',`/choonggi/admin/posts/${id}/edit`);
    return p;
  }
  async function uploadPending(){
    const input=$('#media');const files=[...input.files];
    for(const file of files){
      const fd=new FormData();fd.append('file',file);
      const r=await fetch(`/api/admin/choonggi/posts/${id}/media`,{method:'POST',body:fd,credentials:'same-origin'});
      if(!r.ok)throw new Error(await errorMessage(r,'사진 저장에 실패했습니다.'));
    }
    input.value='';$('#preview').innerHTML='';
    return files.length;
  }
  // Disables the clicked button and ignores re-clicks while a request from it is
  // already in flight, so double-clicking a button can't fire it twice.
  function withBusy(btn,fn){
    return async()=>{
      if(!btn||btn.dataset.busy)return;
      btn.dataset.busy='1';btn.disabled=true;msg('');
      try{await fn()}catch(e){msg(escapeHtml(e.message))}
      finally{delete btn.dataset.busy;btn.disabled=false}
    };
  }
  function escapeHtml(s){const d=document.createElement('div');d.textContent=s;return d.innerHTML}

  $('#media').onchange=()=>{
    const box=$('#preview');box.innerHTML='';
    [...$('#media').files].forEach(f=>{
      const fig=document.createElement('figure'),img=document.createElement('img');
      img.src=URL.createObjectURL(f);fig.append(img);box.append(fig);
    });
  };

  function extractYoutubeId(url){
    let u;try{u=new URL(url)}catch(e){return null}
    if(u.protocol!=='https:')return null;
    const host=u.hostname.toLowerCase();
    const ID=/^[A-Za-z0-9_-]{6,20}$/;
    if(host==='youtu.be'||host.endsWith('.youtu.be')){
      const seg=u.pathname.replace(/^\//,'').split('/')[0];
      return ID.test(seg)?seg:null;
    }
    if(host==='youtube.com'||host.endsWith('.youtube.com')){
      const v=u.searchParams.get('v');
      if(v&&ID.test(v))return v;
      const m=u.pathname.match(/^\/(?:shorts|embed|live)\/([A-Za-z0-9_-]{6,20})/);
      return m?m[1]:null;
    }
    return null;
  }
  let ytTimer;
  $('#youtube').addEventListener('input',()=>{
    clearTimeout(ytTimer);
    ytTimer=setTimeout(()=>{
      const url=$('#youtube').value.trim();
      const box=$('#youtube-preview');box.innerHTML='';
      if(!url)return;
      const vid=extractYoutubeId(url);
      if(vid){
        const img=document.createElement('img');
        img.src=`https://img.youtube.com/vi/${vid}/hqdefault.jpg`;img.alt='YouTube 미리보기';img.className='yt-thumb';
        box.append(img);
      } else {
        box.textContent='올바른 YouTube 링크를 넣어주세요.';
      }
    },300);
  });

  $('#upload-publish').onclick=withBusy($('#upload-publish'),async()=>{
    await save();
    await uploadPending();
    await call(`/api/admin/choonggi/posts/${id}/publish`,{method:'POST'});
    msg('✓ 올라갔습니다. <a href="/choonggi/admin">내 기록 보기</a>');
  });
  $('#save-draft').onclick=withBusy($('#save-draft'),async()=>{
    await save();
    await uploadPending();
    msg('✓ 저장됨');
  });
  $('#unpublish')?.addEventListener('click',withBusy($('#unpublish'),async()=>{
    await call(`/api/admin/choonggi/posts/${id}/unpublish`,{method:'POST'});
    location.reload();
  }));

  $('#existing-media')?.addEventListener('click',ev=>{
    const btn=ev.target.closest('button');if(!btn)return;
    const fig=btn.closest('figure');const mediaId=fig&&fig.dataset.mediaId;if(!mediaId)return;
    withBusy(btn,async()=>{
      if(btn.classList.contains('set-cover'))await call(`/api/admin/choonggi/posts/${id}/cover/${mediaId}`,{method:'POST'});
      else if(btn.classList.contains('move-up'))await call(`/api/admin/choonggi/posts/${id}/media/${mediaId}/move-up`,{method:'POST'});
      else if(btn.classList.contains('move-down'))await call(`/api/admin/choonggi/posts/${id}/media/${mediaId}/move-down`,{method:'POST'});
      else if(btn.classList.contains('delete-media')){
        if(!confirm('이 사진을 삭제할까요?'))return;
        await call(`/api/admin/choonggi/posts/${id}/media/${mediaId}`,{method:'DELETE'});
      } else return;
      location.reload();
    })();
  });
})();
