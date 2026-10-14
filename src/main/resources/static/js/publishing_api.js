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
      if (!res.ok) throw new Error('HTTP ' + res.status + ' ' + method + ' ' + path);
      return isBinary ? res.blob() : res.json();
    });
  }

  var PublishingApi = {
    isMock: function () { return USE_MOCK; },
    constants: function () {
      /* 판형·스타일 목록: 1차 mock에서 제공. 실제 통합 시 백엔드
         GET /api/publishing/templates·styles 로 대체 예정 (연동 시점 협의). */
      return Promise.resolve(global.PublishingMock.meta);
    },

    /* ── 4.1 작업(Job) API ─────────────────────────────────── */
    listJobs: function () {
      if (USE_MOCK) return mockCall('listJobs', []);
      return http('GET', '/jobs').then(function (r) { return r.jobs; });
    },
    createJob: function (template, title, manuscriptFile) {
      if (USE_MOCK) return mockCall('createJob', [template, title, manuscriptFile ? manuscriptFile.name : null]);
      var fd = new FormData();
      fd.append('template', template);
      fd.append('title', title);
      if (manuscriptFile) fd.append('manuscript', manuscriptFile);
      return fetch(BASE + '/jobs', { method: 'POST', credentials: 'same-origin', body: fd })
        .then(function (r) { return r.json(); });
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
        .then(function (r) { return r.blob(); })
        .then(function (b) { return URL.createObjectURL(b); });
    },
    getThumbPng: function (jobId, n) {
      if (USE_MOCK) return mockCall('getThumbPng', [jobId, n]);
      return fetch(BASE + '/jobs/' + encodeURIComponent(jobId) + '/pages/' + n + '/thumb',
        { credentials: 'same-origin' })
        .then(function (r) { return r.blob(); })
        .then(function (b) { return URL.createObjectURL(b); });
    },
    applyCommands: function (jobId, commands, label) {
      if (USE_MOCK) return mockCall('applyCommands', [jobId, commands, label]);
      /* 실계약: POST .../commands { commands: [{id, params}] } → {applied, snapshotId}
         (label은 스냅샷 설명용 확장 필드 — 백엔드 협의 필요) */
      return http('POST', '/jobs/' + encodeURIComponent(jobId) + '/commands', { commands: commands });
    },

    /* ── F2 원고 반입 — 4장에 계약 미기재, 제안 경로로 구현 ─── */
    inspectManuscript: function (fileName, textSample) {
      if (USE_MOCK) return mockCall('inspectManuscript', [fileName, textSample]);
      /* TODO(백엔드): POST /api/publishing/jobs/{jobId}/manuscript/inspect */
      throw new Error('NOT_IMPLEMENTED: manuscript inspect');
    },
    importManuscript: function (jobId, fileName, estPages) {
      if (USE_MOCK) return mockCall('importManuscript', [jobId, fileName, estPages]);
      /* TODO(백엔드): file.place 명령 채널로 통일 가능 (commands API) */
      return PublishingApi.applyCommands(jobId, [
        { id: 'file.place', params: { name: fileName, estPages: estPages } }
      ], '원고 반입: ' + fileName);
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
      /* export도 job으로 처리 → 4.1 getJob 폴링 재사용(artifact job id 필요) */
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
      return http('POST', '/jobs/' + encodeURIComponent(jobId) + '/versions/' + encodeURIComponent(versionId) + '/restore');
    },
    getProject: function (jobId) {
      if (USE_MOCK) return mockCall('getProject', [jobId]);
      return PublishingApi.getJob(jobId);
    },
    saveNow: function (jobId) {
      if (USE_MOCK) return mockCall('saveNow', [jobId]);
      /* 4장에 저장 전용 엔드포인트 없음 — commands 채널 스냅샷으로 대체(백엔드 협의) */
      return PublishingApi.applyCommands(jobId, [], '수동 저장');
    }
  };

  global.PublishingApi = PublishingApi;
})(window);
