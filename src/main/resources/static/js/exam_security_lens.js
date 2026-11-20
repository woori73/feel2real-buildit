/**
 * F2R 시험 보안 검사대 — 렌즈 반사 후보 찾기(시연 화면 /easy/exam-security/lens).
 *
 * 원리: 카메라 렌즈는 빛을 들어온 방향으로 되돌려 보낸다(재귀 반사). 휴대폰 플래시를 켠 장면과 끈 장면을
 * 같은 자리에서 찍어 비교하면, "플래시를 켰을 때만 작고 밝게 빛나는 점"이 렌즈 후보가 된다.
 * 늘 밝은 전등(켜고 끈 차이가 없음)과 넓게 번쩍이는 면(안경알·창문·흰 벽)은 뺀다.
 *
 * - 기계는 판정하지 않는다. 결과는 "의심 없음 / 확인 필요"뿐이고 감독관이 화면을 보고 처리한다.
 * - 사진·영상을 서버로 보내지도, 휴대폰에 저장하지도 않는다. 프레임은 메모리에서만 비교하고 버린다.
 *
 * 순수 함수는 Node 테스트(module.exports), 화면 연결은 브라우저(window.ExamSecurityLens)에서 쓴다.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.ExamSecurityLens = api;
  if (typeof document !== 'undefined' && document.getElementById('lensApp')) api.mountPage(document);
})(typeof self !== 'undefined' ? self : this, function () {
  /** 기본 기준값. 1주차 시험(TEST_PLAN.md)에서 실제 값으로 다시 정한다. */
  const DEFAULTS = Object.freeze({
    onMin: 200,        // 플래시를 켰을 때 이 밝기(0~255) 이상이어야 한다
    diffMin: 60,       // 켰을 때 - 껐을 때 밝기 차이가 이만큼 이상이어야 한다
    minArea: 1,        // 후보 점의 최소 픽셀 수
    maxAreaRatio: 0.002, // 화면의 0.2%보다 큰 밝은 면은 렌즈가 아니라 반사면으로 본다
    minFill: 0.3,      // 둥근 점인지: 점 픽셀 수 ÷ 둘러싼 네모 넓이
    maxAspect: 3,      // 가로·세로 비가 이보다 길쭉하면 뺀다(형광등 반사선 등)
    maxCandidates: 10,
  });

  /** RGBA 한 픽셀의 밝기(0~255, BT.601). */
  function luma(r, g, b) {
    return 0.299 * r + 0.587 * g + 0.114 * b;
  }

  /** {data,width,height} RGBA 프레임 → 밝기 배열. */
  function lumaPlane(frame) {
    const n = frame.width * frame.height;
    const out = new Float32Array(n);
    const d = frame.data;
    for (let i = 0; i < n; i++) out[i] = luma(d[i * 4], d[i * 4 + 1], d[i * 4 + 2]);
    return out;
  }

  /**
   * 플래시 켠 프레임과 끈 프레임을 비교해 렌즈 반사 후보를 찾는다.
   * @returns {Array<{x,y,w,h,area,score,cx,cy}>} 점수(평균 밝기 차이 × 최대 밝기) 높은 순
   */
  function findCandidates(onFrame, offFrame, options) {
    const o = Object.assign({}, DEFAULTS, options || {});
    if (!onFrame || !offFrame || onFrame.width !== offFrame.width || onFrame.height !== offFrame.height) {
      throw new Error('두 프레임의 크기가 같아야 합니다');
    }
    const w = onFrame.width;
    const h = onFrame.height;
    const on = lumaPlane(onFrame);
    const off = lumaPlane(offFrame);
    const mask = new Uint8Array(w * h);
    for (let i = 0; i < w * h; i++) {
      if (on[i] >= o.onMin && on[i] - off[i] >= o.diffMin) mask[i] = 1;
    }
    const maxArea = Math.max(o.minArea, Math.floor(w * h * o.maxAreaRatio));
    const seen = new Uint8Array(w * h);
    const stack = [];
    const found = [];
    for (let start = 0; start < w * h; start++) {
      if (!mask[start] || seen[start]) continue;
      // 4방향으로 이어진 점 하나를 모은다
      let area = 0, sumDiff = 0, peak = 0, minX = w, minY = h, maxX = 0, maxY = 0, sx = 0, sy = 0;
      stack.push(start);
      seen[start] = 1;
      while (stack.length) {
        const p = stack.pop();
        const x = p % w;
        const y = (p - x) / w;
        area++;
        sumDiff += on[p] - off[p];
        if (on[p] > peak) peak = on[p];
        sx += x; sy += y;
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
        const nb = [x > 0 ? p - 1 : -1, x < w - 1 ? p + 1 : -1, y > 0 ? p - w : -1, y < h - 1 ? p + w : -1];
        for (const q of nb) {
          if (q >= 0 && mask[q] && !seen[q]) { seen[q] = 1; stack.push(q); }
        }
      }
      const bw = maxX - minX + 1;
      const bh = maxY - minY + 1;
      const fill = area / (bw * bh);
      const aspect = Math.max(bw, bh) / Math.min(bw, bh);
      if (area < o.minArea || area > maxArea || fill < o.minFill || aspect > o.maxAspect) continue;
      found.push({
        x: minX, y: minY, w: bw, h: bh, area,
        cx: sx / area, cy: sy / area,
        score: Math.round((sumDiff / area) * peak) / 255,
      });
    }
    found.sort((a, b) => b.score - a.score);
    return found.slice(0, o.maxCandidates);
  }

  /**
   * 여러 번 깜빡여 얻은 후보 목록을 합친다. 같은 자리(반경 radius 픽셀)에서 minHits번 이상 나온 점만 남긴다.
   * 한 번만 번쩍인 점(움직임, 우연한 반사)을 걸러 잘못 알리는 비율을 낮춘다.
   */
  function consistentCandidates(rounds, radius, minHits) {
    const r = radius == null ? 6 : radius;
    const need = minHits == null ? 2 : minHits;
    const groups = [];
    (rounds || []).forEach((list, roundIndex) => {
      (list || []).forEach((c) => {
        const g = groups.find((it) => Math.hypot(it.cx - c.cx, it.cy - c.cy) <= r && !it.rounds.has(roundIndex));
        if (g) {
          g.rounds.add(roundIndex);
          g.cx = (g.cx * g.hits + c.cx) / (g.hits + 1);
          g.cy = (g.cy * g.hits + c.cy) / (g.hits + 1);
          g.hits++;
          g.best = c.score > g.best.score ? c : g.best;
        } else {
          groups.push({ cx: c.cx, cy: c.cy, hits: 1, rounds: new Set([roundIndex]), best: c });
        }
      });
    });
    return groups
      .filter((g) => g.hits >= need)
      .map((g) => Object.assign({}, g.best, { cx: g.cx, cy: g.cy, hits: g.hits }))
      .sort((a, b) => b.hits - a.hits || b.score - a.score);
  }

  /** 감독관 화면에 띄울 문장. 기계는 판정하지 않는다. */
  function verdict(candidates) {
    const n = (candidates || []).length;
    if (n === 0) return { level: 'NONE', title: '의심 없음', detail: '플래시에 반응하는 렌즈 반사 점을 찾지 못했습니다.' };
    return {
      level: 'REVIEW',
      title: '확인 필요',
      detail: '렌즈 반사 후보 ' + n + '곳. 표시된 자리를 감독관이 직접 확인해 주세요. 부정행위 판정이 아닙니다.',
    };
  }

  // ---------- 카메라 고르기 ----------

  /** 스트림의 영상 트랙이 플래시(torch)를 조절할 수 있는가. */
  function hasTorch(stream) {
    const track = stream && stream.getVideoTracks ? stream.getVideoTracks()[0] : null;
    if (!track || !track.getCapabilities) return false;
    const caps = track.getCapabilities() || {};
    return !!caps.torch;
  }

  /**
   * 플래시를 시도할 카메라 순서. 지금 열린 카메라는 빼고, 이름에 뒷면(back·rear·후면)이 있는 것을 앞에 둔다.
   * 뒷면 카메라가 여러 개인 휴대폰(광각·초광각·망원)은 플래시를 조절할 수 있는 카메라가 하나뿐인 경우가 많다.
   */
  function orderCameras(devices, currentId) {
    const back = /back|rear|environment|후면|뒷면/i;
    const videos = (devices || []).filter((d) => d.kind === 'videoinput' && d.deviceId && d.deviceId !== currentId);
    return videos.filter((d) => back.test(d.label || '')).concat(videos.filter((d) => !back.test(d.label || '')));
  }

  function stopStream(stream) {
    if (stream && stream.getTracks) stream.getTracks().forEach((t) => t.stop());
  }

  /**
   * 플래시를 조절할 수 있는 카메라를 찾아 연다.
   * 1) 기본 뒷면 카메라 → 2) 잠시 뒤 다시 확인(능력값이 늦게 채워지는 기종) → 3) 다른 카메라를 하나씩 열어 본다.
   * 못 찾으면 { stream: 기본 카메라(미리보기용), torch: false }.
   */
  async function openTorchCamera(mediaDevices, opts) {
    const o = opts || {};
    const wait = o.wait || sleep;
    const size = { width: { ideal: 1280 }, height: { ideal: 720 } };
    const first = await mediaDevices.getUserMedia({ video: Object.assign({ facingMode: { ideal: 'environment' } }, size), audio: false });
    if (hasTorch(first)) return { stream: first, torch: true };
    await wait(500);
    if (hasTorch(first)) return { stream: first, torch: true };
    if (!mediaDevices.enumerateDevices) return { stream: first, torch: false };
    const track = first.getVideoTracks()[0];
    const currentId = track && track.getSettings ? (track.getSettings() || {}).deviceId : undefined;
    const others = orderCameras(await mediaDevices.enumerateDevices(), currentId);
    if (others.length === 0) return { stream: first, torch: false };
    stopStream(first); // 카메라 두 개를 동시에 못 여는 휴대폰이 많다
    for (const d of others) {
      let s = null;
      try {
        s = await mediaDevices.getUserMedia({ video: Object.assign({ deviceId: { exact: d.deviceId } }, size), audio: false });
      } catch (e) {
        continue;
      }
      if (hasTorch(s)) return { stream: s, torch: true };
      stopStream(s);
    }
    const back = await mediaDevices.getUserMedia({ video: Object.assign({ facingMode: { ideal: 'environment' } }, size), audio: false });
    return { stream: back, torch: false };
  }

  // ---------- 브라우저 화면 연결 ----------

  function sleep(ms) {
    return new Promise((r) => setTimeout(r, ms));
  }

  function mountPage(doc) {
    const $ = (id) => doc.getElementById(id);
    const video = $('lensVideo');
    const overlay = $('lensOverlay');
    const work = doc.createElement('canvas');
    const status = $('lensStatus');
    const result = $('lensResult');
    const startBtn = $('lensStart');
    const scanBtn = $('lensScan');
    let track = null;

    function say(text) { status.textContent = text; }

    async function start() {
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        say('이 브라우저는 카메라를 쓸 수 없습니다.');
        return;
      }
      try {
        say('플래시를 쓸 수 있는 카메라를 찾는 중입니다…');
        const opened = await openTorchCamera(navigator.mediaDevices);
        const stream = opened.stream;
        video.srcObject = stream;
        await video.play();
        track = stream.getVideoTracks()[0];
        if (!opened.torch && !hasTorch(stream)) {
          say('카메라는 켜졌지만 이 휴대폰에서는 웹 화면이 플래시를 켤 수 없습니다. 아이폰 사파리는 지원하지 않고, 일부 안드로이드 기종도 막혀 있습니다. 휴대폰 기종과 크롬 버전을 알려 주세요.');
          scanBtn.disabled = true;
          return;
        }
        scanBtn.disabled = false;
        say('준비됐습니다. 30~100㎝ 거리에서 물건을 비추고 [렌즈 찾기]를 누르세요.');
      } catch (e) {
        say('카메라를 열지 못했습니다: ' + (e && e.message ? e.message : e));
      }
    }

    function grab() {
      const w = 320;
      const h = Math.round((video.videoHeight / video.videoWidth) * w) || 180;
      work.width = w; work.height = h;
      const ctx = work.getContext('2d', { willReadFrequently: true });
      ctx.drawImage(video, 0, 0, w, h);
      return ctx.getImageData(0, 0, w, h);
    }

    async function torch(on) {
      await track.applyConstraints({ advanced: [{ torch: on }] });
      await sleep(220); // 노출이 따라오는 시간
    }

    function draw(cands, fw, fh) {
      overlay.width = video.clientWidth;
      overlay.height = video.clientHeight;
      const ctx = overlay.getContext('2d');
      ctx.clearRect(0, 0, overlay.width, overlay.height);
      const sx = overlay.width / fw;
      const sy = overlay.height / fh;
      ctx.lineWidth = 3;
      ctx.strokeStyle = '#f6ad55';
      cands.forEach((c) => {
        const r = Math.max(10, Math.max(c.w * sx, c.h * sy));
        ctx.beginPath();
        ctx.arc(c.cx * sx, c.cy * sy, r, 0, Math.PI * 2);
        ctx.stroke();
      });
    }

    async function scan() {
      scanBtn.disabled = true;
      const t0 = performance.now();
      try {
        const rounds = [];
        let size = null;
        for (let i = 0; i < 3; i++) {
          await torch(false);
          const off = grab();
          await torch(true);
          const on = grab();
          size = { w: on.width, h: on.height };
          rounds.push(findCandidates(on, off));
        }
        await torch(false);
        const cands = consistentCandidates(rounds, 6, 2);
        draw(cands, size.w, size.h);
        const v = verdict(cands);
        result.dataset.level = v.level;
        result.querySelector('strong').textContent = v.title;
        result.querySelector('span').textContent = v.detail + ' (' + ((performance.now() - t0) / 1000).toFixed(1) + '초)';
      } catch (e) {
        say('검사 중 문제가 생겼습니다: ' + (e && e.message ? e.message : e));
      } finally {
        scanBtn.disabled = false;
      }
    }

    startBtn.addEventListener('click', start);
    scanBtn.addEventListener('click', scan);
  }

  return { DEFAULTS, luma, lumaPlane, findCandidates, consistentCandidates, verdict, hasTorch, orderCameras, openTorchCamera, mountPage };
});
