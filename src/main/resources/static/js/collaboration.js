"use strict";
(() => {
  const $ = (selector, root = document) => root.querySelector(selector);
  const state = { task: null, busy: false, tasks: [] };
  const labels = {
    GPT: "GPT · 기획·조정",
    CODEX: "Codex · 구현 제안",
    CLAUDE: "Claude · 독립 검토 제안",
    MUSE: "Muse · 선택 참여",
  };
  const statuses = {
    DRAFT: "등록",
    RUNNING: "진행",
    PAUSED: "중단",
    WAITING_APPROVAL: "사용자 승인 대기",
    APPROVED: "결과 승인",
    CANCELLED: "취소",
    BLOCKED: "연결·상한·검수 확인 필요",
    UNKNOWN: "호출 결과 확인 필요",
  };
  const money = (value) => (Number(value) / 1000000).toFixed(4);
  const status = (text) => {
    $("#status").textContent = text;
  };
  async function api(path, data) {
    const response = await fetch("/api/collaboration/" + path, {
      credentials: "same-origin",
      cache: "no-store",
      ...(data === undefined
        ? {}
        : {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data),
          }),
    });
    let result;
    try {
      result = await response.json();
    } catch {
      throw new Error("서버 응답을 확인하세요.");
    }
    if (!response.ok)
      throw new Error(result.message || "요청이 거부되었습니다.");
    return result;
  }
  async function action(work) {
    if (state.busy) return;
    state.busy = true;
    status("처리 중…");
    try {
      await work();
      status("저장했습니다.");
    } catch (error) {
      status(error.message);
    } finally {
      state.busy = false;
    }
  }
  async function refresh() {
    state.tasks = await api("tasks");
    const list = $("#tasks");
    list.replaceChildren();
    if (!state.tasks.length) list.textContent = "등록한 작업이 없습니다.";
    for (const task of state.tasks) {
      const button = document.createElement("button");
      button.className = "task-card";
      button.type = "button";
      button.setAttribute("aria-current", String(state.task?.id === task.id));
      button.textContent = task.title;
      const small = document.createElement("small");
      small.textContent = `${statuses[task.status] || task.status} · ${task.participants}`;
      button.append(small);
      button.addEventListener("click", () => action(() => select(task.id)));
      list.append(button);
    }
  }
  async function select(id) {
    state.task = await api("tasks/" + encodeURIComponent(id));
    render();
    await refresh();
  }
  function render() {
    const task = state.task,
      panel = $("#task-panel");
    panel.replaceChildren($("#detail-template").content.cloneNode(true));
    $(".title", panel).textContent = task.title;
    $(".instruction", panel).textContent = task.instruction;
    $(".summary", panel).textContent =
      `${statuses[task.status] || task.status} · ${task.participants} · 호출 ${task.calls_used}/${task.max_calls}회 · 보수적 예약 비용 $${money(task.reserved_microusd)} / $${money(task.budget_microusd)} · 버전 ${task.version}`;
    $(".export", panel).href =
      `/api/collaboration/tasks/${encodeURIComponent(task.id)}/export`;
    const allowed = {
      start: task.status === "DRAFT",
      resume:
        ["PAUSED", "UNKNOWN", "BLOCKED"].includes(task.status) &&
        !task.active_run,
      pause: ["DRAFT", "RUNNING", "WAITING_APPROVAL"].includes(task.status),
      approve: task.status === "WAITING_APPROVAL",
      cancel: !["APPROVED", "CANCELLED"].includes(task.status),
      recover: !!task.active_run,
    };
    for (const button of panel.querySelectorAll("[data-action]")) {
      button.disabled = !task.canControl || !allowed[button.dataset.action];
      button.addEventListener("click", () =>
        action(async () => {
          await api(`tasks/${encodeURIComponent(task.id)}/control`, {
            version: task.version,
            action: button.dataset.action,
            externalConsent: $(".consent", panel).checked,
            acknowledgeUnknown: $(".unknown", panel).checked,
          });
          await select(task.id);
        }),
      );
    }
    for (const artifact of task.artifacts) {
      const li = document.createElement("li");
      if (artifact.file_id) {
        const link = document.createElement("a");
        link.href = `/api/studio/files/${encodeURIComponent(artifact.file_id)}/content`;
        link.textContent = `${artifact.filename} · SHA ${artifact.sha256}`;
        li.append(link);
      } else if (artifact.pr_number) {
        const link = document.createElement("a");
        link.href = `https://github.com/woori73/feel2real-buildit/pull/${Number(artifact.pr_number)}`;
        link.target = "_blank";
        link.rel = "noopener noreferrer";
        link.textContent = "PR #" + artifact.pr_number;
        li.append(link);
      } else {
        li.textContent = "제작실 버전 " + artifact.revision_id;
      }
      $(".artifacts", panel).append(li);
    }
    const attach = $(".attach-form", panel);
    attach.addEventListener("submit", (event) => {
      event.preventDefault();
      action(async () => {
        const kind = attach.elements.kind.value;
        await api(`tasks/${encodeURIComponent(task.id)}/artifacts`, {
          version: task.version,
          [kind]:
            kind === "prNumber"
              ? Number(attach.elements.ref.value)
              : attach.elements.ref.value.trim(),
        });
        await select(task.id);
      });
    });
    const github = $(".github-form", panel);
    github.addEventListener("submit", (event) => {
      event.preventDefault();
      action(async () => {
        await api(`tasks/${encodeURIComponent(task.id)}/github-sync`, {
          version: task.version,
          prNumber: Number(github.elements.pr.value),
        });
        await select(task.id);
      });
    });
    const form = $(".message-form", panel);
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      action(async () => {
        await api(`tasks/${encodeURIComponent(task.id)}/messages`, {
          version: task.version,
          body: form.elements.body.value,
        });
        await select(task.id);
      });
    });
    for (const form of panel.querySelectorAll("form"))
      for (const input of form.elements)
        input.disabled =
          !task.canControl || ["APPROVED", "CANCELLED"].includes(task.status);
    function eventNode(event) {
      const li = document.createElement("li"),
        heading = document.createElement("strong"),
        time = document.createElement("time"),
        body = document.createElement("pre");
      heading.textContent = `${event.actor} · ${event.event_type}`;
      time.textContent = String(event.created_at);
      body.textContent = event.body;
      li.append(heading, time, body);
      return li;
    }
    for (const event of [...task.events].reverse())
      $(".timeline", panel).append(eventNode(event));
    let oldest = task.events.length
      ? task.events[task.events.length - 1].id
      : 0;
    const older = $(".older", panel);
    older.hidden = task.events.length < 200;
    older.addEventListener("click", () =>
      action(async () => {
        const events = await api(
          `tasks/${encodeURIComponent(task.id)}/events?beforeId=${Number(oldest)}`,
        );
        const fragment = document.createDocumentFragment();
        for (const event of [...events].reverse())
          fragment.append(eventNode(event));
        $(".timeline", panel).prepend(fragment);
        if (events.length) oldest = events[events.length - 1].id;
        older.hidden = events.length < 200;
      }),
    );
  }
  $("#create-task").addEventListener("submit", (event) => {
    event.preventDefault();
    action(async () => {
      const task = await api("tasks", {
        title: $("#task-title").value,
        instruction: $("#task-instruction").value,
        participants: [
          ...document.querySelectorAll("[name=participant]:checked"),
        ].map((input) => input.value),
        maxCalls: Number($("#max-calls").value),
        budgetMicrousd: Math.round(Number($("#budget").value) * 1000000),
      });
      await select(task.id);
    });
  });
  $("#refresh").addEventListener("click", () =>
    action(async () => {
      if (state.task) await select(state.task.id);
      else await refresh();
    }),
  );
  action(async () => {
    const agents = await api("agents");
    for (const name of ["GPT", "MUSE", "CODEX", "CLAUDE"]) {
      const label = document.createElement("label"),
        check = document.createElement("input"),
        span = document.createElement("span");
      check.type = "checkbox";
      check.name = "participant";
      check.value = name;
      span.textContent =
        labels[name] +
        (agents[name]?.enabled
          ? " · API 설정됨 (실연결 미검증)"
          : " · 호출 준비 전");
      label.append(check, span);
      $("#agents").append(label);
    }
    await refresh();
  });
  // Poll status without rerendering typed messages or resetting explicit consent.
  setInterval(async () => {
    if (state.busy || !state.task || state.task.status !== "RUNNING") return;
    try {
      const task = await api("tasks/" + encodeURIComponent(state.task.id));
      if (task.version !== state.task.version)
        status("새 협업 이력이 있습니다. 목록 새로고침으로 확인하세요.");
    } catch {
      /* Explicit refresh displays actionable errors. */
    }
  }, 5000);
})();
