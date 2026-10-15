(()=>{
  document.querySelectorAll('.delete-post').forEach(btn=>{
    btn.addEventListener('click',async()=>{
      if(btn.dataset.busy)return;
      if(!confirm('이 기록을 삭제할까요?'))return;
      btn.dataset.busy='1';btn.disabled=true;
      try{
        const id=btn.dataset.postId;
        const r=await fetch(`/api/admin/choonggi/posts/${id}`,{method:'DELETE',credentials:'same-origin'});
        if(!r.ok){
          let m='삭제에 실패했습니다.';
          try{const j=await r.json();if(j&&j.message)m=j.message}catch(e){}
          alert(m);btn.disabled=false;delete btn.dataset.busy;return;
        }
        location.reload();
      }catch(e){
        alert('삭제에 실패했습니다.');btn.disabled=false;delete btn.dataset.busy;
      }
    });
  });
})();
