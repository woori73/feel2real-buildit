(function(){
  var d=document;d.documentElement.classList.remove('no-js');
  var h=d.querySelector('.hdr');addEventListener('scroll',function(){h.classList.toggle('scrolled',scrollY>8)},{passive:true});
  var io=new IntersectionObserver(function(es){es.forEach(function(e){if(e.isIntersecting){e.target.classList.add('in');io.unobserve(e.target)}})},{threshold:.1});
  d.querySelectorAll('.rv').forEach(function(el){io.observe(el)});
  var tabs=[].slice.call(d.querySelectorAll('.svc'));
  function sel(t){tabs.forEach(function(x){x.setAttribute('aria-selected',x===t)});d.querySelectorAll('.pane').forEach(function(p){p.classList.toggle('on',p.id===t.getAttribute('aria-controls'))})}
  tabs.forEach(function(t,i){t.addEventListener('click',function(){sel(t)});t.addEventListener('keydown',function(e){var n=e.key==='ArrowRight'?i+1:e.key==='ArrowLeft'?i-1:null;if(n===null)return;var x=tabs[(n+tabs.length)%tabs.length];x.focus();sel(x)})});
  var inp=d.getElementById('ask');
  d.querySelectorAll('.chip').forEach(function(c){c.addEventListener('click',function(){inp.value=c.textContent;inp.focus()})});
  /* 입력창 placeholder 타자 효과 (모션 감소 시 정지) */
  var ex=['초등·중등 영어학원, 밝고 세련되게','입시·내신 전문 학원, 신뢰감 있게','영어회화 학원, 활기차게'],i=0,j=0,del=false;
  if(!matchMedia('(prefers-reduced-motion:reduce)').matches){(function tick(){var s=ex[i];j+=del?-1:1;inp.placeholder='예) '+s.slice(0,j);var t=del?25:55;if(!del&&j===s.length){del=true;t=1600}else if(del&&j===0){del=false;i=(i+1)%ex.length;t=400}setTimeout(tick,t)})()}else{inp.placeholder='예) '+ex[0]}
})();
