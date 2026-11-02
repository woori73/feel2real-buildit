(function () {
  'use strict';
  var app = document.getElementById('snsApp');
  if (!app) return;
  var section = app.getAttribute('data-section') || 'home';
  var API = '/api/admin/sns';
  var flash = document.getElementById('snsFlash');
  var dialog = document.getElementById('snsDialog');
  var dialogForm = document.getElementById('snsDialogForm');
  var cache = { brands: [], refs: [], accounts: [] };

  var STATUS = { GENERATING: '생성 중', REVIEW: '검토 대기', SCHEDULED: '예약', PUBLISHED: '발행', FAILED: '실패', DISCARDED: '버림',
    PENDING: '대기', RUNNING: '진행 중', DONE: '완료', CONNECTED: '연결됨', EXPIRED: '만료', DISCONNECTED: '연결 해제', ERROR: '오류' };
  var SCHEDULE = { DAILY: '매일', EVERY_OTHER_DAY: '격일', WEEKLY: '매주' };
  var SAVE = { LIST: '목록 저장(검토 후 발행)', PAUSED: '멈춤', PUBLISH: '자동 발행(준비 중)' };

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function won(n) { return (n == null ? 0 : n).toLocaleString('ko-KR') + '원'; }
  function dt(s) { return s ? String(s).replace('T', ' ').slice(0, 16) : '-'; }
  function show(msg, isError) {
    flash.textContent = msg; flash.hidden = !msg; flash.classList.toggle('is-error', !!isError);
    if (msg) window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function req(method, path, body, isForm) {
    var opt = { method: method, credentials: 'same-origin', headers: {} };
    if (body !== undefined) {
      if (isForm) opt.body = body;
      else { opt.headers['Content-Type'] = 'application/json'; opt.body = JSON.stringify(body); }
    }
    return fetch(API + path, opt).then(function (res) {
      if (res.status === 204) return null;
      return res.json().catch(function () { return {}; }).then(function (data) {
        if (!res.ok) throw new Error((data && data.message) || ('요청 실패 (' + res.status + ')'));
        return data;
      });
    });
  }
  function act(promise, okMsg, after) {
    return promise.then(function (r) { if (okMsg) show(okMsg); if (after) after(r); return r; })
      .catch(function (e) { show(e.message, true); });
  }

  // ---- 탭 ----
  document.querySelectorAll('[data-tab]').forEach(function (a) {
    if (a.getAttribute('data-tab') === section) a.setAttribute('aria-current', 'page');
  });
  var panel = document.querySelector('[data-panel="' + section + '"]');
  if (panel) panel.hidden = false;

  var params = new URLSearchParams(location.search);
  if (params.get('connected')) show('인스타그램 계정을 연결했습니다.');
  if (params.get('error')) show(params.get('error'), true);

  // ---- 홈 ----
  function loadHome() {
    act(req('GET', '/summary'), null, function (s) {
      var alerts = document.getElementById('snsAlerts');
      alerts.innerHTML = s.alerts.length ? s.alerts.map(function (a) { return '<li>' + esc(a) + '</li>'; }).join('') : '';
      var limit = s.monthlyBudgetKrw ? won(s.monthlyBudgetKrw) : '미설정';
      document.getElementById('snsStats').innerHTML = [
        ['검토 대기', s.reviewCount + '건'], ['생성 중', s.generatingCount + '건'], ['오늘 예약', s.scheduledToday + '건'],
        ['실패', s.failedCount + '건'], ['이번 달 발행', s.publishedThisMonth + '건'], ['이번 달 생성', s.generationsThisMonth + '회'],
        ['이번 달 비용', won(s.spentThisMonthKrw) + ' / ' + limit], ['예약 실행', s.schedulerEnabled ? '켜짐(매분 확인)' : '꺼짐']
      ].map(function (x) { return '<div class="sns-stat"><span>' + esc(x[0]) + '</span><strong>' + esc(x[1]) + '</strong></div>'; }).join('');
    });
    act(req('GET', '/logs?limit=30'), null, function (rows) {
      document.getElementById('snsLogs').innerHTML = rows.length ? rows.map(function (l) {
        return '<tr><td>' + esc(dt(l.createdAt)) + '</td><td>' + esc({ IMAGE: '이미지', TEXT: '문구', PUBLISH: '발행' }[l.kind] || l.kind) + '</td><td>' +
          esc(l.ruleId || '-') + '</td><td>' + esc(l.postId || '-') + '</td><td>' + esc(l.model || '-') + '</td><td>' + won(l.costKrw) +
          '</td><td>' + (l.durationMs / 1000).toFixed(1) + '초</td><td>' + (l.success ? '성공' : '<span class="bad">실패</span> ' + esc(l.errorMessage || '')) + '</td></tr>';
      }).join('') : '<tr><td colspan="8">아직 기록이 없습니다.</td></tr>';
    });
  }

  // ---- 계정 ----
  function loadAccounts() {
    return act(req('GET', '/accounts'), null, function (rows) {
      cache.accounts = rows;
      var box = document.getElementById('snsAccounts');
      if (!box) return;
      box.innerHTML = rows.length ? rows.map(function (a) {
        return '<article class="sns-card"><h4>@' + esc(a.username || '(이름 없음)') + ' <small>' + esc(STATUS[a.status] || a.status) + '</small></h4>' +
          '<p>토큰 만료: ' + esc(dt(a.tokenExpiresAt)) + (a.expiringSoon ? ' <span class="bad">곧 만료</span>' : '') + '</p>' +
          '<p>최근 갱신: ' + esc(dt(a.tokenRefreshedAt)) + '</p>' + (a.lastError ? '<p class="bad">' + esc(a.lastError) + '</p>' : '') +
          '<div class="sns-row">' + (a.status === 'CONNECTED' ? '<button type="button" class="btn" data-acc-refresh="' + a.id + '">토큰 갱신</button>' +
          '<button type="button" class="btn" data-acc-off="' + a.id + '">연결 해제</button>' : '') +
          '<button type="button" class="btn btn-danger" data-acc-del="' + a.id + '">삭제</button></div></article>';
      }).join('') : '<p class="hint">연결된 계정이 없습니다.</p>';
    });
  }
  on('#snsConnect', 'click', function () {
    act(req('POST', '/accounts/connect'), null, function (r) { location.href = r.url; });
  });
  delegate('[data-acc-refresh]', function (el) { act(req('POST', '/accounts/' + el.dataset.accRefresh + '/refresh'), '토큰을 갱신했습니다.', loadAccounts); });
  delegate('[data-acc-off]', function (el) {
    if (confirm('연결을 해제할까요? 저장된 토큰을 지웁니다.')) act(req('POST', '/accounts/' + el.dataset.accOff + '/disconnect'), '연결을 해제했습니다.', loadAccounts);
  });
  delegate('[data-acc-del]', function (el) {
    if (confirm('계정을 삭제할까요?')) act(req('DELETE', '/accounts/' + el.dataset.accDel), '삭제했습니다.', loadAccounts);
  });

  // ---- 브랜드 ----
  function loadBrands() {
    return act(req('GET', '/brands'), null, function (rows) {
      cache.brands = rows;
      var box = document.getElementById('snsBrands');
      if (!box) return;
      box.innerHTML = rows.map(function (b) {
        return '<article class="sns-card"><h4>' + esc(b.name) + ' <small>규칙 ' + b.ruleCount + '개</small></h4>' +
          '<p class="clip">' + esc(b.purpose || '') + '</p><pre class="fixed">' + esc(b.fixedText || '') + '</pre>' +
          '<div class="sns-row"><button type="button" class="btn" data-brand-edit="' + b.id + '">편집</button>' +
          '<button type="button" class="btn" data-brand-test="' + b.id + '">문구 시험 생성</button>' +
          '<button type="button" class="btn btn-danger" data-brand-del="' + b.id + '">삭제</button></div>' +
          '<div class="sns-preview" id="brandPreview' + b.id + '"></div></article>';
      }).join('');
    });
  }
  function loadRefs() {
    return act(req('GET', '/references'), null, function (rows) {
      cache.refs = rows;
      var box = document.getElementById('snsRefs');
      if (!box) return;
      box.innerHTML = rows.map(function (r) {
        return '<figure class="sns-ref"><img src="' + esc(r.previewUrl) + '" alt="' + esc(r.label) + '" loading="lazy">' +
          '<figcaption>' + esc(r.label) + (r.approved ? ' <small>승인</small>' : ' <small class="bad">미승인</small>') + '<br><small>규칙 ' + r.ruleCount + '개</small></figcaption>' +
          '<div class="sns-row"><button type="button" class="btn" data-ref-approve="' + r.id + '" data-approved="' + (!r.approved) + '">' + (r.approved ? '승인 취소' : '승인') + '</button>' +
          '<button type="button" class="btn btn-danger" data-ref-del="' + r.id + '">삭제</button></div></figure>';
      }).join('');
    });
  }
  function brandForm(b) {
    b = b || { fixedTextPosition: 'BOTTOM', language: 'ko' };
    openDialog((b.id ? '브랜드 편집' : '새 브랜드'),
      field('name', '이름', b.name, true) + area('industry', '업종', b.industry) + area('audience', '대상', b.audience) +
      area('purpose', '목적', b.purpose) + area('instructions', '상세 지시', b.instructions, 8) +
      area('fixedText', '고정 문구(서버가 그대로 붙임)', b.fixedText, 4) +
      select('fixedTextPosition', '고정 문구 위치', b.fixedTextPosition, { TOP: '본문 위', BOTTOM: '본문 아래' }) +
      area('bannedPhrases', '금지 표현(줄마다 하나)', b.bannedPhrases, 3) + field('language', '언어', b.language),
      function (data) {
        return b.id ? req('PUT', '/brands/' + b.id, data) : req('POST', '/brands', data);
      }, function () { show('저장했습니다.'); loadBrands(); });
  }
  on('#snsNewBrand', 'click', function () { brandForm(); });
  delegate('[data-brand-edit]', function (el) { brandForm(cache.brands.find(function (b) { return String(b.id) === el.dataset.brandEdit; })); });
  delegate('[data-brand-del]', function (el) {
    if (confirm('브랜드를 삭제할까요?')) act(req('DELETE', '/brands/' + el.dataset.brandDel), '삭제했습니다.', loadBrands);
  });
  delegate('[data-brand-test]', function (el) {
    var box = document.getElementById('brandPreview' + el.dataset.brandTest);
    box.textContent = '문구를 만드는 중...';
    req('POST', '/brands/' + el.dataset.brandTest + '/test-caption').then(function (p) {
      box.innerHTML = '<p><b>주제</b> ' + esc(p.topic || '') + '</p><pre>' + esc(p.caption) + '\n\n' + esc(p.hashtags) + '</pre>';
    }).catch(function (e) { box.innerHTML = '<p class="bad">' + esc(e.message) + '</p>'; });
  });
  on('#snsRefForm', 'submit', function (e) {
    e.preventDefault();
    act(req('POST', '/references', new FormData(e.target), true), '올렸습니다. 확인 후 승인해 주세요.', function () { e.target.reset(); loadRefs(); });
  });
  delegate('[data-ref-approve]', function (el) {
    act(req('POST', '/references/' + el.dataset.refApprove + '/approve?approved=' + el.dataset.approved), '바꿨습니다.', loadRefs);
  });
  delegate('[data-ref-del]', function (el) {
    if (confirm('참고 이미지를 삭제할까요?')) act(req('DELETE', '/references/' + el.dataset.refDel), '삭제했습니다.', loadRefs);
  });

  // ---- 규칙 ----
  var ruleCache = [];
  function loadRules() {
    return act(req('GET', '/rules'), null, function (rows) {
      ruleCache = rows;
      document.getElementById('snsRules').innerHTML = rows.map(function (r) {
        return '<article class="sns-card"><h4>' + esc(r.title) + ' <small>' + (r.enabled ? '켜짐' : '꺼짐') + '</small></h4>' +
          '<p>' + esc(r.brandName || '') + ' · ' + esc(r.accountName || '계정 미연결') + '</p>' +
          '<p>' + esc(SCHEDULE[r.scheduleType] || r.scheduleType) + (r.scheduleDays ? '(' + esc(r.scheduleDays) + ')' : '') + ' ' + esc(String(r.runTime).slice(0, 5)) +
          ' · ' + esc(r.aspectRatio) + ' · ' + esc(SAVE[r.saveMode] || r.saveMode) + '</p>' +
          '<p class="clip">' + esc(r.imagePrompt) + '</p><p>' + esc(r.fixedHashtags || '') + (r.aiHashtags ? ' + AI 해시태그' : '') + '</p>' +
          '<div class="sns-row"><button type="button" class="btn" data-rule-toggle="' + r.id + '" data-enabled="' + (!r.enabled) + '">' + (r.enabled ? '끄기' : '켜기') + '</button>' +
          '<button type="button" class="btn" data-rule-edit="' + r.id + '">편집</button>' +
          '<button type="button" class="btn" data-rule-trial="' + r.id + '">시험 생성</button>' +
          '<button type="button" class="btn" data-rule-run="' + r.id + '">즉시 실행</button>' +
          '<button type="button" class="btn btn-danger" data-rule-del="' + r.id + '">삭제</button></div>' +
          '<div class="sns-trials" id="ruleTrials' + r.id + '"></div></article>';
      }).join('');
      rows.forEach(function (r) { loadTrials(r.id); });
    });
  }
  function loadTrials(ruleId) {
    req('GET', '/rules/' + ruleId + '/trials').then(function (rows) {
      var box = document.getElementById('ruleTrials' + ruleId);
      if (!box || !rows.length) return;
      box.innerHTML = '<h5>시험 생성 결과</h5>' + rows.map(postCard).join('');
    }).catch(function () {});
  }
  function ruleForm(r) {
    Promise.all([req('GET', '/brands'), req('GET', '/references'), req('GET', '/accounts')]).then(function (all) {
      var brands = {}, refs = { '': '(없음)' }, accs = { '': '(연결 안 함)' };
      all[0].forEach(function (b) { brands[b.id] = b.name; });
      all[1].filter(function (x) { return x.approved; }).forEach(function (x) { refs[x.id] = x.label; });
      all[2].filter(function (x) { return x.status === 'CONNECTED'; }).forEach(function (x) { accs[x.id] = '@' + x.username; });
      r = r || { imageStyle: 'PHOTO', aspectRatio: '1:1', aiHashtags: true, scheduleType: 'DAILY', runTime: '19:00', saveMode: 'LIST' };
      openDialog(r.id ? '규칙 편집' : '새 규칙',
        field('title', '제목', r.title, true) + select('brandId', '브랜드', r.brandId, brands) + select('accountId', '계정', r.accountId || '', accs) +
        select('referenceImageId', '참고 이미지(승인된 것)', r.referenceImageId || '', refs) + area('imagePrompt', '이미지 프롬프트', r.imagePrompt, 6) +
        select('imageStyle', '스타일', r.imageStyle, { PHOTO: '사진', ILLUSTRATION: '일러스트', THREE_D: '3D' }) +
        select('aspectRatio', '비율', r.aspectRatio, { '1:1': '1:1', '4:5': '4:5', '16:9': '16:9' }) +
        field('fixedHashtags', '고정 해시태그(공백 구분, 최대 5)', r.fixedHashtags) +
        select('aiHashtags', 'AI 해시태그', String(r.aiHashtags), { 'true': '켬', 'false': '끔' }) +
        select('scheduleType', '주기', r.scheduleType, SCHEDULE) + field('scheduleDays', '요일(매주일 때, 예: MON,WED,FRI)', r.scheduleDays) +
        field('runTime', '실행 시간(서울)', String(r.runTime || '19:00').slice(0, 5), true, 'time') + field('startDate', '시작일', r.startDate, false, 'date') +
        select('saveMode', '저장 방식', r.saveMode, { LIST: SAVE.LIST, PAUSED: SAVE.PAUSED }) +
        field('monthlyBudgetKrw', '이 규칙 월 상한(원, 비우면 전체 상한만)', r.monthlyBudgetKrw, false, 'number'),
        function (d) {
          var body = {
            title: d.title, brandId: num(d.brandId), accountId: num(d.accountId), referenceImageId: num(d.referenceImageId),
            imagePrompt: d.imagePrompt, imageStyle: d.imageStyle, aspectRatio: d.aspectRatio, fixedHashtags: d.fixedHashtags,
            aiHashtags: d.aiHashtags === 'true', scheduleType: d.scheduleType, scheduleDays: d.scheduleDays || null,
            runTime: d.runTime ? d.runTime + ':00' : null, startDate: d.startDate || null, saveMode: d.saveMode, monthlyBudgetKrw: num(d.monthlyBudgetKrw)
          };
          return r.id ? req('PUT', '/rules/' + r.id, body) : req('POST', '/rules', body);
        }, function () { show('저장했습니다.'); loadRules(); });
    }).catch(function (e) { show(e.message, true); });
  }
  on('#snsNewRule', 'click', function () { ruleForm(); });
  delegate('[data-rule-edit]', function (el) { ruleForm(ruleCache.find(function (r) { return String(r.id) === el.dataset.ruleEdit; })); });
  delegate('[data-rule-toggle]', function (el) { act(req('POST', '/rules/' + el.dataset.ruleToggle + '/enabled?enabled=' + el.dataset.enabled), '바꿨습니다.', loadRules); });
  delegate('[data-rule-del]', function (el) {
    if (confirm('규칙을 삭제할까요?')) act(req('DELETE', '/rules/' + el.dataset.ruleDel), '삭제했습니다.', loadRules);
  });
  delegate('[data-rule-trial]', function (el) {
    act(req('POST', '/rules/' + el.dataset.ruleTrial + '/trial'), '시험 생성을 시작했습니다. 문구는 곧, 이미지는 GPU 작업자가 처리하면 나타납니다.', function () { loadTrials(el.dataset.ruleTrial); });
  });
  delegate('[data-rule-run]', function (el) {
    if (confirm('지금 한 번 실행할까요? 결과는 콘텐츠 > 검토 대기에 쌓입니다.')) act(req('POST', '/rules/' + el.dataset.ruleRun + '/run-now'), '실행했습니다.');
  });

  // ---- 콘텐츠 ----
  var postTab = 'review';
  function postCard(p) {
    var img = p.imageUrl ? '<img src="' + esc(p.imageUrl) + '" alt="생성 이미지" loading="lazy">' : '<div class="noimg">이미지 ' + esc(STATUS[p.imageStatus] || p.imageStatus) + '</div>';
    var actions = '';
    if (p.status === 'REVIEW' || p.status === 'SCHEDULED' || p.status === 'FAILED') actions += '<button type="button" class="btn" data-post-edit="' + p.id + '">문구 수정</button>';
    if (p.status === 'REVIEW' || p.status === 'FAILED') actions += '<button type="button" class="btn" data-post-regen="' + p.id + '">이미지 다시 만들기</button>';
    if (p.status === 'REVIEW' || p.status === 'SCHEDULED') actions += '<button type="button" class="btn btn-primary" data-post-publish="' + p.id + '">지금 발행</button>';
    if (p.status === 'REVIEW' && !p.trial) actions += '<button type="button" class="btn" data-post-schedule="' + p.id + '">예약 발행</button>';
    if (p.status === 'SCHEDULED') actions += '<button type="button" class="btn" data-post-unschedule="' + p.id + '">예약 취소</button>';
    if (p.status !== 'PUBLISHED' && p.status !== 'DISCARDED') actions += '<button type="button" class="btn btn-danger" data-post-discard="' + p.id + '">버리기</button>';
    return '<article class="sns-post" data-post-id="' + p.id + '">' + img + '<div class="body"><h4>' + esc(p.ruleTitle || '') + (p.trial ? ' <small>시험</small>' : '') +
      ' <small>' + esc(STATUS[p.status] || p.status) + '</small></h4>' + (p.topic ? '<p><b>주제</b> ' + esc(p.topic) + '</p>' : '') +
      '<pre>' + esc(p.caption || ('문구 ' + (STATUS[p.captionStatus] || p.captionStatus))) + (p.hashtags ? '\n\n' + esc(p.hashtags) : '') + '</pre>' +
      (p.scheduledAt ? '<p>예약: ' + esc(dt(p.scheduledAt)) + '</p>' : '') + (p.publishedAt ? '<p>발행: ' + esc(dt(p.publishedAt)) + ' · ' + esc(p.igMediaId || '') + '</p>' : '') +
      (p.failReason ? '<p class="bad">' + esc(p.failReason) + '</p>' : '') + '<p class="hint">만든 시각 ' + esc(dt(p.createdAt)) + '</p>' +
      '<div class="sns-row">' + actions + '</div></div></article>';
  }
  var postCache = {};
  function loadPosts() {
    return act(req('GET', '/posts?tab=' + postTab), null, function (rows) {
      postCache = {};
      rows.forEach(function (p) { postCache[p.id] = p; });
      document.getElementById('snsPosts').innerHTML = rows.length ? rows.map(postCard).join('') : '<p class="hint">게시물이 없습니다.</p>';
    });
  }
  document.querySelectorAll('[data-post-tab]').forEach(function (b) {
    b.addEventListener('click', function () {
      postTab = b.dataset.postTab;
      document.querySelectorAll('[data-post-tab]').forEach(function (x) { x.setAttribute('aria-pressed', String(x === b)); });
      loadPosts();
    });
  });
  function reloadAfterPost() { if (section === 'posts') loadPosts(); if (section === 'rules') loadRules(); }
  delegate('[data-post-edit]', function (el) {
    var id = el.dataset.postEdit;
    req('GET', '/posts/' + id).then(function (p) {
      openDialog('문구 수정', area('caption', '문구(고정 문구 포함)', p.caption, 12) + field('hashtags', '해시태그(공백 구분, 최대 30)', p.hashtags),
        function (d) { return req('PUT', '/posts/' + id, { caption: d.caption, hashtags: d.hashtags }); },
        function () { show('저장했습니다.'); reloadAfterPost(); });
    }).catch(function (e) { show(e.message, true); });
  });
  delegate('[data-post-regen]', function (el) { act(req('POST', '/posts/' + el.dataset.postRegen + '/regenerate-image'), '이미지를 다시 만들도록 GPU 작업 대기열에 넣었습니다.', reloadAfterPost); });
  delegate('[data-post-publish]', function (el) {
    if (confirm('인스타그램에 지금 올릴까요?')) act(req('POST', '/posts/' + el.dataset.postPublish + '/publish'), '처리했습니다. 결과는 발행/실패 탭에서 확인하세요.', reloadAfterPost);
  });
  delegate('[data-post-schedule]', function (el) {
    var id = el.dataset.postSchedule;
    openDialog('예약 발행', field('at', '발행 시각(서울)', '', true, 'datetime-local'),
      function (d) { return req('POST', '/posts/' + id + '/schedule', { scheduledAt: d.at.length === 16 ? d.at + ':00' : d.at }); },
      function () { show('예약했습니다.'); reloadAfterPost(); });
  });
  delegate('[data-post-unschedule]', function (el) { act(req('POST', '/posts/' + el.dataset.postUnschedule + '/unschedule'), '예약을 취소했습니다.', reloadAfterPost); });
  delegate('[data-post-discard]', function (el) {
    if (confirm('이 게시물을 버릴까요?')) act(req('POST', '/posts/' + el.dataset.postDiscard + '/discard'), '버렸습니다.', reloadAfterPost);
  });

  // ---- 대화상자 ----
  function openDialog(title, inner, submit, done) {
    dialogForm.innerHTML = '<h3>' + esc(title) + '</h3>' + inner +
      '<p class="bad" data-dialog-error hidden></p><div class="sns-row"><button type="submit" class="btn btn-primary" value="ok">저장</button>' +
      '<button type="button" class="btn" data-dialog-close>닫기</button></div>';
    dialogForm.onsubmit = function (e) {
      e.preventDefault();
      var data = {};
      new FormData(dialogForm).forEach(function (v, k) { data[k] = v; });
      var err = dialogForm.querySelector('[data-dialog-error]');
      submit(data).then(function (r) { dialog.close(); done(r); })
        .catch(function (ex) { err.textContent = ex.message; err.hidden = false; });
    };
    dialogForm.querySelector('[data-dialog-close]').onclick = function () { dialog.close(); };
    dialog.showModal();
  }
  function field(name, label, value, required, type) {
    return '<label>' + esc(label) + '<input name="' + name + '" type="' + (type || 'text') + '" value="' + esc(value == null ? '' : value) + '"' + (required ? ' required' : '') + '></label>';
  }
  function area(name, label, value, rows) {
    return '<label>' + esc(label) + '<textarea name="' + name + '" rows="' + (rows || 3) + '">' + esc(value == null ? '' : value) + '</textarea></label>';
  }
  function select(name, label, value, options) {
    return '<label>' + esc(label) + '<select name="' + name + '">' + Object.keys(options).map(function (k) {
      return '<option value="' + esc(k) + '"' + (String(value) === String(k) ? ' selected' : '') + '>' + esc(options[k]) + '</option>';
    }).join('') + '</select></label>';
  }
  function num(v) { return v === '' || v == null ? null : Number(v); }
  function on(sel, ev, fn) { var el = document.querySelector(sel); if (el) el.addEventListener(ev, fn); }
  function delegate(sel, fn) {
    app.addEventListener('click', function (e) { var el = e.target.closest(sel); if (el) fn(el); });
  }

  if (section === 'home') loadHome();
  if (section === 'accounts') loadAccounts();
  if (section === 'brands') { loadBrands(); loadRefs(); }
  if (section === 'rules') loadRules();
  if (section === 'posts') loadPosts();
})();
