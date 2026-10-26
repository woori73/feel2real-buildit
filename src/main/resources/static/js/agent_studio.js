"use strict";
(() => {
  const $ = (id) => document.getElementById(id);
  const projectId=new URLSearchParams(location.search).get("projectId")||"ai-agent-part1";
  const scoped=url=>{const u=new URL(url,location.origin);u.searchParams.set("projectId",projectId);return u.pathname+u.search;};
  const state = {
    revisions: [],
    files: [],
    selected: null,
    permission: "VIEWER",
    preview: null,
    cuts: [],
    boardEpoch: 0,
    section: "materials",
    catalogEpoch: 0,
    canManage: false,
  };
  let uploadInFlight = false;
  const origins = {
    ORIGINAL: "보관 원본",
    AI_PROPOSAL: "AI 제안",
    USER_EDIT: "사용자 수정",
  };
  const kinds = {
    SCRIPT: "각본",
    STORYBOARD: "콘티",
    CHARACTER: "캐릭터",
    VIDEO: "영상",
    MUSIC: "음악",
    JOB: "생성 작업",
    REVIEW: "검수 결과",
  };
  const IMAGE_TYPES = ["png", "jpg", "jpeg", "webp"];
  const STREAM_TYPES = ["mp4", "mov", "wav", "mp3", "flac"];
  const PREVIEW_TYPES = ["pdf", "zip", "md", "md5", "json", "html", ...IMAGE_TYPES, ...STREAM_TYPES];
  const el = (tag, text, cls) => {
    const e = document.createElement(tag);
    if (text !== undefined) e.textContent = text;
    if (cls) e.className = cls;
    return e;
  };
  function message(s) {
    $("message").textContent = s;
  }
  async function api(path, options = {}) {
    const r = await fetch(scoped("/api/studio/" + path), {
      credentials: "same-origin",
      ...options,
    });
    if (!r.ok) {
      let body = {};
      try {
        body = await r.json();
      } catch (_) {}
      const error = Error(`${body.detail || body.message || body.errorMessage || "요청 실패"} (${r.status})`);
      error.status = r.status;
      throw error;
    }
    if (r.status === 204 || r.headers.get("content-length") === "0") return;
    const t = await r.text();
    return t ? JSON.parse(t) : null;
  }
  const json = (data) => ({
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
  const run = (fn) => async (event) => {
    event?.preventDefault();
    try {
      message("");
      await fn(event);
    } catch (e) {
      message(e.message);
    }
  };
  function badges(r) {
    const box = el("div");
    box.append(
      el(
        "span",
        origins[r.origin] || r.origin,
        "badge " + (r.origin === "AI_PROPOSAL" ? "proposal" : ""),
      ),
      el(
        "span",
        r.id === r.approved_id ? "사용자 승인본" : "미승인",
        "badge " + (r.id === r.approved_id ? "approved" : ""),
      ),
      el("span", r.label, "badge"),
    );
    return box;
  }
  const previewUrl = (id, entry) => scoped("/api/studio/files/" + encodeURIComponent(id) + "/preview" + (entry == null ? "" : "?" + new URLSearchParams({entry})));
  const imageQueue = [];
  let imageReads = 0;
  function pumpImages() {
    while (imageReads < 2 && imageQueue.length) {
      const {image, id} = imageQueue.shift();
      if (!image.isConnected) continue;
      imageReads++;
      fetch(previewUrl(id), {credentials:"same-origin"}).then(async response => {
        if (!response.ok) throw Error("이미지 열람 실패 ("+response.status+")");
        const bytes = await response.blob();
        if (!image.isConnected) return;
        const url = URL.createObjectURL(bytes);
        image.onload = image.onerror = () => URL.revokeObjectURL(url);
        image.src = url;
      }).catch(error => { image.replaceWith(el("p", error.message, "hint")); })
        .finally(() => {imageReads--;pumpImages();});
    }
  }
  function imageFor(r) {
    const f = state.files.find(f => f.id === r.file_id);
    if (!f && r.file_id) {
      const slot=el("div");slot.append(el("p","연결 파일 확인 중","hint"));
      api("files/"+encodeURIComponent(r.file_id)).then(metadata=>{state.files.push(metadata);slot.replaceChildren(imageFor(r));}).catch(error=>slot.replaceChildren(el("p",error.message,"hint")));
      return slot;
    }
    if (!f || f.extension !== "png") return el("p", r.file_id ? "연결 문서 · 아래에서 열람하세요" : "이미지 업로드 대기", "empty-art");
    const button = el("button", undefined, "image-open");
    button.setAttribute("aria-label", r.title + " 이미지 확대");
    const img = el("img"); img.alt = r.title + " · " + r.label; img.decoding="async";
    button.append(img);
    button.onclick = () => { $("zoom-title").textContent = r.title + " · " + r.label; $("zoom-image").src = previewUrl(r.file_id); $("zoom-image").alt=img.alt; $("image-zoom").showModal(); };
    imageQueue.push({image:img,id:r.file_id});
    setTimeout(pumpImages,0);
    return button;
  }
  let previewEpoch=0;
  async function previewFile(file, target, entry) {
    const epoch=String(++previewEpoch);target.dataset.previewEpoch=epoch;
    const currentView=()=>target.dataset.previewEpoch===epoch;
    target.replaceChildren(el("h3", entry || file.filename));
    if (file.extension === "zip" && entry == null) {
      const data = await api("files/" + file.id + "/entries");
      if(!currentView())return;
      target.append(el("p",data.note));
      const list = el("ul",undefined,"zip-list");
      for (const row of data.entries) {
        const item = el("li");
        const name = el(row.previewable?"button":"span", row.name + " · " + row.size + " bytes");
        if (row.previewable) name.onclick=run(()=>previewFile(file,$("zip-preview"),row.name));
        item.append(name);list.append(item);
      }
      const view=el("div");view.id="zip-preview";target.append(list,view);return;
    }
    const ext = entry == null ? file.extension : entry.split(".").pop().toLowerCase();
    if(ext==="pdf") {
      const toolbar=el("div",undefined,"cut-navigation"),previous=el("button","이전 페이지"),next=el("button","다음 페이지"),label=el("span");
      const image=el("img");image.alt=(entry||file.filename)+" PDF 페이지";image.className="pdf-page";
      toolbar.append(previous,label,next);target.append(toolbar,image);
      let current=1,total=1,url;
      const show=async n=>{
        previous.disabled=next.disabled=true;
        try {
          const query=new URLSearchParams({page:n});if(entry!=null)query.set("entry",entry);
          const pdfUrl=new URL(previewUrl(file.id),location.origin);for(const [k,v]of query)pdfUrl.searchParams.set(k,v);
          const response=await fetch(pdfUrl);
          if(!response.ok)throw Error("PDF 페이지를 표시할 수 없습니다 ("+response.status+")");
          if(!currentView())return;
          total=Number(response.headers.get("X-PDF-Pages"));current=n;
          if(url)URL.revokeObjectURL(url);url=URL.createObjectURL(await response.blob());image.src=url;
          label.textContent=current+" / "+total+"페이지"+(total>100?" · 처음 100페이지 열람":"");
        }finally{previous.disabled=current<=1;next.disabled=current>=Math.min(100,total);}
      };
      previous.onclick=run(()=>show(current-1));next.onclick=run(()=>show(current+1));await show(1);
      return;
    }
    if (entry == null && STREAM_TYPES.includes(ext)) {
      // 영상·음악은 Range 스트림으로 바로 재생한다(대용량 러프 영상도 전체를 내려받지 않음).
      const media = el(ext === "mp4" || ext === "mov" ? "video" : "audio");
      media.controls = true;
      media.preload = "metadata";
      media.setAttribute("playsinline", "");
      media.className = "media-player";
      media.src = scoped("/api/studio/files/" + encodeURIComponent(file.id) + "/stream");
      media.onerror = () => message("재생할 수 없는 형식입니다. 원본 다운로드로 확인하세요. (" + ext + ")");
      target.append(media);
      return;
    }
    const response = await fetch(previewUrl(file.id, entry));
    if(!currentView())return;
    if (!response.ok) {const error=await response.json().catch(()=>({}));throw Error(error.message || "미리보기를 열 수 없습니다 ("+response.status+")");}
    if (["html","md"].includes(ext)) {
      const frame=el("iframe");frame.title=(entry || file.filename)+" 안전 문서";
      const source=await response.text();if(!currentView())return;
      frame.setAttribute("sandbox","");frame.srcdoc=source;frame.className="document-view";target.append(frame);
    } else if (ext==="md5") {target.append(el("pre",await response.text()));
    } else if (ext==="json") {
      target.append(el("pre",JSON.stringify(JSON.parse(await response.text()),null,2)));
    } else {
      if(state.preview) URL.revokeObjectURL(state.preview);
      state.preview=URL.createObjectURL(await response.blob());
      const image=IMAGE_TYPES.includes(ext), playable=STREAM_TYPES.includes(ext);
      const media=el(image?"img":playable?(ext==="mp4"||ext==="mov"?"video":"audio"):"iframe");
      if(image)media.alt=entry||file.filename;
      if(playable)media.controls=true;
      if(ext==="pdf") {media.title=(entry||file.filename)+" PDF 뷰어";media.className="document-view";media.setAttribute("sandbox","allow-same-origin");}
      media.src=state.preview;target.append(media);
    }
  }
  async function showRevisionFile(r,target) {
    const file = state.files.find(f=>f.id===r.file_id) || await api("files/"+r.file_id);
    await previewFile(file,target);
    target.scrollIntoView({block:"nearest"});
  }
  function renderBoard(cuts) {
    state.cuts=cuts;
    const board=$("storyboard"); board.replaceChildren();
    const numbers=$('episode').value==="1"?Array.from({length:68},(_,i)=>i+1):cuts.map(r=>r.cut_number);
    $("board-heading").textContent="콘티 보드 · 등록 "+cuts.length+" / "+numbers.length+"컷";
    for (const n of numbers) {
      const r=cuts.find(row=>row.cut_number===n);
      const card=el("article",undefined,"story-card");
      card.dataset.cut=n;
      if (r) {
        card.append(imageFor(r),el("h3",String(n).padStart(2,"0")+" · "+r.title),badges(r));
        const notes=el("div",undefined,"story-notes");notes.id="cut-notes-"+r.id;
        notes.append(el("p",r.content||"설명 미등록"));
        if(r.dialogue)notes.append(el("p","대사 · "+readableDialogue(r.dialogue)));
        if(r.direction)notes.append(el("p","연출 · "+r.direction,"hint"));
        const toggle=el("button","더 보기");toggle.className="story-more";toggle.setAttribute("aria-controls",notes.id);toggle.setAttribute("aria-expanded","false");toggle.setAttribute("aria-label",n+"컷 설명 더 보기");
        toggle.onclick=()=>{const expanded=toggle.getAttribute("aria-expanded")!=="true";toggle.setAttribute("aria-expanded",String(expanded));toggle.textContent=expanded?"접기":"더 보기";toggle.setAttribute("aria-label",n+"컷 설명 "+toggle.textContent);notes.classList.toggle("expanded",expanded);};
        card.append(notes,toggle);
        const open=el("button","컷 상세·검수");open.onclick=()=>detail(r);card.append(open);
      } else card.append(el("p",n+"컷 · 이미지·설명 업로드 대기","empty-art"));
      board.append(card);
    }
  }
  async function gallery() {
    const rows=[];let page;
    do {page=await api("revisions?kind=CHARACTER&limit=200&offset="+rows.length);rows.push(...page);}while(page.length===200);
    $("character-gallery").replaceChildren();
    for(const r of rows.filter(r=>r.id===r.head_id)) {
      const c=el("article",undefined,"story-card");c.append(display(r));$("character-gallery").append(c);
    }
    if(!$("character-gallery").children.length)$("character-gallery").append(el("p","등록된 캐릭터 자료가 없습니다. 이미지·설정 업로드 대기입니다."));
  }
  function readableDialogue(value) {
    try {
      const rows=JSON.parse(value);
      if(Array.isArray(rows))return rows.map(row=>typeof row==="string"?row:[row.speaker,row.line].filter(Boolean).join(" · ")).join("\n")||"대사 없음";
    }catch(_){}
    return value;
  }
  function display(r) {
    const box = el("div");
    box.append(
      imageFor(r),
      el("h3", r.title),
      badges(r),
      el(
        "p",
        `${kinds[r.kind]} · ${r.episode ? "EP" + String(r.episode).padStart(2, "0") : "공통 설정"}${r.cut_number ? " · " + r.cut_number + "컷" : ""} · ${r.work_status}`,
      ),
    );
    const notes=el("details");notes.append(el("summary","상세 자료·대사·연출"));
    for(const [name,value] of [["본문·설정",r.content],["대사",readableDialogue(r.dialogue)],["연출",r.direction]])
      if(value)notes.append(el("h3",name),el("pre",value));
    if (r.file_id) {
      const a = el("a", "원본 다운로드");
      a.href =
        scoped("/api/studio/files/" + encodeURIComponent(r.file_id) + "/content");
      const read=el("button","연결 자료 열람");
      const view=el("div",undefined,"revision-file-view");
      read.onclick=run(()=>showRevisionFile(r,view));
      box.append(a,read,view);
    } else box.append(el("p", "파일 업로드 대기", "hint"));
    box.append(notes);
    return box;
  }
  function detail(r) {
    state.selected = r;
    $("detail").hidden=false;
    $("detail").replaceChildren(display(r));
    if(r.kind==="STORYBOARD") {
      const navigation=el("div",undefined,"cut-navigation");
      for(const [delta,label] of [[-1,"이전 컷"],[1,"다음 컷"]]) {
        const row=state.cuts.find(c=>c.cut_number===r.cut_number+delta);
        const b=el("button",label);b.disabled=!row;b.onclick=()=>detail(row);navigation.append(b);
      }
      if(r.approved_id && r.approved_id!==r.id) {
        const b=el("button","승인본과 수정 후보 비교");
        b.onclick=run(async()=>{const approved=await api("revisions/"+r.approved_id);$("cut-comparison").replaceChildren(display(approved),display(r));$("cut-compare-dialog").showModal();});navigation.append(b);
      }
      $("detail").prepend(navigation);
    }
    if (state.permission !== "VIEWER") {
      const b = el("button", "이 자료의 다음 버전 작성");
      b.onclick = run(async () => { await fileList(); edit(r); });
      $("detail").append(b);
      const restore=el("button","이 버전으로 복원 초안 만들기");
      restore.onclick=run(async()=>{
        const restored=await api("revisions/"+r.id+"/restore",json({expectedHead:r.head_id,label:"restore-"+Date.now()}));
        await load();detail(restored);message("이전 원본을 참조하는 새 버전을 만들었습니다. 기존 승인본은 유지되며 새 버전 승인은 별도입니다.");
      });
      $("detail").append(restore);
    }
    if (state.permission === "APPROVER") {
      const b = el("button", "검수·사용자 승인");
      b.onclick = () => {
        $("review-target").textContent = r.title + " · " + r.label;
        $("review-form").reset();$("review-form").elements.action.querySelector('option[value="REJECT"]').disabled=r.id===r.approved_id;
        $("review-dialog").showModal();
      };
      $("detail").append(b);
      if(r.id===r.approved_id){
        const withdraw=el("button","승인 해제…");withdraw.onclick=run(async()=>{
          const current=await api("revisions/"+r.id);if(current.approved_id!==r.id)throw Error("승인 상태가 바뀌었습니다. 상세를 다시 여세요.");
          const file=current.file_id?await api("files/"+current.file_id):null;
          $("withdraw-form").reset();$("withdraw-target").textContent=current.title+" · "+current.label+" / "+current.id;
          $("withdraw-sha").textContent=file?"원본 SHA-256 · "+file.sha256:"연결 파일 없음 · 리비전 보존";
          $("withdraw-dialog").dataset.revisionId=current.id;$("withdraw-dialog").showModal();
        });$("detail").append(withdraw);
      }
    }
    const comments=el("details");comments.append(el("summary","검수표·댓글·승인 이력"));
    let commentsLoaded=false;
    comments.ontoggle=run(async()=>{if(comments.open&&!commentsLoaded){await fileList();comments.append(commentThread(r));commentsLoaded=true;}});
    $("detail").append(comments);
    $("detail").scrollIntoView({ block: "nearest" });
  }
  let memoryRevision="",pendingOffset=0;
  async function loadMemory() {
    const record=await api("memory");memoryRevision=record.revisionId;
    const memory=record.memory||{},box=$("memory-record");box.replaceChildren();
    for(const [key,label] of [["lastDecision","마지막 결정"],["currentAi","현재 담당"],["openIssues","미해결 문제"],["nextWork","다음 작업"]])box.append(el("h3",label),el("p",memory[key]||"아직 기록이 없습니다.","memory-text"));
    if(memory.pr){const link=el("a","관련 PR #"+memory.pr);link.href="https://github.com/woori73/feel2real-buildit/pull/"+encodeURIComponent(memory.pr);link.target="_blank";link.rel="noopener noreferrer";box.append(link);}
    if(memory.commitSha)box.append(el("p","최신 SHA · "+memory.commitSha,"memory-sha"));
    if(record.updatedAt)box.append(el("p","기록 시각 · "+record.updatedAt+" / 기록자 "+record.updatedBy,"hint"));
    $("memory-evidence").replaceChildren();
    for(const file of record.files||[]) {
      const c=el("div",undefined,"card");c.append(el("h3",file.filename),el("p","SHA-256 "+file.sha256));
      const b=el("button","인계 자료 열람"),view=el("div");b.onclick=run(()=>previewFile(file,view));c.append(b,view);$("memory-evidence").append(c);
    }
    for(const field of ["lastDecision","currentAi","openIssues","nextWork","pr","commitSha"])$("memory-form").elements[field].value=memory[field]||"";
    $("memory-files").replaceChildren();
    for(const file of state.files){const option=new Option(file.filename+" · "+file.sha256.slice(0,12),file.id);option.selected=(memory.sourceFileIds||[]).includes(file.id);$("memory-files").add(option);}
    $("memory-edit").hidden=state.permission==="VIEWER";
  }
  async function pending(append=false) {
    if(!append){pendingOffset=0;$("pending-list").replaceChildren();}
    const rows=await api("pending?limit=100&offset="+pendingOffset);pendingOffset+=rows.length;$("pending-more").hidden=rows.length<100;
    for(const row of rows) {
      const c=el("article",undefined,"card");c.append(el("h3",row.title),badges(row),el("p",row.last_review==="REQUEST_CHANGES"?"수정 요청됨 · 기존 승인본 유지":"사용자 검수 대기"));
      const open=el("button","상세·검수 열기");open.onclick=run(async()=>{await setWorkspace("review");detail(row);});c.append(open);$("pending-list").append(c);
    }
    if(!pendingOffset)$("pending-list").append(el("p","승인 대기 자료가 없습니다."));
  }
  function commentThread(r) {
    const section=el("section",undefined,"comment-thread");section.append(el("h3","컷·자료별 댓글과 검수 이력"));
    const list=el("div"),more=el("button","이전 댓글 더 불러오기");let cursor=null;
    const read=async(reset=false)=>{
      if(reset){cursor=null;list.replaceChildren();}
      const rows=await api("revisions/"+r.id+"/comments"+(cursor?"?beforeId="+cursor:""));more.hidden=rows.length<100;
      for(const event of rows) {
        const c=el("article",undefined,"card");let note=event.note,origin="사람 댓글";
        if(event.action==="COMMENT")try{const data=JSON.parse(note);note=data.body;origin=data.authorType==="HUMAN_SHARED_AI_MATERIAL"?"사람이 공유한 "+data.sharedAi+" 자료":"사람 댓글";if(data.sourceFileId){const file=state.files.find(f=>f.id===data.sourceFileId);const button=el("button","근거 파일 열람"),view=el("div");button.onclick=run(async()=>previewFile(file||await api("files/"+data.sourceFileId),view));c.append(button,view);}}catch(_){}
        else origin={APPROVE:"사용자 승인",REJECT:"반려",REQUEST_CHANGES:"수정 요청",WITHDRAW_APPROVAL:"승인 해제"}[event.action]||event.action;
        c.prepend(el("strong",origin+" · "+event.actor_name+" · "+event.label),el("p",note,"memory-text"),el("small",event.created_at));list.append(c);cursor=event.id;
      }
      if(!list.children.length)list.append(el("p","아직 댓글·검수 기록이 없습니다."));
    };
    more.onclick=run(()=>read());section.append(list,more);
    if(state.permission!=="VIEWER") {
      const form=el("form"),label=el("label","댓글·수정 의견"),body=el("textarea");body.required=true;body.maxLength=1200;body.rows=3;label.append(body);
      const aiLabel=el("label","AI 산출물 공유 (선택)"),ai=el("select");ai.add(new Option("사람 댓글",""));for(const name of ["Claude","Codex","Muse","ChatGPT"])ai.add(new Option(name+" 자료를 사람이 공유",name));aiLabel.append(ai);
      const sourceLabel=el("label","근거 자료"),source=el("select");source.add(new Option("근거 자료 없음",""));for(const file of state.files)source.add(new Option(file.filename,file.id));sourceLabel.append(source);
      const submit=el("button","댓글 기록");form.append(label,aiLabel,sourceLabel,el("p","AI 직접 인증 댓글과 구분하며, 실제 기록자는 로그인한 사용자입니다.","hint"),submit);
      form.onsubmit=run(async()=>{submit.disabled=true;try{await api("revisions/"+r.id+"/comments",json({body:body.value,sharedAi:ai.value||null,sourceFileId:source.value||null}));body.value="";await read(true);message("댓글을 저장하고 다시 확인했습니다. 원본·승인본은 유지합니다.");}finally{submit.disabled=false;}});section.append(form);
    }
    read(true).catch(error=>message(error.message));return section;
  }
  function edit(r) {
    const f = $("revision-form");
    f.reset();
    f.elements.parentId.value = "";
    for (const name of ["itemKey", "kind", "episode", "cutNumber"])
      f.elements[name].readOnly = false;
    if (r) {
      const values = {
        ...r,
        itemKey: r.item_key,
        parentId: r.id,
        cutNumber: r.cut_number,
        fileId: r.file_id,
        label: "",
      };
      for (const e of f.elements)
        if (e.name && values[e.name] != null) e.value = values[e.name];
      f.elements.origin.value = "USER_EDIT";
      for (const name of ["itemKey", "episode", "cutNumber"])
        f.elements[name].readOnly = true;
    } else {
      f.elements.episode.value = /^\d+$/.test($("episode").value) ? $("episode").value : "";
      f.elements.kind.value = $("kind").value || "SCRIPT";
      f.elements.origin.value = "USER_EDIT";
    }
    $("editor").showModal();
  }
  const catalog=window.StudioCatalog;
  function catalogueValues(){
    return {section:state.section,episode:$("episode").value,kind:$("kind").value,
      media:$("media").value,status:$("review-status").value,versions:$("versions").value,q:$("query").value.trim()};
  }
  function stopPlayers(){document.querySelectorAll("video,audio").forEach(media=>media.pause());}
  document.addEventListener("play",event=>{
    if(!["VIDEO","AUDIO"].includes(event.target.tagName))return;
    document.querySelectorAll("video,audio").forEach(media=>{if(media!==event.target)media.pause();});
  },true);
  function cacheFile(row){
    if(!row.file_id)return null;
    let file=state.files.find(f=>f.id===row.file_id);
    if(!file){file={id:row.file_id,filename:row.filename,extension:row.extension,sha256:row.sha256,size_bytes:row.size_bytes};state.files.push(file);}
    return file;
  }
  async function openEntry(row){
    stopPlayers();$("detail").hidden=false;
    if(row.entry_type!=="FILE"){
      const full=await api("revisions/"+encodeURIComponent(row.id));
      if(full.kind==="STORYBOARD"&&full.episode)state.cuts=await api("cuts?episode="+full.episode);
      detail(full);
      if(["VIDEO","AUDIO"].includes(catalog.media(row))){
        const target=$("detail").querySelector(".revision-file-view");
        if(target)await previewFile(cacheFile(row),target);
      }
    }else{
      state.selected=null;
      const file=cacheFile(row),view=el("div");
      $("detail").replaceChildren(el("h2",row.filename),el("p","업로드 원본 · 회차·버전·검수 상태 미등록","hint"),el("p",String(row.size_bytes||0)+" bytes · SHA-256 "+row.sha256,"asset-filename"),view);
      if(PREVIEW_TYPES.includes(row.extension))await previewFile(file,view);
      const download=el("a","원본 다운로드");download.href=scoped("/api/studio/files/"+encodeURIComponent(file.id)+"/content");$("detail").append(download);
      if(state.permission!=="VIEWER"){
        const connect=el("button","이 원본으로 자료 등록");connect.onclick=run(async()=>{
          await fileList();edit();const form=$("revision-form");form.elements.fileId.value=file.id;
          form.elements.title.value=file.filename;
          form.elements.kind.value=({VIDEO:"VIDEO",AUDIO:"MUSIC"})[catalog.media(row)]||"JOB";
        });$("detail").append(connect);
      }
    }
    $("detail").scrollIntoView({block:"nearest"});
  }
  function catalogueCard(row){
    const card=el("article",undefined,"card asset-card"),type=catalog.media(row),open=el("button");
    const art=el("span",({VIDEO:"영상",IMAGE:"이미지",AUDIO:"음성 · 음악",PACKAGE:"패키지",DOCUMENT:"문서"})[type],"asset-art type-"+type.toLowerCase());
    if(type==="IMAGE"&&row.file_id){const image=el("img");image.alt="";image.loading="lazy";image.decoding="async";image.src=previewUrl(row.file_id);art.replaceChildren(image);}
    open.append(art,el("strong",row.title||row.filename));open.setAttribute("aria-label",(row.title||row.filename)+" 열기");
    open.onclick=run(()=>openEntry(row));
    const tag=el("span",catalog.status(row),"badge"+(row.id===row.approved_id?" approved":""));
    card.append(open,tag,el("p",(row.episode?"EP"+String(row.episode).padStart(2,"0"):row.entry_type==="FILE"?"공통·회차 미등록":"공통 자료")+" · "+(row.label||"업로드 원본"),"asset-meta"));
    card.append(el("p",(origins[row.origin]||row.origin||"")+" · "+String(row.created_at||"").replace("T"," "),"asset-meta"));
    if(row.filename)card.append(el("small",row.filename,"asset-filename"));
    if(row.item_key)card.append(el("small",row.item_key,"asset-filename"));
    return card;
  }
  async function featured(values,epoch){
    const target=$("featured");target.replaceChildren();
    if((values.media&&values.media!=="VIDEO")||(values.kind&&values.kind!=="VIDEO")||["REJECTED","CHANGES","UNASSIGNED"].includes(values.status))return;
    const modes=state.section==="review"?[["APPROVED","최신 감독 승인 영상"],["PENDING","현재 검수 영상"]]:[[values.status,"최신 시청 영상"]];
    const rows=await Promise.all(modes.map(async([status,title])=>{
      const qs=catalog.params({...values,status,media:"VIDEO"});qs.set("limit",1);
      return {title,items:await api("catalog?"+qs)};
    }));
    if(epoch!==state.catalogEpoch)return;
    for(const result of rows){
      const card=el("article",undefined,"featured-card");card.append(el("h3",result.title));
      const row=result.items[0];
      if(row){
        const view=el("div");card.append(view,el("strong",row.title||row.filename),el("p",catalog.status(row)+" · "+(row.label||"업로드 원본"),"asset-meta"));
        await previewFile(cacheFile(row),view);
        if(epoch!==state.catalogEpoch)return;
        const open=el("button","상세·검수 자료 열기");open.onclick=run(()=>openEntry(row));card.append(open);
      }else card.append(el("p",result.title.includes("승인")?"선택한 범위에 감독 승인 영상이 없습니다. 승인 대기 자료를 확인하세요.":"선택한 범위에 영상이 없습니다.","empty-art"));
      target.append(card);
    }
  }
  async function load(append=false){
    if(typeof append!=="boolean")append=false;
    const epoch=++state.catalogEpoch,values=catalogueValues();
    if(!append){stopPlayers();state.revisions=[];state.selected=null;$("detail").hidden=true;$("detail").replaceChildren();}
    const page=await api("catalog?"+catalog.params(values,state.revisions.length));
    if(epoch!==state.catalogEpoch)return;
    state.revisions=append?[...state.revisions,...page.slice(0,40)]:page.slice(0,40);
    $("load-more").hidden=page.length<=40;
    $("revision-list").replaceChildren();$("document-list").replaceChildren();
    const groups=catalog.partition(state.revisions);
    for(const row of state.revisions){cacheFile(row);(groups.media.includes(row)?$("revision-list"):$("document-list")).append(catalogueCard(row));}
    if(!groups.media.length)$("revision-list").append(el("p","이 범위의 영상·이미지·음성이 없습니다. 문서 자료 또는 필터를 확인하세요.","hint"));
    $("document-count").textContent="("+groups.documents.length+"개)";
    $("document-results").open=!!values.q&&!groups.media.length&&groups.documents.length>0;
    $("catalog-count").textContent="불러온 자료 "+state.revisions.length+"개"+(page.length>40?" · 다음 페이지가 있습니다.":"");
    $("catalog-note").textContent=values.episode&&values.episode!=="common"?"회차가 지정된 자료만 표시합니다. 회차 미등록 원본은 ‘전체 회차·공통 자료’ 또는 ‘공통·회차 미등록’에서 찾으세요.":"공통 자료와 회차 미등록 원본을 포함합니다. 이전 버전은 ‘모든 버전’에서 찾을 수 있습니다.";
    if(!append)await featured(values,epoch);
  }
  async function fileList() {
    const files=[];let filePage;
    do{filePage=await api("files?limit=200&offset="+files.length);files.push(...filePage);}while(filePage.length===200);
    state.files=files;
    $("bulk-file").replaceChildren(new Option("일괄 등록 원본을 선택하세요",""));
    for(const file of state.files.filter(f=>["zip","json"].includes(f.extension)))$("bulk-file").add(new Option(file.filename,file.id));
    $("file-list").replaceChildren();
    const select = $("revision-form").elements.fileId;
    select.replaceChildren(new Option("파일 없음 · 업로드 대기", ""));
    for (const f of state.files) {
      select.add(new Option(f.filename + " · " + f.sha256.slice(0, 12), f.id));
      const c = el("div", undefined, "card");
      c.append(
        el("h3", f.filename),
        el("p", `${f.size_bytes.toLocaleString()} bytes · SHA-256 ${f.sha256}`),
      );
      const a = el("a", "원본 다운로드");
      a.href = scoped("/api/studio/files/" + f.id + "/content");
      c.append(a);
      if (
        PREVIEW_TYPES.includes(f.extension)
      ) {
        const b = el("button", "자료 열람");
        b.onclick = run(() => previewFile(f, $("file-preview")));
        c.append(b);
      }
      $("file-list").append(c);
    }
    if (!state.files.length) $("file-list").append(el("p", "원본 업로드 대기"));
  }
  async function compareOptions() {
    const ids = ["left-version", "right-version"];
    ids.forEach((id) => ($(id).disabled = true));
    $("compare-button").disabled = true;
    try {
      const rows=[];let page;
      do {page=await api("revisions?limit=200&offset="+rows.length);rows.push(...page);}while(page.length===200);
      for (const id of ids) {
        const previous = $(id).value;
        $(id).replaceChildren();
        for (const r of rows)
          $(id).add(
            new Option(
              `${r.item_key} / ${r.label} / ${origins[r.origin]}`,
              r.id,
            ),
          );
        if (rows.some((r) => r.id === previous)) $(id).value = previous;
      }
    } finally {
      ids.forEach((id) => ($(id).disabled = false));
      $("compare-button").disabled = false;
    }
  }
  async function setWorkspace(section){
    state.section=section;$("review-status").value="";$("status-filter").hidden=section!=="review";
    $("catalog-heading").textContent=section==="review"?"검수·승인 자료":"모든 제작 자료";
    document.querySelectorAll("[data-tab]").forEach(button=>{
      if(button.dataset.tab===section)button.setAttribute("aria-current","page");else button.removeAttribute("aria-current");
    });
    document.querySelectorAll("[data-owner]").forEach(tool=>{
      tool.hidden=tool.dataset.owner!==section||tool.id==="chat"||(tool.id==="members"&&!state.canManage);
      if(tool.hidden)tool.open=false;
    });
    await load();
  }
  document.querySelectorAll("[data-tab]").forEach(button=>button.onclick=run(()=>setWorkspace(button.dataset.tab)));
  async function openTool(id){
    const tool=$(id);if(tool.dataset.owner!==state.section)await setWorkspace(tool.dataset.owner);
    const wasOpen=tool.open;tool.open=true;if(wasOpen)await loadTool(tool);tool.scrollIntoView({block:"nearest"});
  }
  async function loadTool(tool){
    if(tool.id==="files")await fileList();
    if(tool.id==="characters")await gallery();
    if(tool.id==="memory")await loadMemory();
    if(tool.id==="inbox")await pending();
    if(tool.id==="ai-work")await aiWork();
    if(tool.id==="compare")await compareOptions();
    if(tool.id==="board"){
      await fileList();const rows=await api("cuts?episode="+(/^\d+$/.test($("episode").value)?$("episode").value:1));renderBoard(rows);
      $("cut-grid").replaceChildren();
      for(const r of rows){const button=el("button",r.cut_number+"컷");button.onclick=()=>detail(r);$("cut-grid").append(button);}
    }
    if(tool.id==="history"){
      const rows=await api("events");$("event-list").replaceChildren();
      for(const row of rows){const card=el("div",undefined,"card");card.append(el("strong",row.action),el("p",row.note||row.revision_id||""),el("small",row.created_at+" · 사용자 "+row.actor_id));$("event-list").append(card);}
    }
    if(tool.id==="notifications")await notifications(true);
    if(tool.id==="members"){
      $("member-list").replaceChildren();for(const member of await api("members"))$("member-list").append(el("p",member.user_id+" · "+member.permission));
    }
  }
  document.querySelectorAll("details.tool-panel").forEach(tool=>tool.addEventListener("toggle",run(async()=>{if(tool.open)await loadTool(tool);})));
  $("open-upload").onclick=run(()=>{
    if(uploadInFlight)return;
    // Keep the native picker inside the user's click, before any network await.
    $("upload-file").click();
    return openTool("files");
  });
  for(const id of ["media","versions","review-status"])$(id).onchange=run(()=>load());
  $("query").addEventListener("keydown",event=>{if(event.key==="Enter"){event.preventDefault();run(()=>load())();}});
  $("close-zoom").onclick=()=>$("image-zoom").close();
  $("close-cut-compare").onclick=()=>$("cut-compare-dialog").close();
  document.addEventListener("keydown",event=>{
    if(document.querySelector("dialog[open]") || ["INPUT","TEXTAREA","SELECT"].includes(event.target.tagName) || event.target.isContentEditable || $("materials").hidden || !state.selected || state.selected.kind!=="STORYBOARD")return;
    const delta=event.key==="ArrowRight"?1:event.key==="ArrowLeft"?-1:0;
    const row=state.cuts.find(r=>r.cut_number===state.selected.cut_number+delta);
    if(delta&&row){event.preventDefault();detail(row);}
  });
  $("memory-reload").onclick=run(()=>loadMemory());
  $("pending-more").onclick=run(()=>pending(true));
  const taskLabels={OPEN:"대기",IN_PROGRESS:"진행 중",DONE:"완료",CANCELLED:"취소"};
  const verdictLabels={PASS:"통과",NEEDS_CHANGES:"수정 필요",COMMENT:"의견"};
  async function aiWork(){
    const status=$("ai-task-status").value;
    const tasks=await api("tasks?limit=100"+(status?"&status="+encodeURIComponent(status):""));
    $("ai-task-list").replaceChildren();
    for(const t of tasks){
      const c=el("article",undefined,"card");
      c.append(el("strong",`${taskLabels[t.status]||t.status} · 담당 ${t.assignee}`),el("h3",t.title),el("p",t.instructions),el("small",`작업 ID ${t.workId} · ${t.author_type==="AI_CONNECTOR"?"AI 연결 "+(t.ai_client||""):"사람"} · ${t.created_at}`));
      if(t.status==="OPEN"||t.status==="IN_PROGRESS")for(const next of t.status==="OPEN"?["IN_PROGRESS","CANCELLED"]:["DONE","CANCELLED"]){
        const b=el("button",taskLabels[next]+"(으)로 변경");
        b.onclick=run(async()=>{await api("tasks/"+encodeURIComponent(t.taskId)+"/status",json({status:next}));await aiWork();message("작업 상태를 바꾸고 다시 불러왔습니다.");});
        c.append(b);
      }
      $("ai-task-list").append(c);
    }
    if(!tasks.length)$("ai-task-list").append(el("p","수정 작업이 없습니다."));
    const reviews=await api("reviews");
    $("ai-review-list").replaceChildren();
    for(const r of reviews){
      const c=el("article",undefined,"card");
      c.append(el("strong",`${verdictLabels[r.verdict]||r.verdict} · ${r.author_type==="AI_CONNECTOR"?"AI 연결 "+(r.ai_client||""):"사람"}`),el("h3",`작업 ID ${r.item_key}`),el("p",r.summary));
      const list=el("ul");for(const i of r.issues||[])list.append(el("li",`[${i.severity}]${i.timecode?" "+i.timecode:""}${i.cutNumber?" 컷"+i.cutNumber:""} ${i.description}`));
      c.append(list,el("small",r.created_at));
      const open=el("button","검수 대상 버전 열기");open.onclick=run(async()=>{await setWorkspace("review");detail(await api("revisions/"+r.revision_id));});c.append(open);
      $("ai-review-list").append(c);
    }
    if(!reviews.length)$("ai-review-list").append(el("p","검수 보고서가 없습니다."));
  }
  $("ai-work-reload").onclick=run(()=>aiWork());
  $("ai-task-status").onchange=run(()=>aiWork());
  $("memory-form").onsubmit=run(async()=>{
    const data=Object.fromEntries(new FormData($("memory-form")));data.expectedRevisionId=memoryRevision||null;data.pr=data.pr?Number(data.pr):null;data.sourceFileIds=[...$("memory-files").selectedOptions].map(o=>o.value);
    const button=$("memory-form").querySelector("button");button.disabled=true;
    try{const saved=await api("memory",json(data));await loadMemory();if(memoryRevision!==saved.revisionId)throw Error("동시에 다른 기록이 저장됐습니다. 최신 상태를 확인하세요.");message("새 작업 기억을 저장하고 실제 저장 결과를 다시 불러왔습니다.");}finally{button.disabled=false;}
  });
  $("load-more").onclick=run(()=>load(true));
  $("bulk-form").onsubmit=run(async()=>{
    if(!$("bulk-file").value)throw Error("보관한 JSON 또는 ZIP 원본을 선택하세요.");
    const result=await api("imports/ep01",json({fileId:$("bulk-file").value}));
    $("episode").value="1";await load();message(result.registered+"컷을 등록했습니다. 승인 전 초안이며 기존 승인 이력은 보존했습니다.");
  });
  $("search").onclick = run(load);
  $("episode").onchange = run(load);
  $("kind").onchange = run(load);
  $("new-revision").onclick = run(async () => { await fileList(); edit(); });
  $("close-editor").onclick = () => $("editor").close();
  $("cancel-review").onclick = () => $("review-dialog").close();
  $("revision-form").onsubmit = run(async () => {
    const data = Object.fromEntries(new FormData($("revision-form")));
    for (const k of ["episode", "cutNumber"])
      data[k] = data[k] ? Number(data[k]) : null;
    data.fileId = data.fileId || null;
    data.parentId = data.parentId || null;
    const result = await api("revisions", json(data));
    $("editor").close();
    await load();
    detail(result);
    message("새 버전을 저장했습니다. 승인본은 검수 결정으로 지정하세요.");
  });
  $("review-form").onsubmit = run(async () => {
    await api(
      "revisions/" + state.selected.id + "/review",
      json(Object.fromEntries(new FormData($("review-form")))),
    );
    $("review-dialog").close();
    await load();
    detail(await api("revisions/" + state.selected.id));
    message("사용자 검수 결정을 기록했습니다.");
  });
  $("cancel-withdraw").onclick=()=>$("withdraw-dialog").close();
  $("withdraw-form").onsubmit=run(async()=>{
    const form=$("withdraw-form"),id=$("withdraw-dialog").dataset.revisionId,button=form.querySelector('button[type="submit"]');
    button.disabled=true;
    try {
      await api("revisions/"+id+"/approval-withdrawals",json({confirmed:form.elements.confirmed.checked,expectedApprovedId:id,note:form.elements.note.value}));
      const actual=await api("revisions/"+id);if(actual.approved_id!==null)throw Error("승인 상태 재조회 결과가 다릅니다. 최신 자료를 확인하세요.");
      $("withdraw-dialog").close();await load();detail(actual);message("승인 해제를 저장하고 다시 확인했습니다. 원본·SHA·버전·기존 승인 이력은 보존합니다.");
    } finally {button.disabled=false;}
  });
  function uploadStatus(text, needsLogin = false) {
    const target = $("upload-status");
    target.replaceChildren(el("span", text));
    message(text);
    if (needsLogin) {
      const login = el("a", " 로그인 화면 열기");
      login.href = "/?" + new URLSearchParams({login:"required", returnUrl:location.pathname + location.search});
      login.target = "_blank";
      login.rel = "noopener";
      target.append(login);
    }
  }
  $("upload-form").onsubmit = run(async () => {
    if (uploadInFlight) return;
    const input = $("upload-file"), f = input.files[0];
    if (!f || f.size === 0 || f.size > 100 * 1024 * 1024) {
      uploadStatus("1 byte~100 MiB의 파일을 선택하세요.");
      return;
    }
    uploadInFlight = true;
    for (const id of ["upload-submit", "upload-file", "open-upload"]) $(id).disabled = true;
    $("upload-receipt").replaceChildren();
    uploadStatus("원본 업로드 중 · " + f.name);
    let result;
    try {
      const data = new FormData();
      data.append("file", f);
      result = await api("files", { method: "POST", body: data });
      if (!result?.file?.id || !result.file.sha256) throw Error("원본 보관 응답을 확인하세요.");
      const file = result.file;
      // Preserve the server receipt before refreshing a potentially large/expired list.
      const download = el("a", "보관한 원본 다운로드 · " + file.filename);
      download.href = scoped("/api/studio/files/" + encodeURIComponent(file.id) + "/content");
      $("upload-receipt").append(
        el("p", "원본 보관 ID · " + file.id),
        el("p", "SHA-256 · " + file.sha256, "asset-filename"),
        download,
      );
      uploadStatus(result.duplicate
        ? "동일 SHA-256의 원본이 이미 보관되어 있습니다. 기존 원본을 연결해 자료를 등록하세요."
        : "원본 보관 완료. 회차·출처·검수 상태는 자료 등록에서 지정하세요.");
      try {
        await fileList();
        // A SHA duplicate can have a different stored filename from the selected file.
        $("episode").value="";$("query").value=file.filename;$("kind").value="";$("media").value="";
        await setWorkspace("materials");
      } catch (error) {
        uploadStatus("원본 보관은 완료됐지만 목록 새로고침에 실패했습니다. 재업로드하지 말고 아래 보관 ID로 확인하세요. " + error.message, error.status === 401);
      }
    } catch (error) {
      if (error.status === 401) {
        uploadStatus("로그인이 만료되었습니다 (401). 다시 로그인한 뒤 업로드하세요. 선택한 파일은 유지됩니다.", true);
      } else if (error.status && error.status < 500) {
        uploadStatus("원본 업로드 요청이 거부되었습니다. " + error.message);
      } else {
        uploadStatus("업로드 결과를 확인하지 못했습니다. 원본 보관함에서 파일명·SHA-256으로 중복 여부를 확인한 뒤 다시 시도하세요. " + error.message);
      }
    } finally {
      uploadInFlight = false;
      for (const id of ["upload-submit", "upload-file", "open-upload"]) $(id).disabled = false;
    }
  });
  $("compare-button").onclick = run(async () => {
    if (!$("left-version").value || !$("right-version").value)
      throw Error("비교할 두 버전을 등록하세요.");
    const data = await api(
      "compare?" +
        new URLSearchParams({
          left: $("left-version").value,
          right: $("right-version").value,
        }),
    );
    $("comparison").replaceChildren(display(data.left), display(data.right));
  });
  $("chat-form").onsubmit = run(async () => {
    const q = $("chat-query").value;
    const rows = await api("revisions?" + new URLSearchParams({ q }));
    const entry = el("div", undefined, "chat-entry");
    entry.append(
      el("strong", q),
      el(
        "p",
        rows.length
          ? `등록 자료 ${rows.length}개에서 근거를 찾았습니다.`
          : "일치하는 등록 자료가 없습니다. 자료를 임의 생성하지 않습니다.",
      ),
    );
    for (const r of rows.slice(0, 10)) {
      const b = el("button", `${r.title} · ${r.label} · ${origins[r.origin]}`);
      b.onclick = run(async () => {
        await setWorkspace("materials");
        detail(r);
      });
      entry.append(
        b,
        el(
          "pre",
          (
            r.content ||
            r.dialogue ||
            r.direction ||
            "파일 원문을 열람하세요."
          ).slice(0, 1000),
        ),
      );
    }
    $("chat-log").append(entry);
    entry.scrollIntoView({ block: "nearest" });
  });
  let notificationCursor=null,notificationThrough=0;
  async function notifications(reset=false){
    if(reset){notificationCursor=null;notificationThrough=0;$("notification-list").replaceChildren();}
    const data=await api("notifications"+(notificationCursor?"?beforeId="+notificationCursor:""));
    for(const row of data.items){notificationThrough=Math.max(notificationThrough,row.id);const card=el("article",undefined,"card");card.append(el("strong",({APPROVE:"승인",REJECT:"반려",REQUEST_CHANGES:"수정 요청",WITHDRAW_APPROVAL:"승인 해제",COMMENT:"댓글"}[row.action]||row.action)+" · "+row.actor_name),el("h3",row.title||"프로젝트 알림"),el("p",row.id>data.readThrough?"새 알림":"확인한 알림"),el("small",row.created_at));if(row.revision_id){const b=el("button","관련 자료 열기");b.onclick=run(async()=>{await setWorkspace("review");detail(await api("revisions/"+row.revision_id));});card.append(b);}$("notification-list").append(card);}
    notificationCursor=data.nextBeforeId||null;$("notifications-more").hidden=!notificationCursor;$("notifications-read").hidden=!notificationThrough;
    if(!$("notification-list").children.length)$("notification-list").append(el("p","아직 다른 팀원의 검수·댓글 알림이 없습니다."));
  }
  $("notifications-more").onclick=run(()=>notifications());$("notifications-read").onclick=run(async()=>{await api("notifications/read",json({throughId:notificationThrough}));await notifications(true);message("알림 확인 상태를 저장하고 다시 조회했습니다.");});
  $("invite-search-form").onsubmit=run(async()=>{const users=await api("users?"+new URLSearchParams({q:$("invite-query").value}));$("invite-search-results").replaceChildren();for(const user of users){const c=el("div",undefined,"card"),role=el("select");role.setAttribute("aria-label",user.name+" 초대 권한");for(const [value,label]of [["VIEWER","열람"],["EDITOR","제작·수정"],["APPROVER","검수·승인"]])role.add(new Option(label,value));const b=el("button","팀원 초대");c.append(el("strong",user.name+" · "+user.nickname),role,b);b.onclick=run(async()=>{await api("invitations",json({userId:user.user_id,permission:role.value}));b.disabled=true;message("초대를 기록했습니다. 상대방이 프로젝트 목록에서 수락해야 자료를 볼 수 있습니다.");});$("invite-search-results").append(c);}if(!users.length)$("invite-search-results").append(el("p","검색 결과가 없습니다."));});
  $("member-form").onsubmit = run(async () => {
    const m = Object.fromEntries(new FormData($("member-form")));
    m.userId = Number(m.userId);
    await api("members", json(m));
    message("접근 권한을 저장했습니다.");
    await openTool("members");
  });
  $("initialize").onclick = run(async () => {
    await api("project", { method: "POST" });
    location.reload();
  });
  run(async () => {
    for (let i = 1; i <= 14; i++) {
      $("episode").add(new Option("EP" + String(i).padStart(2, "0"), i));
      const b = el("button", `EP${String(i).padStart(2, "0")} · 자료 보기`);
      b.onclick = () => {
        $("episode").value = i;
        run(load)();
      };
      $("episodes").append(b);
    }
    const project = await api("project");
    state.permission = project.archived?"VIEWER":project.permission;
    $("project-title").textContent=project.title;
    state.canManage=project.canManage;$("members").hidden=!project.canManage;
    if(project.archived)message("보관된 프로젝트 · 모든 자료와 이력을 열람할 수 있습니다. 수정은 프로젝트 목록에서 재개 후 가능합니다.");
    try {
      const imported=await api("source-import");
      if(imported.registeredSourcePaths>0){
        $("source-info").hidden=false;$("source-info").textContent="GitHub #863 · 자료 "+imported.registeredSourcePaths+" / 536경로 · 원작 대조 0/68 · PASS 확정 0 · 대표 10컷 이미지 미생성";
      }
    }catch(error){message(error.message);}
    $("project-info").textContent =
      `${project.archived?"보관됨 · ":""}보관 파일 ${project.files}개 · ${{ VIEWER: "열람", EDITOR: "제작·수정", APPROVER: "검수·승인" }[project.permission]}`;
    $("initialize").hidden =
      project.initialized || project.permission !== "APPROVER";
    if (state.permission === "VIEWER") {
      $("new-revision").hidden = true;
      $("upload-form").hidden = true;$("bulk-form").hidden=true;$("open-upload").hidden=true;
    }
    $("episode").value = "";
    await setWorkspace("materials");
  })();
})();
