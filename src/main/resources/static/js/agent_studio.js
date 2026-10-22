"use strict";
(() => {
  const $ = (id) => document.getElementById(id);
  const state = {
    revisions: [],
    files: [],
    selected: null,
    permission: "VIEWER",
    preview: null,
  };
  const origins = {
    ORIGINAL: "원작 정본",
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
    const r = await fetch("/api/studio/" + path, {
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
  function display(r) {
    const box = el("div");
    box.append(
      el("h3", r.title),
      badges(r),
      el(
        "p",
        `${kinds[r.kind]} · ${r.episode ? "EP" + String(r.episode).padStart(2, "0") : "공통 설정"}${r.cut_number ? " · " + r.cut_number + "컷" : ""} · ${r.work_status}`,
      ),
    );
    for (const [name, value] of [
      ["본문·설정", r.content],
      ["대사", r.dialogue],
      ["연출", r.direction],
    ])
      if (value) box.append(el("h3", name), el("pre", value));
    if (r.file_id) {
      const a = el("a", "원본 다운로드");
      a.href =
        "/api/studio/files/" + encodeURIComponent(r.file_id) + "/content";
      box.append(a);
    } else box.append(el("p", "파일 업로드 대기", "hint"));
    return box;
  }
  function detail(r) {
    state.selected = r;
    $("detail").replaceChildren(display(r));
    if (state.permission !== "VIEWER") {
      const b = el("button", "이 자료의 다음 버전 작성");
      b.onclick = () => edit(r);
      $("detail").append(b);
    }
    if (state.permission === "APPROVER") {
      const b = el("button", "검수·사용자 승인");
      b.onclick = () => {
        $("review-target").textContent = r.title + " · " + r.label;
        $("review-dialog").showModal();
      };
      $("detail").append(b);
    }
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
  async function load() {
    const qs = new URLSearchParams();
    for (const [key, id] of [
      ["episode", "episode"],
      ["kind", "kind"],
      ["q", "query"],
    ])
      if ($(id).value) qs.set(key, $(id).value);
    state.revisions = await api("revisions?" + qs);
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
        const found = state.revisions.find(
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
    state.files = await api("files");
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
      a.href = "/api/studio/files/" + f.id + "/content";
      c.append(a);
      if (
        ["pdf", "zip", "png", "mp4", "md", "json", "html"].includes(f.extension)
      ) {
        const b = el("button", "자료 열람");
        b.onclick = run(async () => {
          if (state.preview) URL.revokeObjectURL(state.preview);
          if (["pdf", "zip"].includes(f.extension)) {
            const data = await api("files/" + f.id + "/text");
            $("file-preview").replaceChildren(
              el("h3", f.filename),
              el("p", data.note),
              el("pre", data.text || JSON.stringify(data.entries, null, 2)),
            );
            return;
          }
          const response = await fetch(
            "/api/studio/files/" + f.id + "/content",
          );
          if (!response.ok) throw Error("파일 열람 권한을 확인하세요.");
          const blob = await response.blob();
          $("file-preview").replaceChildren(el("h3", f.filename));
          if (["png", "mp4"].includes(f.extension)) {
            state.preview = URL.createObjectURL(
              new Blob([blob], {
                type: f.extension === "png" ? "image/png" : "video/mp4",
              }),
            );
            const media = el(f.extension === "png" ? "img" : "video");
            if (f.extension === "png") media.alt = f.filename;
            else media.controls = true;
            media.src = state.preview;
            $("file-preview").append(media);
          } else
            $("file-preview").append(
              el("pre", (await blob.text()).slice(0, 200000)),
            );
        });
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
      const rows = await api("revisions");
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
        if (b.dataset.tab === "members") {
          $("member-list").replaceChildren();
          for (const m of await api("members"))
            $("member-list").append(el("p", `${m.user_id} · ${m.permission}`));
        }
      })),
  );
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
    state.permission = project.permission;
    $("project-info").textContent =
      `1부 14화 · EP01 68컷 · 보관 파일 ${project.files}개 · ${{ VIEWER: "열람", EDITOR: "제작·수정", APPROVER: "검수·승인" }[project.permission]}`;
    $("initialize").hidden =
      project.initialized || project.permission !== "APPROVER";
    if (state.permission === "VIEWER") {
      $("new-revision").hidden = true;
      $("upload-form").hidden = true;
    }
    $("episode").value = "1";
    await Promise.all([load(), fileList()]);
  })();
})();
