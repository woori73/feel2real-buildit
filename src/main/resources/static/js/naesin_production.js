"use strict";
(() => {
  const $ = id => document.getElementById(id);
  const el = (tag, text, cls) => { const e = document.createElement(tag); if (text !== undefined) e.textContent = text; if (cls) e.className = cls; return e; };
  const labels = {SOURCE:"원재료",WORK:"제작 과정",RESULT:"완성본 후보",HANDOFF:"AI 인계",EXTRACTION:"본문 추출"};
  const statuses = {READY:"완료",WORKING:"작업 중",WAITING:"대기",REVIEW:"검수 요청",FAILED:"실패"};
  let projectId = new URLSearchParams(location.search).get("projectId") || "", rows = [], files = [], selected = null, role = "VIEWER", archived = false, nextOffset = -1;
  let epoch = 0;
  const message = value => { $("message").textContent = value; };
  const data = r => { try { return JSON.parse(r.content); } catch { return {}; } };
  const url = path => { const u = new URL("/api/naesin-production/" + path, location.origin); if (projectId) u.searchParams.set("projectId", projectId); return u.pathname + u.search; };
  async function api(path, options = {}) {
    const response = await fetch(url(path), {credentials:"same-origin",...options});
    const result = await response.json();
    if (!response.ok) throw Error(result.message || result.detail || "요청을 처리하지 못했습니다.");
    return result;
  }
  const post = (path, body) => api(path, {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
  function bind(id, fn, event = "click") {
    $(id).addEventListener(event, async e => {
      e.preventDefault(); const target = e.submitter || e.currentTarget; target.disabled = true;
      try { await fn(e); } catch (err) { message(err.message); } finally { target.disabled = false; }
    });
  }
  const button = (label, fn) => { const b = el("button", label); b.type = "button"; b.onclick = async () => { b.disabled = true; try { await fn(); } catch(e) {message(e.message);} finally {b.disabled = false;} }; return b; };
  function options(select, values, empty) {
    const chosen = new Set(Array.from(select.selectedOptions).map(x=>x.value));
    select.replaceChildren();
    if (empty) { const option = el("option", "파일 없음"); option.value = ""; select.append(option); }
    values.forEach(([id,title])=>{const o=el("option",title);o.value=id;o.selected=chosen.has(id);select.append(o);});
  }
  async function projects() {
    const items = await api("projects");
    $("project").replaceChildren(el("option", "프로젝트 선택")); $("project").firstChild.value="";
    items.forEach(item=>{const o=el("option",item.title);o.value=item.id;$("project").append(o);});
    if (!items.some(item=>item.id===projectId)) projectId="";
    $("project").value=projectId;
    if (projectId) await refresh(); else $("workspace").hidden=true;
  }
  async function refresh(append = false) {
    if (!projectId) return;
    const current = ++epoch, id = projectId;
    const result = await api("state?q="+encodeURIComponent($("query").value)+"&offset="+(append?nextOffset:0));
    if (current !== epoch || id !== projectId) return;
    if (!append) {rows=[]; files=[]; selected=null; $("detail").hidden=true;}
    rows = [...new Map([...rows,...result.revisions].map(r=>[r.id,r])).values()];
    files = [...new Map([...files,...result.files].map(f=>[f.id,f])).values()];
    nextOffset = result.nextOffset;
    role = result.project.permission; archived = result.project.archived;
    $("workspace").hidden=false; $("project-title").textContent=result.project.title;
    $("project-status").textContent=archived?"보관 중":"작업 중";
    $("project-manage").hidden=!result.project.canManage;
    $("more-revisions").hidden=nextOffset<0; $("more-files").hidden=nextOffset<0;
    $("upload-form").querySelectorAll("input,button").forEach(e=>e.disabled=archived || role==="VIEWER");
    $("work-form").querySelectorAll("input,select,textarea,button").forEach(e=>e.disabled=archived || role==="VIEWER");
    $("events").replaceChildren(...result.events.map(e=>el("p",`${e.created_at} · ${e.action} · ${e.note}`)));
    render();
  }
  function render() {
    $("files").replaceChildren(...files.map(f=>{
      const card=el("article",undefined,"card");card.append(el("b",f.filename),el("p",`${f.size_bytes.toLocaleString()} bytes · SHA-256 ${f.sha256}`,"meta"));
      const a=el("a","원본 다운로드");a.href=url(`files/${f.id}/download`);card.append(a);
      if (!["png","jpg","jpeg"].includes(f.extension)) card.append(button("본문 추출",async()=>{const r=await api(`files/${f.id}/extract`,{method:"POST"});await refresh();await open(r.id);message(r.work_status==="FAILED"?"추출 실패 기록을 남겼습니다. 원본을 확인하세요.":"본문 추출 결과를 확인했습니다. 서식·정답 검수는 별도입니다.");}));
      return card;
    }));
    const listed=rows.filter(r=>r.item_key!=="project-profile");
    const rowCard=r=>{const card=el("article",undefined,"card");const d=data(r);card.append(el("b",r.title),el("p",`${labels[d.category]||"자료"} · ${r.label} · ${statuses[r.work_status]||r.work_status} ${r.id===r.head_id?"· 최신":""} ${r.id===r.approved_id?"· 승인본":""}`,"meta"));if(d.ai)card.append(el("p",`${d.ai} · ${d.goal||""}`));if(d.nextWork)card.append(el("p","다음 작업: "+d.nextWork));card.append(button("자료·이력 확인",()=>open(r.id)));return card;};
    $("revisions").replaceChildren(...(listed.length?listed.map(rowCard):[el("p","아직 등록된 제작 자료가 없습니다.")]));
    const candidates=listed.filter(r=>data(r).category==="RESULT");
    $("review-list").replaceChildren(...(candidates.length?candidates.map(rowCard):[el("p","완성본 후보를 등록하면 여기에 표시됩니다.")]));
    options($("source-files"),files.map(f=>[f.id,f.filename]));options($("result-file"),files.map(f=>[f.id,f.filename]),true);
    options($("reference-revisions"),listed.map(r=>[r.id,`${r.title} · ${r.label}`]));
  }
  async function open(id) {
    const current=epoch, response=await api(`revisions/${id}`);if(current!==epoch)return;
    selected=response.revision;const r=selected,d=data(r);$("detail").hidden=false;
    $("detail-title").textContent=r.title;$("detail-meta").textContent=`${r.item_key} / ${r.label} · ${r.work_status} · 리비전 ${r.id}`;
    $("detail-content").textContent=JSON.stringify(d,null,2);
    $("comparison").hidden=!response.parent?.id;
    const before=data(response.parent||{}), changes=Object.keys({...before,...d}).filter(k=>JSON.stringify(before[k])!==JSON.stringify(d[k]));
    $("detail-diff").textContent=changes.map(k=>`${k}\n이전: ${JSON.stringify(before[k])??"없음"}\n현재: ${JSON.stringify(d[k])??"없음"}`).join("\n\n");
    $("history").replaceChildren(...response.history.map(h=>button(`${h.label} · ${statuses[h.work_status]||h.work_status}`,()=>open(h.id))));
    $("revise").hidden=archived || !["WORK","RESULT","HANDOFF"].includes(d.category) || role==="VIEWER";
    $("review-controls").hidden=archived || role!=="APPROVER";
    $("check-status").replaceChildren(...Object.entries({QUESTION:"문항·정답",EXPLANATION:"해설",FORMAT:"서식",RIGHTS:"이용 권한"}).map(([key,name])=>el("span",name+": "+(response.checks[key]===true?"통과":response.checks[key]===false?"수정 필요":"미검수"),"check")));
    $("detail").scrollIntoView({behavior:"smooth",block:"start"});
  }
  function clearWork() {$("work-form").reset();$("work-form").elements.parentId.value="";$("work-form").elements.itemKey.value="work-"+crypto.randomUUID();}
  bind("project-form",async()=>{const values=Object.fromEntries(new FormData($("project-form")));const r=await post("projects",values);projectId=r.id;await projects();message("중등 제작 프로젝트를 만들었습니다.");},"submit");
  bind("project",async()=>{projectId=$("project").value;epoch++;clearWork();const u=new URL(location.href);if(projectId)u.searchParams.set("projectId",projectId);else u.searchParams.delete("projectId");history.replaceState(null,"",u);await projects();},"change");
  bind("project-manage",async e=>{await post("project-state",{action:e.submitter.value,title:$("project-manage").elements.title.value});await refresh();},"submit");
  bind("reload",()=>projects());bind("search",()=>refresh());bind("more-revisions",()=>refresh(true));bind("more-files",()=>refresh(true));
  bind("upload-form",async()=>{const r=await api("files",{method:"POST",body:new FormData($("upload-form"))});const check=await api(`revisions/${r.revision.id}`);if(check.revision.file_id!==r.file.id || data(check.revision).sha256!==r.file.sha256)throw Error("파일·SHA 연결 재조회 실패");$("query").value="";await refresh();message((r.duplicate?"기존 원본을 재사용했습니다.":"원본 파일을 등록했습니다.")+" SHA와 연결 리비전의 재조회를 확인했습니다.");},"submit");
  bind("work-form",async()=>{const form=$("work-form"), values=Object.fromEntries(new FormData(form));values.sourceFileIds=Array.from($("source-files").selectedOptions).map(o=>o.value);values.referenceRevisionIds=Array.from($("reference-revisions").selectedOptions).map(o=>o.value);if(!values.fileId)values.fileId=null;const r=await post("work",values);const verify=await api(`revisions/${r.id}`);if(verify.revision.id!==r.id || JSON.stringify(data(verify.revision).sourceFileIds)!==JSON.stringify(values.sourceFileIds))throw Error("리비전·원재료 연결 재조회 실패");clearWork();await refresh();await open(r.id);message("리비전과 원재료 연결을 저장하고 재조회했습니다.");},"submit");
  bind("clear-work",()=>clearWork());
  bind("revise",async()=>{const f=$("work-form"),revision=selected,d=data(revision),current=epoch;
    const missingFiles=[...new Set([...(d.sourceFileIds||[]),d.fileId].filter(Boolean))].filter(id=>!files.some(x=>x.id===id));
    const missingRefs=(d.referenceRevisionIds||[]).filter(id=>!rows.some(x=>x.id===id));
    const loadedFiles=await Promise.all(missingFiles.map(id=>api(`files/${id}`)));
    const loadedRefs=await Promise.all(missingRefs.map(id=>api(`revisions/${id}`)));
    if(current!==epoch)return;files.push(...loadedFiles);rows.push(...loadedRefs.map(x=>x.revision));render();
    f.reset();Object.entries(d).forEach(([key,value])=>{if(f.elements[key]&&!Array.isArray(value))f.elements[key].value=value??"";});f.elements.itemKey.value=revision.item_key;f.elements.parentId.value=revision.id;f.elements.label.value="v"+(rows.filter(r=>r.item_key===revision.item_key).length+1);for(const o of $("source-files").options)o.selected=(d.sourceFileIds||[]).includes(o.value);for(const o of $("reference-revisions").options)o.selected=(d.referenceRevisionIds||[]).includes(o.value);f.closest("details").open=true;section("materials");f.scrollIntoView({behavior:"smooth"});message("이 버전을 기준으로 새 리비전을 작성합니다. 최신 버전이 달라지면 저장 시 충돌을 안내합니다.");});
  bind("check-form",async()=>{const d=Object.fromEntries(new FormData($("check-form")));d.passed=d.passed==="true";await post(`revisions/${selected.id}/checks`,d);await open(selected.id);},"submit");
  bind("decision-form",async()=>{const id=selected.id;await post(`revisions/${id}/review`,Object.fromEntries(new FormData($("decision-form"))));await refresh();await open(id);message("최종 판단을 저장했습니다.");},"submit");
  bind("publish",async()=>{const r=await post(`revisions/${selected.id}/publish`,{expectedApprovedId:selected.approved_id});message(`내신Wiki 게시 승인 완료 · ${r.title} / ${r.label}. 승인 완성본 화면에서 확인할 수 있습니다.`);});
  function section(name){$("materials").hidden=name!=="materials";$("review").hidden=name!=="review";for(const x of ["materials","review"]){if(x===name)$("tab-"+x).setAttribute("aria-current","page");else $("tab-"+x).removeAttribute("aria-current");}}
  bind("tab-materials",()=>section("materials"));bind("tab-review",()=>section("review"));
  function division(high){$("middle-school").hidden=high;$("high-school").hidden=!high;for(const [id,active] of [["division-middle",!high],["division-high",high]]){if(active)$(id).setAttribute("aria-current","page");else $(id).removeAttribute("aria-current");}}
  bind("division-middle",()=>division(false));bind("division-high",()=>division(true));
  clearWork();projects().catch(e=>message(e.message));
})();
