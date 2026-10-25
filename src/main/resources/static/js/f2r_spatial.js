/* Progressive enhancement: original forms, services, media and auth remain owners. */
(function () {
  'use strict';
  function policy(env) {
    if (env.preference === '2d') return 'preference';
    if (env.webgl === false) return 'webgl-unavailable';
    if (env.reduced) return 'reduced-motion';
    if (env.saveData || /(^|-)2g$/.test(env.connection || '')) return 'data-saver';
    if ((env.memory && env.memory <= 2) || (env.cores && env.cores <= 2)) return 'low-power';
    return null;
  }
  if (typeof module !== 'undefined') module.exports = { policy: policy };
  if (typeof document === 'undefined') return;
  function boot() {
    var stage = document.getElementById('homeStage') || document.getElementById('agentStage');
    if (!stage) return;
    var controller = null, pending = false, visible = false, wanted = true, generation = 0;
    var preference = null;
    try { preference = localStorage.getItem('f2r.space.mode'); } catch (_) {}
    var reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
    var connection = navigator.connection || {};
    var reason = policy({ preference: preference, reduced: reduced.matches, saveData: connection.saveData,
      connection: connection.effectiveType, memory: navigator.deviceMemory, cores: navigator.hardwareConcurrency });
    var controls = document.createElement('div'); controls.className = 'f2r-space-controls';
    var label = document.createElement('span'); label.textContent = 'AI 비서 공간';
    var toggle = document.createElement('button');toggle.type = 'button';toggle.textContent = '3D 공간 켜기';toggle.setAttribute('aria-pressed', 'false');
    var reset = document.createElement('button');reset.type = 'button';reset.textContent = '시점 초기화';reset.hidden = true;
    controls.append(label,toggle,reset);stage.insertAdjacentElement('afterend',controls);
    function setPreference(value) { try {localStorage.setItem('f2r.space.mode',value);} catch (_) {} }
    function fallback(why) {
      controller?.dispose();controller=null;stage.classList.remove('f2r-space-ready');
      stage.dataset.space='2d';stage.dataset.spaceReason=why || 'manual';
      toggle.textContent='3D 공간 켜기';toggle.setAttribute('aria-pressed','false');reset.hidden=true;
      label.textContent=why==='webgl-unavailable'?'2D 화면 · 이 브라우저는 WebGL2를 지원하지 않습니다':why==='error'?'2D 화면 · 3D 연결을 다시 시도할 수 있습니다':'2D 화면';
    }
    async function start(force) {
      if (pending || controller || (!visible && !force) || !wanted || (reason && !force)) return;
      pending=true;var current=++generation;toggle.disabled=true;label.textContent='3D 공간 준비 중…';
      try {
        var mod=await import('/js/f2r_spatial_scene.js?v=20261025-4');
        if (!wanted || current!==generation) return;
        var made=await mod.mount(stage,{onFailure:function(){wanted=false;fallback('error');}});
        if (!wanted || current!==generation) {made.dispose();return;}
        controller=made;toggle.textContent='2D 화면으로';toggle.setAttribute('aria-pressed','true');reset.hidden=false;
        label.textContent=stage.dataset.spaceRenderer==='svg-compatibility'?'검수용 SVG · GPU·VideoTexture 미검증':'3D 공간 · 드래그해서 둘러보기';
      } catch (error) {wanted=false;fallback(error.code==='WEBGL_UNAVAILABLE'?'webgl-unavailable':'error');} finally {pending=false;toggle.disabled=false;}
    }
    toggle.addEventListener('click',function(){
      if (controller) {wanted=false;++generation;setPreference('2d');fallback('manual');}
      else {wanted=true;reason=null;setPreference('3d');start(true);}
    });
    reset.addEventListener('click',function(){controller?.reset();});
    var observer=new IntersectionObserver(function(entries){visible=entries[0].isIntersecting;if(visible)start(false);},{rootMargin:'80px'});observer.observe(stage);
    if(reason)fallback(reason);
    reduced.addEventListener('change',function(e){if(e.matches){reason='reduced-motion';wanted=false;++generation;fallback(reason);}});
    window.addEventListener('pagehide',function(){wanted=false;++generation;controller?.dispose();controller=null;});
    window.addEventListener('pageshow',function(e){if(e.persisted){wanted=true;start(false);}});
  }
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',boot);else boot();
}());
