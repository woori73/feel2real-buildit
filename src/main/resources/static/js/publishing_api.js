/* publishing_api.js — DesignCraft 출판 API 클라이언트 (Qoder, 명세 4장 계약)
 * USE_MOCK = true  → publishing_mock.js 가짜 응답
 * USE_MOCK = false → /api/publishing/* 실제 호출 (백엔드 구현 시, Muse/Claude M1/M5)
 * 화면 코드(publishing_editor.js)는 이 모듈만 사용한다. */
(function (global) {
  'use strict';

  var USE_MOCK = false; // ← 백엔드 통합 시 false로 전환 (명세 4.6)
  var BASE = '/api/publishing';

  function mockCall(fnName, args) {
    // mock은 동기/비동기 혼용 가능 → 항상 Promise로 정규화
    return new Promise(function (resolve, reject) {
      setTimeout(function () {
        try {
          var f = global.PublishingMock[fnName];
          if (!f) { reject(new Error('MOCK_MISSING: ' + fnName)); return; }
          resolve(f.apply(null, args));
        } catch (e) { reject(e); }
      }, 120 + Math.random() * 220);
    });
  }

  function http(method, path, body, isBinary) {
    var opt = { method: method, credentials: 'same-origin', headers: {} };
    if (body !== undefined) {
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    return fetch(BASE + path, opt).then(function (res) {
      if (!res.ok) return httpError(res, method, path);
      if (isBinary) return res.blob();
      return res.text().then(function (t) { return t ? JSON.parse(t) : {}; });
    });
  }

  /* 서버가 준 사유(message / reason)를 에러에 담아 화면이 그대로 보여줄 수 있게 한다. */
  function httpError(res, method, path) {
    return res.text().then(function (t) {
      var msg = '';
      try { var b = JSON.parse(t); msg = b.message || b.reason || b.error || ''; } catch (e) { msg = t || ''; }
      var err = new Error(msg || statusMessage(res.status) || ('HTTP ' + res.status + ' ' + method + ' ' + path));
      err.status = res.status;
      throw err;
    });
  }

  /* 서버가 사유를 주지 않을 때(기본 설정은 오류 본문을 숨긴다) 상태 코드로 안내 문구를 만든다. */
  function statusMessage(status) {
    return {
      401: '로그인이 필요합니다.',
      403: '권한이 없습니다.',
      404: '대상을 찾을 수 없습니다.',
      409: '작업이 아직 준비되지 않았습니다.',
      413: '파일이 너무 큽니다.',
      422: '검수 기준을 통과하지 못해 처리가 차단되었습니다. 검수 결과를 확인하세요.',
      502: '조판 엔진 실행에 실패했습니다.',
      503: '조판 엔진을 사용할 수 없습니다. 관리자에게 문의하세요.'
    }[status] || '';
  }

  function imageUrl(res) {
    if (!res.ok) return httpError(res, 'GET', res.url);
    return res.blob().then(function (b) { return URL.createObjectURL(b); });
  }

  /* 판형 크기 문자열("210×297mm")을 [가로, 세로] mm 배열로 바꾼다. 알 수 없으면 A4. */
  function parseMm(size) {
    var m = /(\d+(?:\.\d+)?)\s*[×x]\s*(\d+(?:\.\d+)?)/.exec(size || '');
    return m ? [Number(m[1]), Number(m[2])] : [210, 297];
  }

  /* 서버가 id만 주는 스타일의 표시 이름. 모르는 id는 id를 그대로 보여준다. */
  var STYLE_LABELS = {
    'ko-body': '본문', 'ko-heading1': '대제목', 'ko-heading2': '중제목', 'ko-heading3': '소제목',
    'ko-passage': '지문', 'ko-choice': '선택지', 'ko-footnote': '각주', 'ko-quote': '인용',
    'ko-caption': '캡션', 'ko-qnum': '문항 번호', 'ko-explain': '해설', 'ko-vocab': '어휘',
    'ko-time': '시간 배정', 'ko-char-emph': '강조', 'ko-char-strong': '굵게',
    'ko-char-code': '코드', 'ko-char-caption': '캡션 글자', 'ko-char-note': '주석 글자'
  };
  function toStyleList(ids) {
    return (ids || []).map(function (id) { return { id: id, label: STYLE_LABELS[id] || id }; });
  }

  var PublishingApi = {
    isMock: function () { return USE_MOCK; },
    constants: function () {
      /* 판형·스타일 목록: 백엔드 GET /templates · /styles (PublishingMetaController). */
      if (USE_MOCK) return Promise.resolve(global.PublishingMock.meta);
      return Promise.all([http('GET', '/templates'), http('GET', '/styles')]).then(function (r) {
        return {
          TEMPLATES: (r[0].templates || []).map(function (t) {
            return { id: t.code, name: t.name, size: t.size, mm: parseMm(t.size) };
          }),
          PARAGRAPH_STYLES: toStyleList(r[1].paragraph),
          CHAR_STYLES: toStyleList(r[1].character)
        };
      });
    },

    /* ── 4.1 작업(Job) API ─────────────────────────────────── */
    listJobs: function () {
      if (USE_MOCK) return mockCall('listJobs', []);
      /* 백엔드 목록 엔드포인트는 별도 이슈로 분리 — 일단 빈 목록 반환 */
      return http('GET', '/jobs').then(function (r) { return r.jobs; })
        .catch(function () { return []; });
    },
    createJob: function (template, title, manuscriptFile) {
      if (USE_MOCK) return mockCall('createJob', [template, title, manuscriptFile ? manuscriptFile.name : null]);
      /* 원고는 작업 문서가 준비된 뒤 importManuscript로 반입한다(생성 시점엔 문서가 아직 없다). */
      return http('POST', '/jobs', { template: template, title: title });
    },
    getJob: function (jobId) {
      if (USE_MOCK) return mockCall('getJob', [jobId]);
      return http('GET', '/jobs/' + encodeURIComponent(jobId));
    },
    cancelJob: function (jobId) {
      if (USE_MOCK) return mockCall('cancelJob', [jobId]);
      return http('POST', '/jobs/' + encodeURIComponent(jobId) + '/cancel');
    },

    /* ── 4.2 미리보기·편집 API ─────────────────────────────── */
    getPagePng: function (jobId, n, scale) {
      if (USE_MOCK) return mockCall('getPagePng', [jobId, n, scale]);
      /* 실계약: GET .../pages/{n}/png?scale=1 → PNG 바이너리 */
      return fetch(BASE + '/jobs/' + encodeURIComponent(jobId) + '/pages/' + n +
        '/png?scale=' + (scale || 1), { credentials: 'same-origin' })
        .then(imageUrl);
    },
    getThumbPng: function (jobId, n) {
      if (USE_MOCK) return mockCall('getThumbPng', [jobId, n]);
      return fetch(BASE + '/jobs/' + encodeURIComponent(jobId) + '/pages/' + n + '/thumb',
        { credentials: 'same-origin' })
        .then(imageUrl);
    },
    applyCommands: function (jobId, commands, label) {
      if (USE_MOCK) return mockCall('applyCommands', [jobId, commands, label]);
      /* 실계약: POST .../commands { commands: [{id, params}] } → {applied, snapshotId}
         (label은 스냅샷 설명용 확장 필드 — 백엔드 협의 필요) */
      return http('POST', '/jobs/' + encodeURIComponent(jobId) + '/commands',
        { commands: commands, label: label || null });
    },

    /* ── F2 원고 반입 — 4장에 계약 미기재, 제안 경로로 구현 ─── */
    inspectManuscript: function (fileName, textSample, fileSize) {
      if (USE_MOCK) return mockCall('inspectManuscript', [fileName, textSample]);
      /* 서버는 업로드 전 검사 API가 없다 — 브라우저에서 구조를 세어 매핑표를 만든다(예상치). */
      return Promise.resolve(PublishingApi.estimateManuscript(fileName, textSample, fileSize));
    },
    /* 원고 구조 추정(순수 함수): md/txt는 제목·인용·본문 문단 수와 예상 쪽수, 이진(docx/rtf)은 크기 기반 추정. */
    estimateManuscript: function (fileName, text, fileSize) {
      var binary = !/\.(md|txt)$/i.test(fileName || '');
      if (binary) {
        var est = Math.max(1, Math.round((fileSize || 0) / 3000));
        return { binary: true, estPages: est, rows: [
          { source: '본문 (이진 원고)', count: est, target: 'ko-body' }
        ] };
      }
      var h = [0, 0, 0], quote = 0, body = 0, chars = 0;
      String(text || '').split(/\r?\n/).forEach(function (line) {
        var m = /^(#{1,3})\s+\S/.exec(line);
        if (m) { h[m[1].length - 1] += 1; return; }
        if (/^>\s*\S/.test(line)) { quote += 1; return; }
        if (line.trim()) { body += 1; chars += line.length; }
      });
      var rows = [];
      ['# 제목', '## 소제목', '### 세부제목'].forEach(function (label, i) {
        if (h[i]) rows.push({ source: label, count: h[i], target: 'ko-heading' + (i + 1) });
      });
      if (quote) rows.push({ source: '> 인용', count: quote, target: 'ko-quote' });
      if (body) rows.push({ source: '본문 문단', count: body, target: 'ko-body' });
      return { binary: false, rows: rows, estPages: Math.max(1, Math.round(chars / 1400)) };
    },
    /* 원고를 올려 문서에 반입한다(file.place autoflow, 서버가 반입 전 스냅샷 생성). */
    importManuscript: function (jobId, file) {
      if (USE_MOCK) return mockCall('importManuscript', [jobId, file.name, 0]);
      var fd = new FormData();
      fd.append('manuscript', file);
      return fetch(BASE + '/jobs/' + encodeURIComponent(jobId) + '/manuscript',
        { method: 'POST', credentials: 'same-origin', body: fd })
        .then(function (res) { return res.ok ? res.json() : httpError(res, 'POST', '/manuscript'); });
    },

    /* ── 4.3 AI 자연어 수정 (승인 플로우) ──────────────────── */
    aiPlan: function (jobId, message) {
      if (USE_MOCK) return mockCall('aiPlan', [jobId, message]);
      return http('POST', '/ai/plan', { jobId: jobId, message: message });
    },
    applyRevision: function (jobId, plan) {
      if (USE_MOCK) return mockCall('applyRevision', [jobId, plan]);
      return http('POST', '/jobs/' + encodeURIComponent(jobId) + '/revisions', { plan: plan });
    },

    /* ── 4.4 검수·산출물 ───────────────────────────────────── */
    runPreflight: function (jobId) {
      if (USE_MOCK) return mockCall('preflight', [jobId]);
      return http('POST', '/jobs/' + encodeURIComponent(jobId) + '/preflight');
    },
    setPreflightScenario: function (jobId, scenario) {
      if (USE_MOCK) return mockCall('setPreflightScenario', [jobId, scenario]);
      throw new Error('mock-only');
    },
    startExport: function (jobId, format, standard) {
      if (USE_MOCK) return mockCall('startExport', [jobId, format, standard]);
      return http('POST', '/jobs/' + encodeURIComponent(jobId) + '/export', { format: format, standard: standard });
    },
    getExportState: function (jobId) {
      if (USE_MOCK) return mockCall('getExportState', [jobId]);
      /* 서버 export는 동기 응답이라 진행 상태가 없다 — startExport의 응답이 완료 신호다. */
      return Promise.resolve(null);
    },
    listArtifacts: function (jobId) {
      if (USE_MOCK) return mockCall('listArtifacts', [jobId]);
      return http('GET', '/jobs/' + encodeURIComponent(jobId) + '/artifacts');
    },
    downloadArtifact: function (jobId, artifactId) {
      if (USE_MOCK) return mockCall('downloadArtifact', [jobId, artifactId]);
      return Promise.resolve({
        url: BASE + '/jobs/' + encodeURIComponent(jobId) + '/artifacts/' + encodeURIComponent(artifactId),
        fileName: null
      });
    },

    /* ── F8 버전·스냅샷 ────────────────────────────────────── */
    listVersions: function (jobId) {
      if (USE_MOCK) return mockCall('listVersions', [jobId]);
      return http('GET', '/jobs/' + encodeURIComponent(jobId) + '/versions');
    },
    restoreVersion: function (jobId, versionId) {
      if (USE_MOCK) return mockCall('restoreVersion', [jobId, versionId]);
      return http('POST', '/jobs/' + encodeURIComponent(jobId) + '/versions/' + encodeURIComponent(versionId) + '/restore')
        .then(function () { return { restored: true }; });
    },
    getProject: function (jobId) {
      if (USE_MOCK) return mockCall('getProject', [jobId]);
      /* JobResponse에는 쪽수가 없다 → 작업 문서가 준비된 뒤 /info로 쪽수를 합친다. */
      return PublishingApi.getJob(jobId).then(function (job) {
        job.pageCount = 0;
        if (job.status !== 'RUNNING' && job.status !== 'DONE') return job;
        return http('GET', '/jobs/' + encodeURIComponent(jobId) + '/info').then(function (i) {
          job.pageCount = i.pageCount || 0;
          return job;
        });
      });
    },
    saveNow: function (jobId) {
      if (USE_MOCK) return mockCall('saveNow', [jobId]);
      /* 4장에 저장 전용 엔드포인트 없음 — commands 채널 스냅샷으로 대체(백엔드 협의) */
      return PublishingApi.applyCommands(jobId, [], '수동 저장').then(function (r) {
        return { saved: !!r.applied, snapshotId: r.snapshotId };
      });
    }
  };

  global.PublishingApi = PublishingApi;
})(window);
