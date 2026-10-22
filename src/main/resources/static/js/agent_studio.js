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
  };
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
    JOB: "생성 작업",
    REVIEW: "검수 결과",
  };
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
      throw Error(
        body.detail ||
          body.message ||
          body.errorMessage ||
          `요청 실패 (${r.status})`,
      );
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
      const media=el(ext==="png"?"img":ext==="mp4"?"video":"iframe");
      if(ext==="png")media.alt=entry||file.filename;
      if(ext==="mp4")media.controls=true;
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
        card.append(imageFor(r),el("h3",String(n).padStart(2,"0")+" · "+r.title),badges(r),el("p",r.content||"설명 미등록"));
        if(r.dialogue)card.append(el("p","대사 · "+readableDialogue(r.dialogue)));
        if(r.direction)card.append(el("p","연출 · "+r.direction,"hint"));
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
    for (const [name, value] of [
      ["본문·설정", r.content],
      ["대사", readableDialogue(r.dialogue)],
      ["연출", r.direction],
    ])
      if (value) box.append(el("h3", name), el("pre", value));
    if (r.file_id) {
      const a = el("a", "원본 다운로드");
      a.href =
        scoped("/api/studio/files/" + encodeURIComponent(r.file_id) + "/content");
      const read=el("button","연결 자료 열람");
      const view=el("div",undefined,"revision-file-view");
      read.onclick=run(()=>showRevisionFile(r,view));
      box.append(a,read,view);
    } else box.append(el("p", "파일 업로드 대기", "hint"));
    return box;
  }
  function detail(r) {
    state.selected = r;
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
      b.onclick = () => edit(r);
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
        $("review-dialog").showModal();
      };
      $("detail").append(b);
    }
    $("detail").append(commentThread(r));
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
      const open=el("button","상세·검수 열기");open.onclick=()=>{document.querySelector('[data-tab="materials"]').click();detail(row);};c.append(open);$("pending-list").append(c);
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
        else origin={APPROVE:"사용자 승인",REJECT:"반려",REQUEST_CHANGES:"수정 요청"}[event.action]||event.action;
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
      f.elements.episode.value = $("episode").value;
      f.elements.kind.value = $("kind").value || "SCRIPT";
      f.elements.origin.value = "USER_EDIT";
    }
    $("editor").showModal();
  }
  async function load(append=false) {
    if(typeof append!=="boolean")append=false;
    const qs = new URLSearchParams();
    for (const [key, id] of [
      ["episode", "episode"],
      ["kind", "kind"],
      ["q", "query"],
    ])
      if ($(id).value) qs.set(key, $(id).value);
    qs.set("offset",append?state.revisions.length:0);qs.set("limit",200);
    const page=await api("revisions?"+qs);state.revisions=append?[...state.revisions,...page]:page;
    $("load-more").hidden=page.length<200;
    const cuts=await api("cuts?episode="+($('episode').value||1));
    renderBoard(cuts);
    $("revision-list").replaceChildren();
    for (const r of state.revisions) {
      const c = el("div", undefined, "card");
      const b = el("button", r.title + " · " + r.label);
      b.onclick = () => detail(r);
      c.append(b, badges(r), el("small", `${kinds[r.kind]} · ${r.item_key}`));
      $("revision-list").append(c);
    }
    if (!state.revisions.length)
      $("revision-list").append(
        el("p", "업로드 대기 · 등록된 자료가 없습니다."),
      );
    $("cut-grid").replaceChildren();
    if ($("episode").value === "1") {
      for (let i = 1; i <= 68; i++) {
        const found = cuts.find(
          (r) => r.cut_number === i && r.kind === "STORYBOARD",
        );
        const b = el("button", String(i), found ? "ready" : "");
        b.title = `${i}컷 · ${found ? "등록 자료 열람" : "업로드 대기"}`;
        b.setAttribute("aria-label", b.title);
        b.onclick = () => {
          if (found) detail(found);
          else if (state.permission !== "VIEWER") {
            edit();
            const f = $("revision-form");
            f.elements.kind.value = "STORYBOARD";
            f.elements.itemKey.value = "ep01-cut-" + String(i).padStart(2, "0");
            f.elements.cutNumber.value = i;
            f.elements.title.value = `EP01 ${i}컷`;
          }
        };
        $("cut-grid").append(b);
      }
    }
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
        ["pdf", "zip", "png", "mp4", "md", "md5", "json", "html"].includes(f.extension)
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
  document.querySelectorAll("[data-tab]").forEach(
    (b) =>
      (b.onclick = run(async () => {
        document
          .querySelectorAll(".panel")
          .forEach((p) => (p.hidden = p.id !== b.dataset.tab));
        document
          .querySelectorAll("[data-tab]")
          .forEach((t) => t.removeAttribute("aria-current"));
        b.setAttribute("aria-current", "page");
        if (b.dataset.tab === "files") await fileList();
        if (b.dataset.tab === "characters") await gallery();
        if (b.dataset.tab === "memory") await loadMemory();
        if (b.dataset.tab === "inbox") await pending();
        if (b.dataset.tab === "compare") await compareOptions();
        if (b.dataset.tab === "history") {
          const rows = await api("events");
          $("event-list").replaceChildren();
          for (const e of rows) {
            const c = el("div", undefined, "card");
            c.append(
              el("strong", e.action),
              el("p", e.note || e.revision_id || ""),
              el("small", `${e.created_at} · 사용자 ${e.actor_id}`),
            );
            $("event-list").append(c);
          }
        }
        if (b.dataset.tab === "notifications") await notifications(true);
        if (b.dataset.tab === "members") {
          $("member-list").replaceChildren();
          for (const m of await api("members"))
            $("member-list").append(el("p", `${m.user_id} · ${m.permission}`));
        }
      })),
  );
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
  $("new-revision").onclick = () => edit();
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
  $("upload-form").onsubmit = run(async () => {
    const f = $("upload-file").files[0];
    if (f.size > 100 * 1024 * 1024)
      throw Error("100 MiB 이하 파일을 선택하세요.");
    const data = new FormData();
    data.append("file", f);
    const result = await api("files", { method: "POST", body: data });
    await fileList();
    message(
      result.duplicate
        ? "동일 SHA-256의 원본이 이미 보관되어 있습니다."
        : "원본을 보관했습니다. 자료 등록에서 연결할 수 있습니다.",
    );
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
      b.onclick = () => {
        document.querySelector('[data-tab="materials"]').click();
        detail(r);
      };
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
    for(const row of data.items){notificationThrough=Math.max(notificationThrough,row.id);const card=el("article",undefined,"card");card.append(el("strong",({APPROVE:"승인",REJECT:"반려",REQUEST_CHANGES:"수정 요청",COMMENT:"댓글"}[row.action]||row.action)+" · "+row.actor_name),el("h3",row.title||"프로젝트 알림"),el("p",row.id>data.readThrough?"새 알림":"확인한 알림"),el("small",row.created_at));if(row.revision_id){const b=el("button","관련 자료 열기");b.onclick=run(async()=>{document.querySelector('[data-tab="materials"]').click();detail(await api("revisions/"+row.revision_id));});card.append(b);}$("notification-list").append(card);}
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
    document.querySelector('[data-tab="members"]').click();
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
    document.querySelector('[data-tab="members"]').hidden=!project.canManage;
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
      $("upload-form").hidden = true;$("bulk-form").hidden=true;
    }
    $("episode").value = "1";
    await fileList();
    await load();
  })();
})();
