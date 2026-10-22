"use strict";
document.querySelectorAll("[data-revoke]").forEach((button) =>
  button.addEventListener("click", async () => {
    button.disabled = true;
    try {
      const r = await fetch(
        "/api/mcp/connections/" +
          encodeURIComponent(button.dataset.revoke) +
          "/revoke",
        { method: "POST", credentials: "same-origin" },
      );
      if (!r.ok) throw Error("연결 폐기 실패 (" + r.status + ")");
      location.reload();
    } catch (e) {
      document.getElementById("connection-message").textContent = e.message;
      button.disabled = false;
    }
  }),
);
