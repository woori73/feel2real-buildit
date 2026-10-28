(function () {
  "use strict";
  const $ = (id) => document.getElementById(id),
    admin = document.body.dataset.admin === "true";
  const states = {
    PENDING: "전화 확인 대기",
    VERIFIED: "확인 완료 · 한 건 신청 가능",
    USED: "신청에 사용됨",
    REVOKED: "철회됨",
    REJECTED: "동의 거절·확인 불가",
  };
  const node = (tag, text) => {
    const e = document.createElement(tag);
    if (text) e.textContent = text;
    return e;
  };
  async function api(path, body) {
    const r = await fetch(path, {
      method: body ? "POST" : "GET",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", "X-F2R-Exam": "1" },
      body: body ? JSON.stringify(body) : undefined,
    });
    const v = await r.json();
    if (!r.ok) throw new Error(v.message || "요청을 처리할 수 없습니다.");
    return v;
  }
  async function load() {
    const rows = await api(
      admin
        ? "/api/admin/exam-intake/guardian-consents"
        : "/api/exam-intake/guardian-consents",
    );
    $("consents").replaceChildren();
    for (const r of rows) {
      const article = node("article");
      article.append(
        node("strong", r.guardian_name),
        node("p", r.phone + " · " + (states[r.state] || r.state)),
        node("small", r.id),
      );
      if (admin && r.state === "PENDING") {
        const f = node("form");
        for (const [name, text] of Object.entries({
          identityChecked: "실제 전화로 신원을 확인했습니다.",
          authorityChecked: "법정대리 권한을 확인했습니다.",
          explainedAndAgreed:
            "수집 항목·목적·기간·거부권을 설명하고 동의 의사를 확인했습니다.",
        })) {
          const label = node("label", text);
          label.className = "check";
          const input = node("input");
          input.type = "checkbox";
          input.name = name;
          input.required = true;
          label.prepend(input);
          f.append(label);
        }
        const b = node("button", "전화 확인 완료 기록");
        b.type = "submit";
        f.append(b);
        f.onsubmit = async (e) => {
          e.preventDefault();
          b.disabled = true;
          try {
            await api(
              "/api/admin/exam-intake/guardian-consents/" + r.id + "/verify",
              Object.fromEntries([...new FormData(f)].map(([k]) => [k, true])),
            );
            await load();
          } catch (x) {
            $("notice").textContent = x.message;
          } finally {
            b.disabled = false;
          }
        };
        article.append(f);
        for (const [result, label] of [
          ["DECLINED", "동의 거절 기록"],
          ["UNVERIFIED", "법정대리인 확인 불가 기록"],
        ]) {
          const deny = node("button", label);
          deny.type = "button";
          deny.onclick = async () => {
            deny.disabled = true;
            try {
              await api(
                "/api/admin/exam-intake/guardian-consents/" + r.id + "/reject",
                { result },
              );
              await load();
            } catch (e) {
              $("notice").textContent = e.message;
            } finally {
              deny.disabled = false;
            }
          };
          article.append(deny);
        }
      }
      if (r.state !== "REVOKED") {
        const b = node("button", "동의 철회");
        b.type = "button";
        b.onclick = async () => {
          b.disabled = true;
          try {
            await api(
              "/api/exam-intake/guardian-consents/" + r.id + "/revoke",
              {},
            );
            await load();
            $("notice").textContent =
              "동의를 철회했습니다. 계약 취소·자료 삭제가 필요하면 개인정보 요청을 남겨 주세요.";
          } catch (e) {
            $("notice").textContent = e.message;
          } finally {
            b.disabled = false;
          }
        };
        article.append(b);
      }
      $("consents").append(article);
    }
  }
  if ($("guardianForm"))
    $("guardianForm").onsubmit = async (e) => {
      e.preventDefault();
      const f = e.target,
        b = f.querySelector("button");
      b.disabled = true;
      try {
        const data = Object.fromEntries(new FormData(f));
        data.adultGuardian = data.adultGuardian === "on";
        data.consent = data.consent === "on";
        data.privacyVersion = document.body.dataset.privacy;
        data.termsVersion = document.body.dataset.terms;
        await api("/api/exam-intake/guardian-consents", data);
        f.reset();
        await load();
        $("notice").textContent =
          "확인 요청을 접수했습니다. 전화 확인 전에는 아동 정보를 제출하지 마세요.";
      } catch (x) {
        $("notice").textContent = x.message;
      } finally {
        b.disabled = false;
      }
    };
  $("refresh").onclick = () =>
    load().catch((e) => ($("notice").textContent = e.message));
  load().catch((e) => ($("notice").textContent = e.message));
})();
