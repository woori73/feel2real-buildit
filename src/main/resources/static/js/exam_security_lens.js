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
      let area = 0, sumDiff = 0, sumOff = 0, peak = 0, minX = w, minY = h, maxX = 0, maxY = 0, sx = 0, sy = 0;
      stack.push(start);
      seen[start] = 1;
      while (stack.length) {
        const p = stack.pop();
        const x = p % w;
        const y = (p - x) / w;
        area++;
        sumDiff += on[p] - off[p];
        sumOff += off[p];
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
      const offMean = sumOff / area;
      // 렌즈는 플래시를 끄면 어둡다. 꺼도 밝은 점은 케이블·플라스틱·금속 같은 반사면으로 본다(2단계 최종 기준에서만 씀)
      if (o.maxOffMean != null && offMean > o.maxOffMean) continue;
      found.push({
        x: minX, y: minY, w: bw, h: bh, area, aspect, fill,
        cx: sx / area, cy: sy / area,
        diff: sumDiff / area, // 켰을 때 - 껐을 때 평균 밝기 차이
        offMean, // 플래시를 껐을 때 평균 밝기
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

  /**
   * 가까이 붙은 후보를 하나로 합친다. 렌즈 하나의 반사가 작은 점 여러 개로 쪼개지거나
   * 손이 떨려 자리가 조금씩 달라지면 같은 렌즈를 여러 곳으로 세게 된다.
   * 반경 radius(프레임 픽셀) 안의 후보는 한 자리로 보고, 가장 많이·밝게 잡힌 것을 대표로 남긴다.
   */
  function mergeNearby(candidates, radius) {
    const r = radius == null ? 16 : radius;
    const sorted = (candidates || []).slice().sort((a, b) => (b.hits || 0) - (a.hits || 0) || b.score - a.score);
    const merged = [];
    sorted.forEach((c) => {
      const near = merged.find((m) => Math.hypot(m.cx - c.cx, m.cy - c.cy) <= r);
      if (near) near.members++;
      else merged.push(Object.assign({}, c, { members: 1 }));
    });
    return merged;
  }

  /**
   * 프레임(srcW×srcH)을 화면 칸(boxW×boxH)에 잘림 없이 넣을 때의 배율과 여백(object-fit: contain).
   * 프레임 좌표 (x, y) → 화면 좌표 (ox + x·scale, oy + y·scale).
   */
  function fitRect(srcW, srcH, boxW, boxH) {
    const scale = Math.min(boxW / srcW, boxH / srcH);
    return { scale, ox: (boxW - srcW * scale) / 2, oy: (boxH - srcH * scale) / 2 };
  }

  /** 분석할 프레임 폭(픽셀). 320이면 2m 밖의 렌즈가 1픽셀보다 작아져 놓치기 쉬워 640으로 올렸다. */
  const ANALYSIS_WIDTH = 640;

  /**
   * 검사 거리별 설정. 거리 값은 시작값이고 1주차 측정으로 다시 정한다.
   * - near(입구·순회 1~2m): 확대 없음, 기본 기준값
   * - far(교실 앞 3~5m): 카메라가 지원하면 3배까지 확대하고, 멀리서 약해지는 반사를 잡도록 기준값을 낮춘다
   *   (기준을 낮추면 잘못 알리는 경우도 늘 수 있다)
   * @param {'near'|'far'} mode
   * @param {object} caps track.getCapabilities() 결과(zoom: {min,max})
   * @returns {{zoom: number|null, options: object, radius: number, mergeRadius: number}}
   */
  function rangeSettings(mode, caps) {
    const k = ANALYSIS_WIDTH / 320; // 반경 기준값은 320폭에서 정했으므로 폭에 맞춰 늘린다
    const zoomCaps = caps && caps.zoom && typeof caps.zoom.max === 'number' ? caps.zoom : null;
    // coarse: 1단계(전체를 거칠게) — 놓치지 않게 기준을 낮춘다. options: 2단계(후보만 정밀하게) — 최종 기준.
    if (mode === 'far') {
      return {
        zoom: zoomCaps ? Math.min(zoomCaps.max, Math.max(zoomCaps.min || 1, 3)) : null,
        coarse: { onMin: 80, diffMin: 25, maxCandidates: 30 },
        options: { onMin: 170, diffMin: 40, maxOffMean: 150 },
        radius: 10 * k,
        mergeRadius: 16 * k,
      };
    }
    return {
      zoom: zoomCaps ? (zoomCaps.min || 1) : null,
      coarse: { onMin: 100, diffMin: 40, maxCandidates: 30 },
      options: { maxOffMean: 150 },
      radius: 10 * k,
      mergeRadius: 16 * k,
    };
  }

  // ---------- 다이내믹 덴서티(2단계 정밀 확인) ----------
  // F2R 다이내믹 덴서티 D = I · dΨ/dt ÷ (V + ε) 를 렌즈 후보에 그대로 옮긴다(docs/f2r-llm/DYNAMIC_DENSITY.md 4장).
  // 화면 전체는 거칠게 보고, D가 높은 후보 자리에만 카메라 원본 해상도(정밀 분석)를 몰아준다.

  /** 2단계에서 정밀 확인할 후보 수(계산량 상한). */
  const FINE_BUDGET = 5;
  /** 모양 벌점 무게(1단계 화면 넓이 비율과 같은 단위). 시작값 — 1주차 측정으로 다시 정한다. */
  const SHAPE_PENALTY = 5e-4;
  /** 눈·안경 짝으로 볼 두 점 사이 가로 거리(화면 폭 비율). 휴대폰 렌즈 두 개(1~1.5㎝)보다 넓게 잡는다. */
  const EYE_PAIR_MIN = 0.03, EYE_PAIR_MAX = 0.25;

  /**
   * 렌즈 후보의 동태밀도 D.
   * - I(정보량): 켰을 때 - 껐을 때 평균 밝기 차이(0~1)
   * - dΨ/dt(변화율): 끔·켬 반복 중 같은 자리에서 다시 나타난 비율(0~1)
   * - V(점유 공간): 후보 넓이 ÷ 화면 넓이 — 넓게 번쩍이는 면(노이즈)일수록 크다
   */
  function lensDensity(c, roundsTotal, frameArea, eps) {
    const e = eps == null ? 1e-3 : eps;
    // I: 켜면 강하게 빛나고(밝기 차이) 끄면 어두운(렌즈 구멍) 점일수록 크다. 꺼도 밝은 케이블·금속은 작아진다
    const darkness = 1 - Math.min(1, Math.max(0, (c.offMean || 0) / 255));
    const I = Math.min(1, Math.max(0, (c.diff || 0) / 255)) * darkness;
    const dPsi = Math.min(1, (c.hits || 1) / Math.max(1, roundsTotal));
    // V: 넓이 + 모양 벌점(길쭉함·속 빔·주변에 비슷한 점이 많음 = 철망·촘촘한 무늬). 렌즈는 작고 둥글고 홀로 있다
    const V = (c.area || 1) / Math.max(1, frameArea)
      + SHAPE_PENALTY * (Math.max(1, c.aspect || 1) - 1)
      + SHAPE_PENALTY * (1 - Math.min(1, c.fill == null ? 1 : c.fill))
      + SHAPE_PENALTY * (Math.max(1, c.members || 1) - 1);
    return (I * dPsi) / (V + e);
  }

  /**
   * 사람 눈·안경 반사로 보이는 짝을 찾는다(가로로 나란한, 밝기가 비슷한 두 점).
   * 얼굴 위치를 알 수 없어 '짝 모양'으로만 추정한다. 표시만 따로 하고(분홍) 렌즈 후보 순위에서는 뺀다.
   */
  function markEyePairs(candidates, frameW) {
    const list = (candidates || []).map((c) => Object.assign({}, c));
    const lo = EYE_PAIR_MIN * frameW, hi = EYE_PAIR_MAX * frameW;
    for (let i = 0; i < list.length; i++) {
      for (let j = i + 1; j < list.length; j++) {
        const a = list[i], b = list[j];
        const dx = Math.abs(a.cx - b.cx), dy = Math.abs(a.cy - b.cy);
        const ratio = (a.diff || 1) / (b.diff || 1);
        if (dx >= lo && dx <= hi && dy <= 0.2 * dx && ratio >= 0.5 && ratio <= 2) { a.eyePair = true; b.eyePair = true; }
      }
    }
    return { eyes: list.filter((c) => c.eyePair), others: list.filter((c) => !c.eyePair) };
  }

  /** 빨간 원(확인 필요)과 노란 점선(약한 후보)을 D 높은 순으로 한 줄에 세워 순위를 붙인다(시험 기록용). */
  function rankAll(confirmed, weak) {
    const all = (confirmed || []).map((c) => Object.assign({}, c, { level: 'REVIEW' }))
      .concat((weak || []).map((c) => Object.assign({}, c, { level: 'WEAK' })));
    all.sort((a, b) => (b.d || 0) - (a.d || 0));
    return all.map((c, i) => Object.assign(c, { rank: i + 1 }));
  }

  /** 화면에서 누른 자리(프레임 좌표)와 가장 가까운 후보. radius 밖이면 null(= 그 자리는 후보에 없었음). */
  function nearestRanked(ranked, x, y, radius) {
    let best = null, bestD = Infinity;
    (ranked || []).forEach((c) => { const d = Math.hypot(c.cx - x, c.cy - y); if (d < bestD) { bestD = d; best = c; } });
    return best && bestD <= radius ? best : null;
  }

  /** D가 높은 순으로 정밀 확인할 후보(picked)와 나머지(rest)를 나눈다. */
  function allocateFine(candidates, roundsTotal, frameArea, budget) {
    const scored = (candidates || []).map((c) => Object.assign({}, c, { d: lensDensity(c, roundsTotal, frameArea) }));
    scored.sort((a, b) => b.d - a.d);
    const n = budget == null ? FINE_BUDGET : budget;
    return { picked: scored.slice(0, n), rest: scored.slice(n) };
  }

  /**
   * 1단계 프레임(coarseW×coarseH)의 후보 자리를 카메라 원본(videoW×videoH)에서 잘라 볼 사각형.
   * half: 원본 픽셀 기준 반폭. 화면 밖으로 나가지 않게 맞춘다. s = 원본 ÷ 1단계 배율.
   */
  function roiRect(c, coarseW, coarseH, videoW, videoH, half) {
    const s = videoW / coarseW;
    const w = Math.min(videoW, half * 2);
    const h = Math.min(videoH, half * 2);
    const x = Math.round(Math.min(Math.max(c.cx * s - half, 0), videoW - w));
    const y = Math.round(Math.min(Math.max(c.cy * (videoH / coarseH) - half, 0), videoH - h));
    return { x, y, w, h, s };
  }

  /**
   * 2단계 기준값. "반사면으로 볼 넓이" 상한은 1단계 화면에서 정한 픽셀 수를 원본 배율(s²)로 키워
   * 잘라 본 사각형 넓이에 대한 비율로 바꾼다(잘라 본 칸이 작아도 같은 크기의 점을 같은 기준으로 본다).
   */
  function fineOptions(base, roi, coarseArea) {
    const ratio = (base && base.maxAreaRatio) || DEFAULTS.maxAreaRatio;
    const maxPixels = coarseArea * ratio * roi.s * roi.s;
    return Object.assign({}, base, { maxAreaRatio: Math.min(1, maxPixels / (roi.w * roi.h)) });
  }

  /**
   * 감독관 화면에 띄울 문장. 기계는 판정하지 않는다.
   * weakCount: 1단계에서만 보이고 2단계 정밀 확인에서 다시 안 보인 약한 후보 수(판정에는 넣지 않고 알려만 준다).
   */
  function verdict(candidates, weakCount) {
    const n = (candidates || []).length;
    const weak = weakCount ? ' 정밀 확인에서 다시 안 보인 약한 후보 ' + weakCount + '곳은 노란 점선으로 표시했습니다.' : '';
    if (n === 0) return { level: 'NONE', title: '의심 없음', detail: '플래시에 반응하는 렌즈 반사 점을 찾지 못했습니다.' + weak };
    return {
      level: 'REVIEW',
      title: '확인 필요',
      detail: '렌즈 반사 후보 ' + n + '곳. 표시된 자리를 감독관이 직접 확인해 주세요. 부정행위 판정이 아닙니다.' + weak,
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
    const size = { width: { ideal: 1920 }, height: { ideal: 1080 } }; // 멀리 있는 작은 렌즈도 픽셀로 남게 높은 해상도를 요청
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
    let last = null; // 마지막 검사의 순위표(시험 기록용, 메모리에만)

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
        if (video.videoWidth && video.videoHeight) {
          video.parentElement.style.aspectRatio = video.videoWidth + ' / ' + video.videoHeight; // 영상 전체가 보이게
        }
        track = stream.getVideoTracks()[0];
        if (!opened.torch && !hasTorch(stream)) {
          say('카메라는 켜졌지만 이 휴대폰에서는 웹 화면이 플래시를 켤 수 없습니다. 아이폰 사파리는 지원하지 않고, 일부 안드로이드 기종도 막혀 있습니다. 휴대폰 기종과 크롬 버전을 알려 주세요.');
          scanBtn.disabled = true;
          return;
        }
        scanBtn.disabled = false;
        say('준비됐습니다. 거리를 고르고 [렌즈 찾기]를 누르세요. 입구·순회는 1~2m, 교실 앞은 [멀리]를 고르세요(거리별 성능은 아직 재는 중).');
      } catch (e) {
        say('카메라를 열지 못했습니다: ' + (e && e.message ? e.message : e));
      }
    }

    function grab() {
      const w = ANALYSIS_WIDTH;
      const h = Math.round((video.videoHeight / video.videoWidth) * w) || 180;
      work.width = w; work.height = h;
      const ctx = work.getContext('2d', { willReadFrequently: true });
      ctx.drawImage(video, 0, 0, w, h);
      return ctx.getImageData(0, 0, w, h);
    }

    const fineCanvas = doc.createElement('canvas');
    /** 카메라 원본 해상도에서 사각형 하나만 잘라 온다(2단계 정밀 확인). 메모리에서만 비교하고 버린다. */
    function grabRoi(r) {
      fineCanvas.width = r.w; fineCanvas.height = r.h;
      const ctx = fineCanvas.getContext('2d', { willReadFrequently: true });
      ctx.drawImage(video, r.x, r.y, r.w, r.h, 0, 0, r.w, r.h);
      return ctx.getImageData(0, 0, r.w, r.h);
    }

    async function torch(on) {
      await track.applyConstraints({ advanced: [{ torch: on }] });
      await sleep(220); // 노출이 따라오는 시간
    }

    function clearOverlay() {
      const ctx = overlay.getContext('2d');
      ctx.clearRect(0, 0, overlay.width, overlay.height);
    }

    /** 후보 자리에 번호 붙은 큰 원과 십자선을 그린다(영상은 object-fit: contain이라 잘린 곳이 없다). */
    function draw(cands, fw, fh, weak, eyes) {
      overlay.width = overlay.clientWidth;
      overlay.height = overlay.clientHeight;
      const ctx = overlay.getContext('2d');
      ctx.clearRect(0, 0, overlay.width, overlay.height);
      const f = fitRect(fw, fh, overlay.width, overlay.height);
      (eyes || []).forEach((c) => { // 눈·안경 반사 추정: 분홍 점선
        ctx.setLineDash([3, 4]); ctx.lineWidth = 3; ctx.strokeStyle = '#d53f8c';
        ctx.beginPath(); ctx.arc(f.ox + c.cx * f.scale, f.oy + c.cy * f.scale, 14, 0, Math.PI * 2); ctx.stroke();
        ctx.setLineDash([]);
      });
      (weak || []).forEach((c) => { // 약한 후보: 노란 점선 + 작은 순위 숫자
        const x = f.ox + c.cx * f.scale, y = f.oy + c.cy * f.scale;
        ctx.setLineDash([6, 5]); ctx.lineWidth = 3; ctx.strokeStyle = '#ecc94b';
        ctx.beginPath(); ctx.arc(x, y, 16, 0, Math.PI * 2); ctx.stroke();
        ctx.setLineDash([]);
        if (c.rank) {
          ctx.font = 'bold 12px system-ui, sans-serif'; ctx.fillStyle = '#975a16';
          ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
          ctx.fillText(String(c.rank), x + 18, y - 16);
        }
      });
      cands.forEach((c, i) => {
        const x = f.ox + c.cx * f.scale;
        const y = f.oy + c.cy * f.scale;
        const r = Math.max(22, Math.max(c.w, c.h) * f.scale * 2);
        ctx.lineWidth = 7; ctx.strokeStyle = '#fff';
        ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2); ctx.stroke();
        ctx.lineWidth = 4; ctx.strokeStyle = '#e53e3e';
        ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2); ctx.stroke();
        ctx.beginPath();
        ctx.moveTo(x - r - 10, y); ctx.lineTo(x - r + 8, y);
        ctx.moveTo(x + r - 8, y); ctx.lineTo(x + r + 10, y);
        ctx.moveTo(x, y - r - 10); ctx.lineTo(x, y - r + 8);
        ctx.moveTo(x, y + r - 8); ctx.lineTo(x, y + r + 10);
        ctx.stroke();
        const label = String(c.rank || i + 1);
        ctx.font = 'bold 18px system-ui, sans-serif';
        ctx.fillStyle = '#e53e3e';
        ctx.beginPath(); ctx.arc(x + r * 0.75, y - r * 0.75, 13, 0, Math.PI * 2); ctx.fill();
        ctx.fillStyle = '#fff';
        ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
        ctx.fillText(label, x + r * 0.75, y - r * 0.75 + 1);
      });
    }

    async function scan() {
      scanBtn.disabled = true;
      clearOverlay();
      if (video.paused) await video.play(); // 지난 결과로 멈춰 둔 화면을 다시 움직인다
      const t0 = performance.now();
      try {
        const modeInput = doc.querySelector('input[name="lensRange"]:checked');
        const set = rangeSettings(modeInput ? modeInput.value : 'near', track.getCapabilities ? track.getCapabilities() : {});
        // 효과 비교 측정용(TEST_PLAN 4-1): 주소 끝에 ?dd=off를 붙이면 2단계 없이 예전 방식(한 번에 최종 기준)으로 찾는다
        const ddOff = /[?&]dd=off\b/.test((doc.defaultView && doc.defaultView.location.search) || '');
        if (set.zoom != null) {
          try { await track.applyConstraints({ advanced: [{ zoom: set.zoom }] }); await sleep(300); } catch (e) { /* 확대를 못 하는 기종 */ }
        }
        const rounds = [];
        let size = null;
        for (let i = 0; i < 3; i++) {
          await torch(false);
          const off = grab();
          await torch(true);
          const on = grab();
          size = { w: on.width, h: on.height };
          rounds.push(findCandidates(on, off, ddOff ? set.options : set.coarse));
        }
        await torch(false);
        // 1단계: 화면 전체를 거칠게(기준을 낮춰 놓치지 않게) 본 후보
        const merged = mergeNearby(consistentCandidates(rounds, set.radius, 2), set.mergeRadius);
        // 눈·안경 반사로 보이는 짝은 따로 표시하고 렌즈 후보 순위에서 뺀다
        const split = ddOff ? { eyes: [], others: merged } : markEyePairs(merged, size.w);
        const coarse = split.others;
        // 2단계: D가 높은 후보 자리만 카메라 원본 해상도로 다시 끔·켬 2번 비교(최종 기준)
        const frameArea = size.w * size.h;
        const alloc = ddOff ? { picked: [], rest: [] } : allocateFine(coarse, rounds.length, frameArea, FINE_BUDGET);
        const cands = ddOff ? coarse : [];
        let weak = alloc.rest;
        if (alloc.picked.length) {
          say('후보 ' + alloc.picked.length + '곳을 정밀 확인하는 중입니다…');
          const vw = video.videoWidth || size.w;
          const vh = video.videoHeight || size.h;
          const rois = alloc.picked.map((c) => roiRect(c, size.w, size.h, vw, vh, Math.round(32 * vw / size.w)));
          const per = rois.map(() => []);
          for (let i = 0; i < 2; i++) {
            await torch(false);
            const offs = rois.map(grabRoi);
            await torch(true);
            const ons = rois.map(grabRoi);
            rois.forEach((r, j) => per[j].push(findCandidates(ons[j], offs[j], fineOptions(set.options, r, frameArea))));
          }
          await torch(false);
          const fineRadius = 10 * vw / 320;
          alloc.picked.forEach((c, j) => {
            const got = consistentCandidates(per[j], fineRadius, 2);
            if (!got.length) { weak = weak.concat([c]); return; }
            const r = rois[j];
            const g = got[0];
            cands.push(Object.assign({}, c, {
              cx: (r.x + g.cx) / r.s, cy: (r.y + g.cy) / r.s,
              w: Math.max(1, g.w / r.s), h: Math.max(1, g.h / r.s),
            }));
          });
        }
        cands.sort((a, b) => (b.d || 0) - (a.d || 0));
        last = { ranked: rankAll(cands, weak), fw: size.w, fh: size.h, mode: modeInput ? modeInput.value : 'near', ddOff };
        const byRank = new Map(last.ranked.map((c) => [c.cx + ',' + c.cy, c.rank]));
        cands.forEach((c) => { c.rank = byRank.get(c.cx + ',' + c.cy); });
        weak.forEach((c) => { c.rank = byRank.get(c.cx + ',' + c.cy); });
        draw(cands, size.w, size.h, weak, split.eyes);
        if (markBtn) markBtn.disabled = last.ranked.length === 0 && cands.length === 0;
        if (cands.length) {
          video.pause(); // 휴대폰을 움직여도 원 자리를 볼 수 있게 화면을 멈춘다
          say('화면을 멈췄습니다. 빨간 원(번호) 자리를 직접 확인하세요. 다시 찾으려면 [렌즈 찾기]를 누르세요.');
        }
        const v = verdict(cands, weak.length);
        const eyeNote = split.eyes.length ? ' 눈·안경 반사로 보이는 ' + split.eyes.length + '곳은 분홍 점선(렌즈 후보에서 제외).' : '';
        result.dataset.level = v.level;
        result.querySelector('strong').textContent = v.title;
        result.querySelector('span').textContent = v.detail + eyeNote + ' (' + ((performance.now() - t0) / 1000).toFixed(1) + '초)';
      } catch (e) {
        say('검사 중 문제가 생겼습니다: ' + (e && e.message ? e.message : e));
      } finally {
        scanBtn.disabled = false;
      }
    }

    // ---------- 시험 기록: 실제 렌즈가 몇 순위였나(TEST_PLAN 4-1) ----------
    // 사진·좌표는 남기지 않고 순위 문장만 이 화면에 적는다. 새로고침하면 사라진다(저장·전송 없음).
    const markBtn = $('lensMark');
    const copyBtn = $('lensCopy');
    const logList = $('lensLog');
    const lines = [];
    let marking = false;

    function onMark() {
      if (!last) return;
      marking = true;
      say('화면에서 실제 렌즈가 있는 자리를 한 번 누르세요.');
    }

    function onOverlayTap(ev) {
      if (!marking || !last) return;
      marking = false;
      const rect = overlay.getBoundingClientRect();
      const f = fitRect(last.fw, last.fh, rect.width, rect.height);
      const fx = (ev.clientX - rect.left - f.ox) / f.scale;
      const fy = (ev.clientY - rect.top - f.oy) / f.scale;
      const hit = nearestRanked(last.ranked, fx, fy, 40 / f.scale);
      const when = new Date().toLocaleString('ko-KR', { hour12: false });
      const mode = (last.mode === 'far' ? '멀리' : '가까이') + (last.ddOff ? '·예전 방식' : '');
      const what = hit
        ? '실제 렌즈 = 후보 ' + last.ranked.length + '곳 중 ' + hit.rank + '순위(' + (hit.level === 'REVIEW' ? '빨간 원' : '노란 점선') + ')'
        : '실제 렌즈 = 후보에 없음(못 찾음)';
      lines.push(when + ' · ' + mode + ' · ' + what);
      const li = doc.createElement('li');
      li.textContent = lines[lines.length - 1];
      logList.appendChild(li);
      copyBtn.disabled = false;
      say('기록했습니다: ' + what);
    }

    async function onCopy() {
      const text = lines.join('\n');
      try {
        await navigator.clipboard.writeText(text);
        say('기록 ' + lines.length + '줄을 복사했습니다.');
      } catch (e) {
        say('복사하지 못했습니다. 기록 목록을 길게 눌러 직접 복사해 주세요.');
      }
    }

    startBtn.addEventListener('click', start);
    scanBtn.addEventListener('click', scan);
    if (markBtn) markBtn.addEventListener('click', onMark);
    if (copyBtn) copyBtn.addEventListener('click', onCopy);
    overlay.addEventListener('click', onOverlayTap);
  }

  return { DEFAULTS, luma, lumaPlane, findCandidates, consistentCandidates, mergeNearby, fitRect, rangeSettings, lensDensity, markEyePairs, rankAll, nearestRanked, allocateFine, roiRect, fineOptions, FINE_BUDGET, verdict, hasTorch, orderCameras, openTorchCamera, mountPage };
});
