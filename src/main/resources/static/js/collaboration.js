"use strict";
(() => {
  const $ = (s, r = document) => r.querySelector(s),
    state = {
      workspace: "ai-agent-part1",
      space: null,
      task: null,
      busy: false,
      fileItems: [],
      filesBefore: "",
      compare: [],
      cuts: [],
      cutPage: 0,
    };
  const labels = {
    GPT: "GPT · 기획·조정",
    MUSE: "Muse · 선택 참여",
    CODEX: "Codex · 제작·수정 제안",
    CLAUDE: "Claude · 검수",
  };
  const statuses = {
    DRAFT: "등록",
    WAITING_AGENT: "구독 AI 담당자 인계 대기",
    PAUSED: "중단",
    WAITING_APPROVAL: "사용자 승인 대기",
    APPROVED: "작업 결과 승인",
    CANCELLED: "취소",
    BLOCKED: "인계 상한·검수 확인 필요",
    RUNNING: "기존 상태: 중단 후 인계로 전환",
    UNKNOWN: "이전 실행 상태 확인 필요",
  };
  const status = (s) => ($("#status").textContent = s);
  async function api(path, data) {
    const response = await fetch("/api/collaboration/" + path, {
      credentials: "same-origin",
      cache: "no-store",
      ...(data === undefined
        ? {}
        : {
            method: "POST",
            ...(data instanceof FormData
              ? { body: data }
              : {
                  headers: { "Content-Type": "application/json" },
                  body: JSON.stringify(data),
                }),
          }),
    });
    let result;
    try {
      result = await response.json();
    } catch {
      throw Error("서버 응답을 확인하세요.");
    }
    if (!response.ok) throw Error(result.message || "요청이 거부되었습니다.");
    return result;
  }
  async function action(work) {
    if (state.busy) return;
    state.busy = true;
    $("#workspace").disabled = true;
    status("처리 중…");
    try {
      await work();
      status("저장했습니다.");
    } catch (e) {
      status(e.message);
    } finally {
      state.busy = false;
      $("#workspace").disabled = false;
    }
  }
  function node(tag, text) {
    const el = document.createElement(tag);
    if (text !== undefined) el.textContent = text;
    return el;
  }
  function eventNode(event) {
    const li = node("li"),
      h = node(
        "strong",
        `${event.actor || event.actor_agent || "USER"} · ${event.event_type}`,
      ),
      time = node("time", String(event.created_at)),
      body = node("pre", event.body);
    li.append(h, time, body);
    return li;
  }
  async function workspaces() {
    const spaces = await api("workspaces"),
      select = $("#workspace");
    select.replaceChildren();
    for (const w of spaces) {
      const option = node("option", w.title);
      option.value = w.id;
      select.append(option);
    }
    select.value = state.workspace;
  }
  async function refresh() {
    state.space = await api(
      "workspaces/" + encodeURIComponent(state.workspace),
    );
    await taskList();
    renderMemory();
    await connections();
    await loadFiles(false);
    await loadProduction();
  }
  async function taskList() {
    const list = $("#tasks");
    list.replaceChildren();
    const tasks = await api(
      "tasks?workspaceId=" + encodeURIComponent(state.workspace),
    );
    if (!tasks.length) list.textContent = "아직 등록한 작업이 없습니다.";
    for (const task of tasks) {
      const button = node("button", task.title);
      button.className = "task-card";
      button.type = "button";
      button.setAttribute("aria-current", String(state.task?.id === task.id));
      button.append(
        node(
          "small",
          `${statuses[task.status] || task.status} · ${task.participants}`,
        ),
      );
      button.onclick = () => action(() => selectTask(task.id));
      list.append(button);
    }
  }
  async function selectTask(id) {
    state.task = await api("tasks/" + encodeURIComponent(id));
    renderTask();
    await taskList();
  }
  function renderTask() {
    const task = state.task,
      panel = $("#task-panel");
    panel.replaceChildren($("#detail-template").content.cloneNode(true));
    $(".title", panel).textContent = task.title;
    $(".instruction", panel).textContent = task.instruction;
    $(".summary", panel).textContent =
      `${statuses[task.status] || task.status} · 다음 담당 ${task.nextAgent || "사용자"} · 인계 ${task.calls_used}/${task.max_calls}회 · 별도 모델 API 호출 0 · 버전 ${task.version}`;
    $(".export", panel).href =
      `/api/collaboration/tasks/${encodeURIComponent(task.id)}/export`;
    const allowed = {
      start: task.status === "DRAFT",
      resume: ["PAUSED", "BLOCKED", "UNKNOWN"].includes(task.status),
      pause: ["DRAFT", "WAITING_AGENT", "WAITING_APPROVAL", "RUNNING"].includes(
        task.status,
      ),
      approve: task.status === "WAITING_APPROVAL",
      cancel: !["APPROVED", "CANCELLED"].includes(task.status),
    };
    for (const button of panel.querySelectorAll("[data-action]")) {
      button.disabled = !task.canControl || !allowed[button.dataset.action];
      button.onclick = () =>
        action(async () => {
          await api(`tasks/${encodeURIComponent(task.id)}/control`, {
            version: task.version,
            action: button.dataset.action,
            externalConsent: $(".consent", panel).checked,
            acknowledgeUnknown: false,
          });
          await selectTask(task.id);
        });
    }
    for (const artifact of task.artifacts) {
      const li = node("li");
      if (artifact.file_id) {
        const a = node("a", `${artifact.filename} · SHA ${artifact.sha256}`);
        a.href = `/api/studio/files/${encodeURIComponent(artifact.file_id)}/content`;
        li.append(a);
      } else if (artifact.pr_number) {
        const a = node("a", "PR #" + artifact.pr_number);
        a.href = `https://github.com/woori73/feel2real-buildit/pull/${Number(artifact.pr_number)}`;
        a.rel = "noopener noreferrer";
        a.target = "_blank";
        li.append(a);
      } else li.textContent = "제작실 버전 " + artifact.revision_id;
      $(".artifacts", panel).append(li);
    }
    const attach = $(".attach-form", panel);
    attach.onsubmit = (e) => {
      e.preventDefault();
      action(async () => {
        const kind = attach.elements.kind.value;
        await api(`tasks/${encodeURIComponent(task.id)}/artifacts`, {
          version: task.version,
          [kind]:
            kind === "prNumber"
              ? Number(attach.elements.ref.value)
              : attach.elements.ref.value.trim(),
        });
        await selectTask(task.id);
      });
    };
    const git = $(".github-form", panel);
    git.onsubmit = (e) => {
      e.preventDefault();
      action(async () => {
        await api(`tasks/${encodeURIComponent(task.id)}/github-sync`, {
          version: task.version,
          prNumber: Number(git.elements.pr.value),
        });
        await selectTask(task.id);
      });
    };
    const form = $(".message-form", panel);
    form.onsubmit = (e) => {
      e.preventDefault();
      action(async () => {
        await api(`tasks/${encodeURIComponent(task.id)}/messages`, {
          version: task.version,
          body: form.elements.body.value,
        });
        await selectTask(task.id);
      });
    };
    for (const f of panel.querySelectorAll("form"))
      for (const input of f.elements)
        input.disabled =
          (f === form ? !task.canComment : !task.canControl) ||
          ["APPROVED", "CANCELLED"].includes(task.status);
    for (const event of [...task.events].reverse())
      $(".timeline", panel).append(eventNode(event));
    let oldest = task.events.length
      ? task.events[task.events.length - 1].id
      : 0;
    const older = $(".older", panel);
    older.hidden = task.events.length < 200;
    older.onclick = () =>
      action(async () => {
        const rows = await api(
            `tasks/${encodeURIComponent(task.id)}/events?beforeId=${Number(oldest)}`,
          ),
          fragment = document.createDocumentFragment();
        for (const row of [...rows].reverse()) fragment.append(eventNode(row));
        $(".timeline", panel).prepend(fragment);
        if (rows.length) oldest = rows[rows.length - 1].id;
        older.hidden = rows.length < 200;
      });
  }
  function renderMemory() {
    const space = state.space;
    $("#memory-list").replaceChildren();
    for (const event of space.memory)
      $("#memory-list").append(eventNode(event));
    $("#project-members").hidden = space.permission !== "MANAGER";
    $("#members").textContent = (space.members || [])
      .map((m) => `${m.user_id}: ${m.permission}`)
      .join(" · ");
    for (const id of ["memory-form", "file-upload"])
      for (const input of $("#" + id).elements)
        input.disabled = space.permission === "VIEWER";
  }
  async function loadFiles(append = false) {
    const page = await api(
      `workspaces/${encodeURIComponent(state.workspace)}/files?limit=100&beforeId=${encodeURIComponent(append ? state.filesBefore : "")}&query=${encodeURIComponent($("#file-query").value)}`,
    );
    state.fileItems = append ? [...state.fileItems, ...page.items] : page.items;
    state.filesBefore = page.nextBeforeId;
    $("#file-count").textContent =
      `${state.fileItems.length} / ${page.totalCount}개 열람 · 새 등록은 DRAFT`;
    $("#files-more").hidden = !page.nextBeforeId;
    renderFiles();
  }
  function imageDetails(file) {
    return `${file.source_path || file.filename} · ${file.version_label || "신규"} · SHA256 ${file.sha256} · ${file.source_approval === "APPROVED_REFERENCE" ? "기존 승인 참조(출처) · 신규 등록 DRAFT" : file.status || "DRAFT"}`;
  }
  function showImage(file) {
    $("#image-title").textContent = file.filename;
    $("#image-large").src =
      `/api/collaboration/files/${encodeURIComponent(file.id)}/content?inline=true`;
    $("#image-large").alt = file.filename;
    $("#image-details").textContent = imageDetails(file);
    $("#image-dialog").showModal();
  }
  function selectCompare(file) {
    const index = state.compare.findIndex((x) => x.id === file.id);
    if (index >= 0) state.compare.splice(index, 1);
    else {
      if (state.compare.length === 2) state.compare.shift();
      state.compare.push(file);
    }
    $("#compare-count").textContent = `${state.compare.length}/2 선택`;
    $("#compare-open").disabled = state.compare.length !== 2;
  }
  async function showDocument(file) {
    $("#document-title").textContent = file.filename;
    $("#document-details").textContent = imageDetails(file);
    const view = $("#document-view");
    view.replaceChildren();
    if (file.extension === "html") {
      const frame = node("iframe");
      frame.title = file.filename;
      frame.setAttribute("sandbox", "");
      frame.src = `/api/collaboration/files/${encodeURIComponent(file.id)}/preview`;
      view.append(frame);
    } else if (file.extension === "pdf") {
      const info = await api(`files/${encodeURIComponent(file.id)}/pdf-info`);
      let page = 1;
      const label = node("p"),
        controls = node("div"),
        prev = node("button", "이전 페이지"),
        next = node("button", "다음 페이지"),
        image = node("img");
      image.alt = file.filename + " PDF 페이지";
      const update = () => {
        label.textContent = `${page} / ${info.pages}쪽 · 원본 SHA 보존`;
        image.src = `/api/collaboration/files/${encodeURIComponent(file.id)}/pdf-page?page=${page}`;
        prev.disabled = page === 1;
        next.disabled = page === info.pages;
      };
      prev.onclick = () => {
        page--;
        update();
      };
      next.onclick = () => {
        page++;
        update();
      };
      controls.className = "actions";
      controls.append(prev, label, next);
      view.append(controls, image);
      update();
    } else {
      const result = await api(`files/${encodeURIComponent(file.id)}/text`),
        content = node("article");
      content.className = "document-text";
      let table = null,
        body = null;
      for (const line of (result.text || result.note || "").split("\n")) {
        if (line.startsWith("|") && line.endsWith("|")) {
          if (/^\|[\s:|\-]+\|$/.test(line)) continue;
          const cells = line
              .split("|")
              .slice(1, -1)
              .map((c) => c.trim()),
            row = node("tr");
          if (!table) {
            table = node("table");
            const head = node("thead");
            for (const cell of cells) row.append(node("th", cell));
            head.append(row);
            body = node("tbody");
            table.append(head, body);
            content.append(table);
          } else {
            for (const cell of cells) row.append(node("td", cell));
            body.append(row);
          }
          continue;
        }
        table = null;
        const heading = /^(#{1,6}) (.*)$/.exec(line);
        content.append(
          node(
            heading ? "h" + Math.min(heading[1].length + 1, 6) : "p",
            heading ? heading[2] : line || " ",
          ),
        );
      }
      view.append(content);
    }
    $("#document-dialog").showModal();
  }
  function renderFiles() {
    const list = $("#shared-files");
    list.replaceChildren();
    for (const file of state.fileItems) {
      const card = node("article");
      card.className = "file-card";
      card.dataset.fileId = file.id;
      card.append(
        node("h3", file.filename),
        node(
          "p",
          `${file.status} · 등록 ${file.registered_agent || "인간 팀원"} · 사용자 ${file.registered_by} · ${file.created_at}`,
        ),
        node("p", `SHA256 ${file.sha256}`),
      );
      if (file.source_path)
        card.append(
          node("small", file.source_path),
          node(
            "p",
            `${file.version_label} · ${file.category} · ${file.source_approval === "APPROVED_REFERENCE" ? "기존 승인 참조(출처) · 정본 포인터 불변" : "출처 보존·정본 미검수"}`,
          ),
        );
      if (file.extension === "md5")
        card.append(
          node("p", "검증용 메타자료 · 이미지 아님 · 원본 byte 보존"),
        );
      if (file.parent_id)
        card.append(node("p", "수정 제안 원본 ID: " + file.parent_id));
      const link = node("a", "원본 내려받기");
      link.href = `/api/collaboration/files/${encodeURIComponent(file.id)}/content`;
      card.append(link, node("small", "파일 ID: " + file.id));
      if (file.extension === "png") {
        const img = node("img");
        img.src = `/api/collaboration/files/${encodeURIComponent(file.id)}/content?inline=true`;
        img.alt = file.filename;
        img.loading = "lazy";
        card.append(img);
        const zoom = node("button", "이미지 확대"),
          compare = node("button", "비교에 추가");
        zoom.onclick = () => showImage(file);
        compare.onclick = () => selectCompare(file);
        card.append(zoom, compare);
      } else if (
        ["md", "md5", "json", "html", "pdf"].includes(file.extension)
      ) {
        const text = node("button", "텍스트 열람"),
          viewer = node("button", "문서 보기");
        text.onclick = () =>
          action(async () => {
            const data = await api(`files/${encodeURIComponent(file.id)}/text`),
              pre = $("#file-preview");
            pre.hidden = false;
            pre.textContent = data.text || data.note;
          });
        viewer.onclick = () => action(() => showDocument(file));
        card.append(text, viewer);
      }
      if (file.status === "DRAFT" && state.space.permission === "MANAGER")
        for (const decision of ["APPROVED", "REJECTED"]) {
          const b = node(
            "button",
            decision === "APPROVED" ? "협업 후보 승인" : "후보 반려",
          );
          b.onclick = () =>
            action(async () => {
              await api(`files/${encodeURIComponent(file.id)}/review`, {
                decision,
              });
              await refresh();
            });
          card.append(b);
        }
      list.append(card);
    }
  }
  async function loadProduction() {
    const result = await api(
      `workspaces/${encodeURIComponent(state.workspace)}/production`,
    );
    state.cuts = result.cuts;
    state.cutPage = 0;
    $("#production-board").hidden = !state.cuts.length;
    const select = $("#cut-page");
    select.replaceChildren();
    for (let i = 0; i < state.cuts.length; i += 12) {
      const option = node(
        "option",
        `${i + 1}–${Math.min(i + 12, state.cuts.length)}컷`,
      );
      option.value = String(i / 12);
      select.append(option);
    }
    $("#production-summary").textContent = result.batches.length
      ? `등록 ${result.batches[0].registered_files}개 · 출처 ${result.batches[0].source_ref} · 이미지 원본/설명/버전 이력 보존`
      : "";
    renderCuts();
  }
  function renderCuts() {
    const grid = $("#cut-grid");
    grid.replaceChildren();
    $("#cut-page").value = String(state.cutPage);
    $("#cuts-prev").disabled = state.cutPage === 0;
    $("#cuts-next").disabled = (state.cutPage + 1) * 12 >= state.cuts.length;
    for (const cut of state.cuts.slice(
      state.cutPage * 12,
      state.cutPage * 12 + 12,
    )) {
      const record = JSON.parse(cut.record_json),
        card = node("article"),
        file = {
          id: cut.panel_file_id,
          filename: `컷 ${cut.cut_number}`,
          sha256: cut.sha256,
          source_path: cut.source_path,
          version_label: cut.version_label,
          status: "DRAFT",
        };
      card.className = "cut-card";
      card.dataset.cut = String(cut.cut_number);
      const img = node("img");
      img.src = `/api/collaboration/files/${encodeURIComponent(cut.panel_file_id)}/content?inline=true`;
      img.alt = `EP01 컷 ${cut.cut_number} 러프 콘티`;
      img.loading = "lazy";
      card.append(
        node(
          "h3",
          `컷 ${String(cut.cut_number).padStart(2, "0")} · ${record.beat || ""} · ${record.tc_start || ""}–${record.tc_end || ""}`,
        ),
        img,
        node("p", record.composition_v3_screen_text || ""),
        node(
          "p",
          "연출: " +
            (record.action_expression_r5 || record.action_expression_r14 || ""),
        ),
        node(
          "p",
          "대사: " +
            JSON.stringify(record.dialogue_r5 || record.dialogue || []),
        ),
        node(
          "small",
          "출처 r5 설명 + 보존 러프 / 정식 원작 미대조 / PASS 미확정",
        ),
      );
      const zoom = node("button", "컷 확대"),
        compare = node("button", "비교에 추가");
      zoom.onclick = () => showImage(file);
      compare.onclick = () => selectCompare(file);
      card.append(zoom, compare);
      grid.append(card);
    }
  }
  async function connections() {
    const rows = await api("connections"),
      select = $("#binding-grant"),
      list = $("#bindings");
    select.replaceChildren();
    list.replaceChildren();
    for (const row of rows) {
      if (row.revoked) continue;
      const option = node("option", `${row.name} · ${row.scopes}`);
      option.value = row.id;
      select.append(option);
      const item = node(
        "p",
        `${row.name}: ${row.agent || "AI 미지정"} · 프로젝트 ${row.workspace_id || "미지정"}`,
      );
      if (row.workspace_id) {
        const b = node("button", "프로젝트 연결 해제");
        b.onclick = () =>
          action(async () => {
            await api(`connections/${encodeURIComponent(row.id)}/unbind`, {});
            await refresh();
          });
        item.append(b);
      }
      list.append(item);
    }
    if (!rows.length)
      list.textContent =
        "아직 OAuth 연결이 없습니다. MCP 연결 설명서에 따라 연결한 뒤 지정하세요.";
  }
  $("#workspace").onchange = () =>
    action(async () => {
      state.workspace = $("#workspace").value;
      state.task = null;
      state.compare = [];
      $("#file-query").value = "";
      $("#task-panel").replaceChildren(
        node("h2", "프로젝트 작업을 선택하세요"),
      );
      await refresh();
    });
  $("#workspace-create").onsubmit = (e) => {
    e.preventDefault();
    action(async () => {
      const space = await api("workspaces", {
        title: $("#workspace-title").value,
      });
      state.workspace = space.id;
      state.task = null;
      await workspaces();
      await refresh();
    });
  };
  $("#create-task").onsubmit = (e) => {
    e.preventDefault();
    action(async () => {
      const task = await api("tasks", {
        title: $("#task-title").value,
        instruction: $("#task-instruction").value,
        workspaceId: state.workspace,
        participants: [
          ...document.querySelectorAll("[name=participant]:checked"),
        ].map((c) => c.value),
        maxCalls: Number($("#max-calls").value),
        budgetMicrousd: 0,
      });
      await selectTask(task.id);
    });
  };
  $("#memory-form").onsubmit = (e) => {
    e.preventDefault();
    action(async () => {
      await api(`workspaces/${encodeURIComponent(state.workspace)}/memory`, {
        kind: $("#memory-kind").value,
        body: $("#memory-body").value,
      });
      $("#memory-body").value = "";
      await refresh();
    });
  };
  $("#memory-search").onsubmit = (e) => {
    e.preventDefault();
    action(async () => {
      const rows = await api(
          `search?workspaceId=${encodeURIComponent(state.workspace)}&q=${encodeURIComponent($("#memory-query").value)}`,
        ),
        list = $("#search-results");
      list.replaceChildren();
      for (const row of rows) {
        const item = node("p", `${row.kind} · ${row.body}`);
        if (row.kind === "TASK") {
          const b = node("button", "작업 열기");
          b.onclick = () => action(() => selectTask(row.source_id));
          item.append(b);
        }
        list.append(item);
      }
    });
  };
  $("#file-upload").onsubmit = (e) => {
    e.preventDefault();
    action(async () => {
      const body = new FormData();
      body.set("workspaceId", state.workspace);
      body.set("file", $("#upload-file").files[0]);
      if ($("#parent-file").value.trim())
        body.set("parentId", $("#parent-file").value.trim());
      await api("files", body);
      $("#file-upload").reset();
      await refresh();
    });
  };
  $("#binding-form").onsubmit = (e) => {
    e.preventDefault();
    action(async () => {
      await api("connections", {
        grantId: $("#binding-grant").value,
        workspaceId: state.workspace,
        agent: $("#binding-agent").value,
        acknowledgeDraftAppend: $("#binding-ack").checked,
      });
      await refresh();
    });
  };
  $("#member-form").onsubmit = (e) => {
    e.preventDefault();
    action(async () => {
      await api(`workspaces/${encodeURIComponent(state.workspace)}/members`, {
        userId: Number($("#member-id").value),
        permission: $("#member-permission").value,
      });
      await refresh();
    });
  };
  $("#files-more").onclick = () => action(() => loadFiles(true));
  $("#file-search").onsubmit = (e) => {
    e.preventDefault();
    action(() => loadFiles(false));
  };
  $("#cut-page").onchange = () => {
    state.cutPage = Number($("#cut-page").value);
    renderCuts();
  };
  $("#cuts-prev").onclick = () => {
    state.cutPage--;
    renderCuts();
  };
  $("#cuts-next").onclick = () => {
    state.cutPage++;
    renderCuts();
  };
  $("#compare-open").onclick = () => {
    const list = $("#compare-images");
    list.replaceChildren();
    for (const file of state.compare) {
      const figure = node("figure"),
        img = node("img");
      img.src = `/api/collaboration/files/${encodeURIComponent(file.id)}/content?inline=true`;
      img.alt = file.filename;
      figure.append(img, node("figcaption", imageDetails(file)));
      list.append(figure);
    }
    $("#compare-dialog").showModal();
  };
  for (const b of document.querySelectorAll(".dialog-close"))
    b.onclick = () => b.closest("dialog").close();
  $("#refresh").onclick = () =>
    action(async () => {
      await refresh();
      if (state.task) await selectTask(state.task.id);
    });
  action(async () => {
    for (const name of ["GPT", "MUSE", "CODEX", "CLAUDE"]) {
      const label = node("label"),
        input = node("input"),
        span = node("span", labels[name] + " · 구독 커넥터");
      input.type = "checkbox";
      input.name = "participant";
      input.value = name;
      label.append(input, span);
      $("#agents").append(label);
    }
    await workspaces();
    await refresh();
  });
})();
