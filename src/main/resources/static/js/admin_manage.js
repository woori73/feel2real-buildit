/**
 * 관리자 홈페이지 관리 화면 (PR-C). API: /api/admin/academies/{academyId}/builder/** (docs/API.md)
 * - 대상 academyId는 경로(data-academy-id)에서 한 번 읽고 모든 호출이 같은 값을 쓴다.
 * - 서버가 준 문자열은 textContent로만 넣는다(XSS 방지). 공개 홈페이지는 게시 전까지 바뀌지 않는다.
 */
(function () {
  'use strict';
  var ID = document.getElementById('ahApp').dataset.academyId;
  var API = '/api/admin/academies/' + encodeURIComponent(ID) + '/builder';
  var PREVIEW = '/admin/academies/' + encodeURIComponent(ID) + '/preview';
  var PRESETS = {
    '': '기본', EXAM: '입시학원형', ENGLISH: '영어회화형', ARTS: '예체능형', STUDY: '공부방형', ONLINE: '온라인학원형',
    MINIMAL: '미니멀형', DARK: '다크형', WARM: '감성형', PHOTO: '포토형', DYNAMIC: '다이나믹형'
  };
  var $ = function (id) { return document.getElementById(id); };
  var S = { academy: null, live: null, revision: null, state: null, busy: false };

  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; }
  function api(method, path, body) {
    return fetch(API + path, {
      method: method, credentials: 'same-origin',
      headers: body ? { 'Content-Type': 'application/json' } : undefined, body: body ? JSON.stringify(body) : undefined
    }).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (j) {
        if (!r.ok) { var e = new Error(j.message || '요청을 처리하지 못했어요.'); e.status = r.status; e.code = j.code; throw e; }
        return j;
      });
    });
  }
  function toast(msg) { var t = $('ahToast'); t.textContent = msg; t.hidden = false; clearTimeout(toast.h); toast.h = setTimeout(function () { t.hidden = true; }, 3500); }
  var FIELD_LABELS = { intro: '소개 문구', blogUrl: '블로그 주소', naverMapUrl: '네이버 지도 주소', heroYoutubeUrl: '대표 영상 주소', 'builderDraft.designPreset': '디자인' };
  function labels(list) { return list.map(function (k) { return FIELD_LABELS[k] || k; }).join(', '); }
  function fmt(iso) { return iso ? String(iso).replace('T', ' ').slice(0, 16) : ''; }
  function draft(settings) { return (settings && settings.builderDraft) || {}; }

  /* ───── 상태 표시 ───── */
  function applyState(j) {
    S.state = j; S.revision = j.revision;
    $('amIntro').value = j.intro || '';
    var preset = draft(j.pageSettings).designPreset || '';
    $('amPreset').value = Object.prototype.hasOwnProperty.call(PRESETS, preset) ? preset : '';
    $('amBlog').value = (j.pageSettings && j.pageSettings.blogUrl) || '';
    $('amMap').value = (j.pageSettings && j.pageSettings.naverMapUrl) || '';
    $('amYoutube').value = (j.pageSettings && j.pageSettings.heroYoutubeUrl) || '';
    $('amDirty').textContent = j.dirty ? '게시 안 된 변경 있음' : '공개와 같음';
    $('amLiveChanged').hidden = !j.liveChanged;
    $('amPublish').disabled = $('ahFullPublish').disabled = !!j.liveChanged;
  }
  function showBanner() {
    var a = S.academy;
    $('amName').textContent = a.name;
    var handle = a.handle ? '@' + a.handle : '(핸들 없음 · ' + a.slug + ')';
    $('amMeta').textContent = '대상 학원 ID ' + a.academyId + ' · ' + handle + ' · 상태 ' + a.status + ' · 소유자 ' + (a.ownerLoginId || '-') +
      ' — 여기서 하는 모든 작업은 이 학원에만 적용되고 기록돼요.';
    document.title = '[관리자] ' + a.name + ' — BuildIt';
  }

  /* ───── 변경 내용 비교 (작업본 vs 현재 공개) ───── */
  function diffRows() {
    var rows = [], live = S.live || {}, st = S.state || {};
    function add(label, before, after) { if ((before || '') !== (after || '')) rows.push([label, before || '(없음)', after || '(없음)']); }
    add('소개 문구', live.intro, st.intro);
    add('블로그 주소', live.blogUrl, st.pageSettings && st.pageSettings.blogUrl);
    add('네이버 지도 주소', live.naverMapUrl, st.pageSettings && st.pageSettings.naverMapUrl);
    add('대표 영상 주소', live.heroYoutubeUrl, st.pageSettings && st.pageSettings.heroYoutubeUrl);
    var a = live.builderDraft || {}, b = draft(st.pageSettings), keys = {};
    Object.keys(a).forEach(function (k) { keys[k] = 1; }); Object.keys(b).forEach(function (k) { keys[k] = 1; });
    Object.keys(keys).sort().forEach(function (k) { if (k !== 'homepage.renderMode') add(k === 'designPreset' ? '디자인' : k, a[k], b[k]); });
    return rows;
  }

  /* ───── 저장·되돌리기·반영 ───── */
  function guarded(fn) {
    if (S.busy) return; S.busy = true;
    return fn().catch(function (e) { toast(e.message); $('amSaveMsg').textContent = e.message; })
      .then(function () { S.busy = false; });
  }
  function save() {
    return guarded(function () {
      var cur = S.state.pageSettings || {}, ps = {};
      var blog = $('amBlog').value.trim(), map = $('amMap').value.trim(), yt = $('amYoutube').value.trim();
      if (blog !== (cur.blogUrl || '')) ps.blogUrl = blog;
      if (map !== (cur.naverMapUrl || '')) ps.naverMapUrl = map;
      if (yt !== (cur.heroYoutubeUrl || '')) ps.heroYoutubeUrl = yt;
      var preset = $('amPreset').value;
      if (preset !== (draft(cur).designPreset || '')) ps.builderDraft = { designPreset: preset };
      return api('PUT', '', { revision: S.revision, intro: $('amIntro').value, pageSettings: Object.keys(ps).length ? ps : null })
        .then(function (j) { applyState(j); $('amSaveMsg').textContent = '작업본을 저장했어요. 공개 홈페이지는 아직 그대로예요.'; toast('저장했어요'); return refreshLists(); });
    });
  }
  function discard() {
    if (!window.confirm('작업본을 현재 공개 내용으로 되돌릴까요? (공개 홈페이지는 바뀌지 않아요)')) return;
    return guarded(function () {
      return api('POST', '/discard', { revision: S.revision }).then(function (j) { applyState(j); toast('공개 내용으로 되돌렸어요'); return refreshLists(); });
    });
  }
  function rebase() {
    return guarded(function () {
      return api('POST', '/rebase', { revision: S.revision }).then(function (j) {
        applyState(j);
        var box = $('amRebaseResult'); box.textContent = '';
        box.appendChild(el('b', null, '공개 변경을 반영했어요.'));
        if (j.keptMine && j.keptMine.length) box.appendChild(el('div', null, '내 수정이 유지된 항목: ' + labels(j.keptMine)));
        if (j.conflicts && j.conflicts.length) box.appendChild(el('div', null, '양쪽이 모두 바꿔서 공개 값을 유지한 항목: ' + labels(j.conflicts) + ' — 필요하면 다시 수정하세요.'));
        box.hidden = false;
        return api('GET', '').then(function (o) { S.live = o.live; });
      }).then(refreshLists);
    });
  }

  /* ───── 미리보기 ───── */
  function openFull() { $('ahFrame').src = PREVIEW + '?t=' + Date.now(); $('ahFull').hidden = false; document.body.style.overflow = 'hidden'; $('ahBack').focus(); }
  function closeFull() { $('ahFull').hidden = true; document.body.style.overflow = ''; }
  function setDevice(mobile) {
    $('ahFrame').className = mobile ? 'mobile' : 'desktop';
    $('ahDevMobile').classList.toggle('on', mobile); $('ahDevMobile').setAttribute('aria-pressed', String(mobile));
    $('ahDevDesktop').classList.toggle('on', !mobile); $('ahDevDesktop').setAttribute('aria-pressed', String(!mobile));
  }

  /* ───── 게시 전 확인 ───── */
  function openPublish() {
    if (S.state.dirty === false && S.academy.status !== 'DRAFT') { toast('공개 내용과 같아서 게시할 변경이 없어요.'); return; }
    closeFull();
    var a = S.academy, t = $('amTarget'); t.textContent = '';
    t.appendChild(el('b', null, a.name)); t.appendChild(el('div', null, 'ID ' + a.academyId + ' · ' + (a.handle ? '@' + a.handle : a.slug) + ' · ' + a.status + ' · 소유자 ' + (a.ownerLoginId || '-')));
    var d = $('amDiff'); d.textContent = '';
    var rows = diffRows();
    if (!rows.length) d.appendChild(el('div', null, '공개 내용과 다른 항목이 없어요.'));
    rows.forEach(function (r) {
      var row = el('div'); row.appendChild(el('b', null, r[0])); row.appendChild(el('br'));
      var s = el('s', null, r[1]); var i = el('ins', null, r[2]); row.appendChild(s); row.appendChild(document.createTextNode(' → ')); row.appendChild(i); d.appendChild(row);
    });
    $('amFirstWrap').hidden = a.status !== 'DRAFT'; $('amFirst').checked = false;
    $('amConfirmLabel').textContent = '맞는 학원인지 확인하려면 "' + a.confirmKey + '"를 그대로 입력하세요';
    $('amConfirmKey').value = ''; $('amModalErr').hidden = true; $('amModal').hidden = false; $('amConfirmKey').focus();
  }
  function closePublish() { $('amModal').hidden = true; }
  function doPublish() {
    var a = S.academy, key = $('amConfirmKey').value.trim();
    if (key.toLowerCase() !== String(a.confirmKey).toLowerCase()) { showModalErr('입력한 값이 대상 학원과 달라요. 대상 학원을 다시 확인하세요.'); return; }
    if (a.status === 'DRAFT' && !$('amFirst').checked) { showModalErr('최초 공개 확인에 체크해 주세요.'); return; }
    return guarded(function () {
      return api('POST', '/publish', { revision: S.revision, confirmAcademyId: a.academyId, confirmHandle: key, confirmFirstPublish: $('amFirst').checked })
        .then(function (j) {
          applyState(j); if (j.firstPublish) a.status = 'PUBLISHED';
          closePublish(); toast(j.firstPublish ? '최초 공개했어요' : '게시했어요'); return reload();
        }).catch(function (e) {
          if (e.code === 'LIVE_CHANGED') { closePublish(); $('amLiveChanged').hidden = false; window.scrollTo(0, 0); toast(e.message); return; }
          showModalErr(e.message);
        });
    });
  }
  function showModalErr(m) { var e = $('amModalErr'); e.textContent = m; e.hidden = false; }

  /* ───── 목록: 버전·감사 ───── */
  function refreshLists() { return Promise.all([loadVersions(), loadAudit()]); }
  function loadVersions() {
    return api('GET', '/versions').then(function (list) {
      var ul = $('amVersions'); ul.textContent = '';
      if (!list.length) { ul.appendChild(el('li', null, '보관된 이전 공개 버전이 없어요.')); return; }
      list.forEach(function (v) {
        var li = el('li'); var left = el('div');
        left.appendChild(el('div', null, 'v' + v.versionNo + ' · ' + (v.note || '')));
        left.appendChild(el('div', 'when', fmt(v.createdAt) + ' · ' + (v.createdBy || ''))); li.appendChild(left);
        var b = el('button', 'ah-secondary', '작업본으로 복원'); b.type = 'button';
        b.addEventListener('click', function () { restore(v); }); li.appendChild(b); ul.appendChild(li);
      });
    }).catch(function () { $('amVersions').textContent = '버전을 불러오지 못했어요.'; });
  }
  function restore(v) {
    if (!window.confirm('v' + v.versionNo + ' 내용을 작업본으로 불러올까요? 공개 홈페이지는 게시 전까지 바뀌지 않아요.')) return;
    return guarded(function () {
      return api('POST', '/restore', { revision: S.revision, versionId: v.id }).then(function (j) { applyState(j); toast('작업본으로 복원했어요. 미리보기 후 게시하세요.'); return refreshLists(); });
    });
  }
  var ACTIONS = { ACCESS: '화면 열람', SAVE: '작업본 수정', PUBLISH: '게시', RESTORE: '복원', DISCARD: '되돌리기' };
  function loadAudit() {
    return api('GET', '/audit?limit=30').then(function (rows) {
      var ul = $('amAudit'); ul.textContent = '';
      if (!rows.length) { ul.appendChild(el('li', null, '기록이 없어요.')); return; }
      rows.forEach(function (r) {
        var li = el('li'); li.appendChild(el('div', null, (ACTIONS[r.action] || r.action) + (r.published ? ' · 공개 반영' : '') + (r.summary ? ' — ' + r.summary : '')));
        li.appendChild(el('div', 'when', fmt(r.createdAt) + ' · ' + r.actorLoginId)); ul.appendChild(li);
      });
    }).catch(function () { $('amAudit').textContent = '기록을 불러오지 못했어요.'; });
  }

  /* ───── 초기화 ───── */
  function reload() {
    return api('GET', '').then(function (j) {
      S.academy = j.academy; S.live = j.live; showBanner(); applyState(j);
      $('amMemberDirty').hidden = !j.memberDraftDirty; $('amBody').hidden = false;
      return refreshLists();
    });
  }
  function init() {
    var sel = $('amPreset');
    Object.keys(PRESETS).forEach(function (k) { var o = el('option', null, PRESETS[k]); o.value = k; sel.appendChild(o); });
    $('amSave').addEventListener('click', save);
    $('amDiscard').addEventListener('click', discard);
    $('amRebase').addEventListener('click', rebase);
    $('amPreview').addEventListener('click', openFull);
    $('amPublish').addEventListener('click', openPublish);
    $('ahFullPublish').addEventListener('click', openPublish);
    $('ahBack').addEventListener('click', closeFull);
    $('ahDevMobile').addEventListener('click', function () { setDevice(true); });
    $('ahDevDesktop').addEventListener('click', function () { setDevice(false); });
    $('amModalCancel').addEventListener('click', closePublish);
    $('amModalOk').addEventListener('click', doPublish);
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape') { if (!$('amModal').hidden) closePublish(); else if (!$('ahFull').hidden) closeFull(); } });
    reload().catch(function (e) {
      var box = $('amError'); box.textContent = e.status === 404 ? '대상 학원을 찾을 수 없어요.' : (e.message || '불러오지 못했어요.'); box.hidden = false;
      $('amName').textContent = '대상 학원을 열 수 없어요';
    });
  }
  init();
})();
