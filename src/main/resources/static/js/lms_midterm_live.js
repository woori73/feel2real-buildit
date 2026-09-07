/* 내신 LIVE 운영자 관리 화면.
 * 모든 쓰기는 /api/tutor/lms/{handle}/midterm-campaign/** — 서버가 requireOwnedAcademyByHandle 로
 * 소유자만 통과시킨다(타 학원 TUTOR·ADMIN 차단). 응답은 항상 최신 AdminView 라 화면을 다시 그린다.
 */
(function () {
  'use strict';

  var main = document.querySelector('.mtx');
  if (!main) return;
  var HANDLE = main.getAttribute('data-handle');
  var BASE = '/api/tutor/lms/' + encodeURIComponent(HANDLE) + '/midterm-campaign';
  var statusEl = document.querySelector('[data-mtx-status]');
  var view = null;
  var videos = [];
  var wikiMaterials = [];

  var CATS = ['SCHOOL_PRINT', 'ANALYSIS', 'WORKSHEET', 'VOCABULARY', 'GRAMMAR', 'VARIANT', 'FINAL_REVIEW', 'OTHER'];
  var VIS = ['PRIVATE', 'PREVIEW', 'PUBLIC', 'MEMBER'];
  var PREP_STEPS = ['SCHOOL_PRINT', 'ANALYSIS', 'WORKSHEET', 'VARIANT', 'FINAL_REVIEW'];
  var PREP_STATE = ['NOT_STARTED', 'IN_PROGRESS', 'DONE'];
  var MAKING_STAGES = ['SOURCE', 'ANALYSIS', 'WORKSHEET', 'VARIATION', 'FINAL'];

  // ── infra ──────────────────────────────────────────────────────────
  function say(msg, isErr) {
    statusEl.textContent = msg;
    statusEl.className = 'mtx-status ' + (isErr ? 'err' : 'ok');
    if (!isErr) setTimeout(function () { if (statusEl.textContent === msg) statusEl.className = 'mtx-status'; }, 4000);
  }
  function req(method, path, body) {
    var opts = { method: method, credentials: 'same-origin', headers: {} };
    if (body !== undefined) { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body); }
    return fetchAuth(BASE + path, opts).then(function (r) {
      if (!r.ok) return r.text().then(function (t) { throw new Error(t || ('요청 실패 ' + r.status)); });
      return r.status === 204 ? null : r.json();
    });
  }
  function apply(promise, okMsg) {
    return promise.then(function (data) {
      if (data) { view = data; renderAll(); }
      if (okMsg) say(okMsg);
    }).catch(function (e) { say(e.message || '오류가 발생했습니다.', true); });
  }
  function el(tag, cls, text) { var n = document.createElement(tag); if (cls) n.className = cls; if (text != null) n.textContent = text; return n; }
  function field(label, control) {
    var l = el('label', 'mtx-field');
    l.appendChild(el('span', null, label));
    l.appendChild(control);
    return l;
  }
  function input(type, value) { var i = document.createElement('input'); i.type = type; if (value != null) i.value = value; return i; }
  function textarea(value) { var t = document.createElement('textarea'); if (value != null) t.value = value; return t; }
  function select(options, value, labels) {
    var s = document.createElement('select');
    options.forEach(function (o, idx) {
      var opt = document.createElement('option');
      opt.value = o;
      opt.textContent = labels ? labels[idx] : o;
      if (String(o) === String(value)) opt.selected = true;
      s.appendChild(opt);
    });
    return s;
  }
  function checkbox(label, checked) {
    var w = el('label', 'mtx-check');
    var c = input('checkbox'); c.checked = !!checked;
    w.appendChild(c); w.appendChild(document.createTextNode(label));
    w.control = c;
    return w;
  }
  function num(v) { return v === '' || v == null ? null : Number(v); }
  function str(v) { return v === '' ? null : v; }

  // ── boot ───────────────────────────────────────────────────────────
  document.querySelectorAll('[data-mtx-tabs] button').forEach(function (btn) {
    btn.addEventListener('click', function () {
      document.querySelectorAll('[data-mtx-tabs] button').forEach(function (b) { b.classList.toggle('active', b === btn); });
      document.querySelectorAll('.mtx-panel').forEach(function (p) {
        p.hidden = p.getAttribute('data-tab-panel') !== btn.getAttribute('data-tab');
      });
    });
  });

  Promise.all([
    req('GET', ''),
    fetchAuth('/api/tutor/academy/videos', { credentials: 'same-origin' }).then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; }),
    fetchAuth('/api/wiki/analysis-materials', { credentials: 'same-origin' }).then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; })
  ]).then(function (res) {
    view = res[0]; videos = res[1] || []; wikiMaterials = res[2] || [];
    renderAll();
  }).catch(function (e) { say(e.message || '불러오지 못했습니다.', true); });

  function renderAll() {
    renderCampaign();
    renderSchools();
    renderMaterials();
    renderLogs();
    renderMaking();
    renderNext();
  }

  // ── 캠페인 탭 ───────────────────────────────────────────────────────
  function renderCampaign() {
    var host = document.querySelector('[data-mtx-campaign]');
    host.innerHTML = '';
    var v = view || {};

    var card = el('div', 'mtx-card');
    card.appendChild(el('h3', null, '캠페인 기본 정보'));

    var examName = input('text', v.examName || '2026 2학기 중간고사');
    var examYear = input('number', v.examYear || 2026);
    var semester = input('text', v.semester || '2학기');
    var examType = input('text', v.examType || '중간고사');
    var pStart = input('date', v.periodStart || '');
    var pEnd = input('date', v.periodEnd || '');
    var stateSel = select(['PREPARING', 'FINAL_WEEK', 'EXAM_DONE', 'NEXT_EXAM'], v.state || 'PREPARING',
      ['준비 중', '시험 직전 주', '시험 종료', '다음 시험 전환']);

    card.appendChild(field('시험명', examName));
    var row1 = el('div', 'mtx-row');
    row1.appendChild(field('연도', examYear));
    row1.appendChild(field('학기', semester));
    card.appendChild(row1);
    var row2 = el('div', 'mtx-row');
    row2.appendChild(field('시험 종류', examType));
    row2.appendChild(field('진행 단계', stateSel));
    card.appendChild(row2);
    var row3 = el('div', 'mtx-row');
    row3.appendChild(field('시험기간 시작', pStart));
    row3.appendChild(field('시험기간 종료', pEnd));
    card.appendChild(row3);

    card.appendChild(el('h3', null, 'HERO (첫 화면)'));
    var hEyebrow = input('text', v.heroEyebrow || '');
    var hTitle = input('text', v.heroTitle || '');
    var hBody = textarea(v.heroBody || '');
    var hCta = input('text', v.heroCtaLabel || '');
    card.appendChild(field('eyebrow (예: 2026 FALL MIDTERM · APGUJEONG EDUHOLIC)', hEyebrow));
    card.appendChild(field('제목', hTitle));
    card.appendChild(field('본문', hBody));
    card.appendChild(field('CTA 라벨 (예: 우리 학교 찾기)', hCta));

    card.appendChild(el('h3', null, '홈페이지 팝업'));
    var popEnabled = checkbox('팝업 노출 (에듀홀릭 홈페이지)', v.popupEnabled);
    var popHead = input('text', v.popupHeadline || '');
    var popBody = textarea(v.popupBody || '');
    card.appendChild(popEnabled);
    card.appendChild(field('팝업 headline', popHead));
    card.appendChild(field('팝업 본문', popBody));

    var visualWrap = el('div', 'mtx-field');
    visualWrap.appendChild(el('span', null, '팝업 비주얼 이미지'));
    if (v.popupVisualUrl) {
      var pv = document.createElement('img'); pv.src = v.popupVisualUrl + '?t=' + Date.now();
      pv.style.cssText = 'max-width:180px;border-radius:8px;display:block;margin-bottom:6px'; pv.alt = '';
      visualWrap.appendChild(pv);
    }
    var file = input('file'); file.accept = 'image/*';
    visualWrap.appendChild(file);
    if (v.exists) {
      var upBtn = el('button', 'mtx-btn ghost', '팝업 이미지 업로드');
      upBtn.type = 'button';
      upBtn.addEventListener('click', function () {
        if (!file.files || !file.files[0]) { say('이미지 파일을 선택해 주세요.', true); return; }
        var fd = new FormData(); fd.append('image', file.files[0]);
        apply(
          fetchAuth(BASE + '/popup-visual', { method: 'POST', credentials: 'same-origin', body: fd })
            .then(function (r) { if (!r.ok) return r.text().then(function (t) { throw new Error(t || '업로드 실패'); }); return r.json(); }),
          '팝업 이미지를 업로드했습니다.');
      });
      visualWrap.appendChild(upBtn);
      if (v.popupVisualUrl) {
        var rmBtn = el('button', 'mtx-btn danger', '이미지 제거');
        rmBtn.type = 'button';
        rmBtn.style.marginLeft = '6px';
        rmBtn.addEventListener('click', function () {
          apply(req('DELETE', '/popup-visual'), '팝업 이미지를 제거했습니다.');
        });
        visualWrap.appendChild(rmBtn);
      }
    } else {
      visualWrap.appendChild(el('p', 'mtx-sub', '캠페인을 먼저 만든 뒤 이미지를 업로드할 수 있습니다.'));
    }
    card.appendChild(visualWrap);

    var saveBtn = el('button', 'mtx-btn', v.exists ? '캠페인 저장' : '캠페인 만들기');
    saveBtn.type = 'button';
    saveBtn.addEventListener('click', function () {
      var payload = {
        examName: examName.value.trim(), examYear: num(examYear.value), semester: semester.value.trim(),
        examType: examType.value.trim(), periodStart: str(pStart.value), periodEnd: str(pEnd.value),
        state: stateSel.value, heroEyebrow: str(hEyebrow.value), heroTitle: str(hTitle.value),
        heroBody: str(hBody.value), heroCtaLabel: str(hCta.value),
        popupEnabled: popEnabled.control.checked, popupHeadline: str(popHead.value), popupBody: str(popBody.value)
      };
      apply(req('PUT', '', payload), '캠페인을 저장했습니다.');
    });

    var pubBtn = el('button', 'mtx-btn ' + (v.published ? 'danger' : ''), v.published ? '비공개로 전환' : '공개하기');
    pubBtn.type = 'button';
    pubBtn.disabled = !v.exists;
    pubBtn.addEventListener('click', function () {
      apply(req('PATCH', '/publish', { published: !v.published }),
        !v.published ? '공개했습니다.' : '비공개로 전환했습니다.');
    });

    var btnRow = el('div', 'mtx-btn-row');
    btnRow.appendChild(saveBtn);
    btnRow.appendChild(pubBtn);
    card.appendChild(btnRow);
    if (v.exists) {
      var badge = el('span', 'mtx-pill ' + (v.published ? 'on' : 'off'), v.published ? '공개 중' : '비공개');
      card.appendChild(badge);
    }
    host.appendChild(card);
  }

  // ── 학교 컬렉션 탭 ─────────────────────────────────────────────────
  function renderSchools() {
    var host = document.querySelector('[data-mtx-schools]');
    host.innerHTML = '';
    if (!view || !view.exists) { host.appendChild(el('p', 'mtx-empty', '먼저 캠페인을 만들어 주세요.')); return; }

    var head = el('div', 'mtx-list-head');
    head.appendChild(el('h2', null, '학교 컬렉션'));
    host.appendChild(head);

    (view.schoolCollections || []).forEach(function (c) { host.appendChild(schoolCard(c)); });

    var addCard = el('div', 'mtx-card');
    addCard.appendChild(el('h3', null, '새 학교 추가'));
    var sn = input('text'); sn.placeholder = '학교명 (예: 신구중)';
    var gr = input('number'); gr.min = 1; gr.max = 3; gr.placeholder = '학년(1~3)';
    var addBtn = el('button', 'mtx-btn', '추가');
    addBtn.type = 'button';
    addBtn.addEventListener('click', function () {
      apply(req('POST', '/school-collections', { schoolName: sn.value.trim(), grade: num(gr.value), published: false }),
        '학교를 추가했습니다.');
    });
    addCard.appendChild(field('학교명', sn));
    addCard.appendChild(field('학년', gr));
    addCard.appendChild(addBtn);
    host.appendChild(addCard);
  }

  function schoolCard(c) {
    var d = el('details', 'mtx-collection');
    var sum = el('summary');
    sum.appendChild(document.createTextNode(c.schoolName + ' ' + c.grade + '학년'));
    sum.appendChild(el('span', 'mtx-pill ' + (c.published ? 'on' : 'off'), c.published ? '공개' : '비공개'));
    d.appendChild(sum);
    var body = el('div', 'mtx-collection-body');

    var examTitle = input('text', c.examTitle || '');
    var statusLabel = input('text', c.statusLabel || '');
    var prepHead = input('text', c.prepHeadline || '');
    var feat = textarea(c.examFeatureNote || '');
    var sort = input('number', c.sortOrder || 0);
    var pub = checkbox('공개', c.published);
    body.appendChild(field('시험명 표기 (예: 2026 FALL MIDTERM)', examTitle));
    body.appendChild(field('상태 라벨 (예: 현재 준비 중)', statusLabel));
    body.appendChild(field('준비상태 섹션 리드', prepHead));
    body.appendChild(field('이번 시험 특징 (여러 줄 가능)', feat));
    var r = el('div', 'mtx-row');
    r.appendChild(field('표시 순서', sort));
    r.appendChild(pub);
    body.appendChild(r);
    var saveBtn = el('button', 'mtx-btn', '학교 정보 저장');
    saveBtn.type = 'button';
    saveBtn.addEventListener('click', function () {
      apply(req('PUT', '/school-collections/' + c.id, {
        schoolName: c.schoolName, grade: c.grade, examTitle: str(examTitle.value),
        statusLabel: str(statusLabel.value), prepHeadline: str(prepHead.value),
        examFeatureNote: str(feat.value), sortOrder: num(sort.value), published: pub.control.checked
      }), '저장했습니다.');
    });
    var delBtn = el('button', 'mtx-btn danger', '학교 삭제');
    delBtn.type = 'button';
    delBtn.addEventListener('click', function () {
      if (!window.confirm(c.schoolName + ' ' + c.grade + '학년 컬렉션과 하위 자료·로그를 모두 삭제합니다. 계속할까요?')) return;
      apply(req('DELETE', '/school-collections/' + c.id), '삭제했습니다.');
    });
    var br = el('div', 'mtx-btn-row'); br.appendChild(saveBtn); br.appendChild(delBtn);
    body.appendChild(br);

    // 브리핑
    body.appendChild(el('h3', null, '박정민 원장 브리핑 영상'));
    var vidOptions = ['']; var vidLabels = ['(연결 안 함)'];
    videos.forEach(function (vd) { vidOptions.push(String(vd.videoId)); vidLabels.push((vd.originalFilename || ('영상 #' + vd.videoId)) + ' · ' + (vd.availabilityStatus || vd.status || '')); });
    var vidSel = select(vidOptions, c.briefingVideoId || '', vidLabels);
    var bTitle = input('text', c.briefingTitle || '');
    var bDesc = textarea(c.briefingDescription || '');
    var bShoot = input('date', c.briefingShootDate || '');
    var bPubDate = input('date', c.briefingPublishDate || '');
    var bPub = checkbox('브리핑 공개', c.briefingPublished);
    body.appendChild(field('연결할 영상 (강사 영상 업로드에서 먼저 올린 뒤 선택)', vidSel));
    body.appendChild(field('브리핑 제목', bTitle));
    body.appendChild(field('브리핑 설명', bDesc));
    var br2 = el('div', 'mtx-row');
    br2.appendChild(field('촬영일', bShoot));
    br2.appendChild(field('게시일', bPubDate));
    body.appendChild(br2);
    body.appendChild(bPub);
    var bSave = el('button', 'mtx-btn', '브리핑 저장');
    bSave.type = 'button';
    bSave.addEventListener('click', function () {
      apply(req('PUT', '/school-collections/' + c.id + '/briefing', {
        briefingVideoId: vidSel.value ? Number(vidSel.value) : null,
        briefingTitle: str(bTitle.value), briefingDescription: str(bDesc.value),
        briefingShootDate: str(bShoot.value), briefingPublishDate: str(bPubDate.value),
        briefingPublished: bPub.control.checked
      }), '브리핑을 저장했습니다.');
    });
    body.appendChild(bSave);

    // 준비 상태
    body.appendChild(el('h3', null, '준비 상태 (NOW PREPARING)'));
    var prepWrap = el('div');
    var rows = (c.prepStatuses && c.prepStatuses.length ? c.prepStatuses : PREP_STEPS.map(function (k, i) {
      return { stepKey: k, label: defaultStepLabel(k), state: 'NOT_STARTED', sortOrder: i };
    }));
    var rowControls = [];
    rows.forEach(function (row) {
      var rw = el('div', 'mtx-prep-row');
      var lab = input('text', row.label); lab.placeholder = '단계 이름';
      var st = select(PREP_STATE, row.state, ['준비 예정', '진행 중', '완료']);
      var keyHidden = row.stepKey;
      rw.appendChild(lab);
      rw.appendChild(st);
      rw.appendChild(el('span', null, ''));
      prepWrap.appendChild(rw);
      rowControls.push({ key: keyHidden, lab: lab, st: st });
    });
    body.appendChild(prepWrap);
    var prepSave = el('button', 'mtx-btn', '준비 상태 저장');
    prepSave.type = 'button';
    prepSave.addEventListener('click', function () {
      var steps = rowControls.map(function (rc, i) {
        return { stepKey: rc.key, label: rc.lab.value.trim() || rc.key, state: rc.st.value, sortOrder: i };
      });
      apply(req('PUT', '/school-collections/' + c.id + '/prep-status', { steps: steps }), '준비 상태를 저장했습니다.');
    });
    body.appendChild(prepSave);

    // After Exam
    body.appendChild(el('h3', null, 'AFTER THE EXAM (시험 후 · Phase 1 입력만)'));
    var ae = c.afterExam || {};
    var aeFeat = textarea(ae.examFeature || '');
    var aePrep = textarea(ae.preparedNote || '');
    var aeConn = textarea(ae.connectionNote || '');
    var aeNext = textarea(ae.nextImprovement || '');
    var aePub = checkbox('공개(Phase 2 페이지에서 노출)', ae.published);
    body.appendChild(field('실제 시험 특징', aeFeat));
    body.appendChild(field('에듀홀릭에서 준비했던 부분', aePrep));
    body.appendChild(field('제작자료와 실제 시험의 연결', aeConn));
    body.appendChild(field('다음 시험 개선점', aeNext));
    body.appendChild(aePub);
    var aeSave = el('button', 'mtx-btn ghost', 'After Exam 저장');
    aeSave.type = 'button';
    aeSave.addEventListener('click', function () {
      apply(req('PUT', '/school-collections/' + c.id + '/after-exam', {
        examFeature: str(aeFeat.value), preparedNote: str(aePrep.value),
        connectionNote: str(aeConn.value), nextImprovement: str(aeNext.value), published: aePub.control.checked
      }), 'After Exam 을 저장했습니다.');
    });
    body.appendChild(aeSave);

    d.appendChild(body);
    return d;
  }

  function defaultStepLabel(k) {
    return { SCHOOL_PRINT: '학교 프린트 분석', ANALYSIS: '본문 분석', WORKSHEET: '학습지 제작',
      VARIANT: '변형문제', FINAL_REVIEW: '시험 직전자료' }[k] || k;
  }

  // ── 자료 Showcase 탭 ───────────────────────────────────────────────
  function collectionOptions() {
    var opts = ['']; var labels = ['(캠페인 공용)'];
    (view.schoolCollections || []).forEach(function (c) {
      opts.push(String(c.id)); labels.push(c.schoolName + ' ' + c.grade + '학년');
    });
    return { opts: opts, labels: labels };
  }
  function wikiOptions() {
    var opts = ['']; var labels = ['(원본 연결 안 함)'];
    wikiMaterials.forEach(function (w) {
      opts.push(String(w.wikiAnalysisMaterialId));
      labels.push((w.schoolName ? w.schoolName + ' · ' : '') + w.title);
    });
    return { opts: opts, labels: labels };
  }

  function renderMaterials() {
    var host = document.querySelector('[data-mtx-materials]');
    host.innerHTML = '';
    if (!view || !view.exists) { host.appendChild(el('p', 'mtx-empty', '먼저 캠페인을 만들어 주세요.')); return; }
    host.appendChild(el('p', 'mtx-empty', '자료 원본은 BuildIt Wiki 분석자료를 참조합니다. 공개 범위(PRIVATE/PREVIEW/PUBLIC/MEMBER)는 서버가 강제합니다.'));

    (view.materials || []).forEach(function (m) { host.appendChild(materialCard(m)); });

    var addCard = el('div', 'mtx-card');
    addCard.appendChild(el('h3', null, '새 자료 추가'));
    addCard.appendChild(materialForm({}, function (payload) {
      apply(req('POST', '/materials', payload), '자료를 추가했습니다.');
    }, '추가'));
    host.appendChild(addCard);
  }

  function materialCard(m) {
    var card = el('div', 'mtx-card');
    var h = el('h3', null, m.displayTitle || '(제목 없음)');
    h.appendChild(el('span', 'mtx-pill ' + (m.published ? 'on' : 'off'), m.published ? '공개' : '비공개'));
    h.appendChild(el('span', 'mtx-pill', m.visibility));
    card.appendChild(h);
    if (m.sourceLabel) card.appendChild(el('p', 'mtx-sub', '원본: ' + m.sourceLabel));
    card.appendChild(materialForm(m, function (payload) {
      apply(req('PUT', '/materials/' + m.id, payload), '저장했습니다.');
    }, '저장', function () {
      if (!window.confirm('이 자료를 삭제할까요?')) return;
      apply(req('DELETE', '/materials/' + m.id), '삭제했습니다.');
    }));
    return card;
  }

  function materialForm(m, onSave, saveLabel, onDelete) {
    var box = el('div');
    var co = collectionOptions();
    var colSel = select(co.opts, m.schoolCollectionId || '', co.labels);
    var catSel = select(CATS, m.category || 'OTHER');
    var title = input('text', m.displayTitle || '');
    var blurb = textarea(m.blurb || '');
    var comment = textarea(m.principalComment || '');
    var checklist = textarea(m.checklist || '');
    var wo = wikiOptions();
    var wikiSel = select(wo.opts,
      (m.sourceType === 'WIKI_ANALYSIS' ? m.sourceMaterialId : '') || '', wo.labels);
    var visSel = select(VIS, m.visibility || 'PRIVATE');
    var sort = input('number', m.sortOrder || 0);
    var pub = checkbox('공개', m.published);

    box.appendChild(field('소속 학교', colSel));
    box.appendChild(field('분류', catSel));
    box.appendChild(field('자료 제목 (학부모 표시)', title));
    box.appendChild(field('한 줄 설명', blurb));
    box.appendChild(field('박정민 원장 COMMENT', comment));
    box.appendChild(field('체크 항목 (한 줄에 하나)', checklist));
    box.appendChild(field('원본 분석자료 (BuildIt Wiki)', wikiSel));
    var r = el('div', 'mtx-row');
    r.appendChild(field('공개 범위', visSel));
    r.appendChild(field('표시 순서', sort));
    box.appendChild(r);
    box.appendChild(pub);

    var saveBtn = el('button', 'mtx-btn', saveLabel);
    saveBtn.type = 'button';
    saveBtn.addEventListener('click', function () {
      onSave({
        schoolCollectionId: colSel.value ? Number(colSel.value) : null,
        category: catSel.value, displayTitle: title.value.trim(), blurb: str(blurb.value),
        principalComment: str(comment.value), checklist: str(checklist.value),
        sourceType: wikiSel.value ? 'WIKI_ANALYSIS' : 'NONE',
        sourceMaterialId: wikiSel.value ? Number(wikiSel.value) : null,
        visibility: visSel.value, sortOrder: num(sort.value), published: pub.control.checked
      });
    });
    var row = el('div', 'mtx-btn-row');
    row.appendChild(saveBtn);
    if (onDelete) { var d = el('button', 'mtx-btn danger', '삭제'); d.type = 'button'; d.addEventListener('click', onDelete); row.appendChild(d); }
    box.appendChild(row);
    return box;
  }

  // ── 업데이트 로그 탭 ───────────────────────────────────────────────
  function renderLogs() {
    var host = document.querySelector('[data-mtx-logs]');
    host.innerHTML = '';
    if (!view || !view.exists) { host.appendChild(el('p', 'mtx-empty', '먼저 캠페인을 만들어 주세요.')); return; }
    (view.updateLogs || []).forEach(function (l) { host.appendChild(logCard(l)); });
    var addCard = el('div', 'mtx-card');
    addCard.appendChild(el('h3', null, '새 업데이트 추가'));
    addCard.appendChild(logForm({}, function (p) { apply(req('POST', '/update-logs', p), '추가했습니다.'); }, '추가'));
    host.appendChild(addCard);
  }
  function logCard(l) {
    var card = el('div', 'mtx-card');
    var h = el('h3', null, l.headline || '(제목 없음)');
    h.appendChild(el('span', 'mtx-pill ' + (l.published ? 'on' : 'off'), l.published ? '공개' : '비공개'));
    card.appendChild(h);
    card.appendChild(logForm(l, function (p) { apply(req('PUT', '/update-logs/' + l.id, p), '저장했습니다.'); }, '저장',
      function () { if (window.confirm('삭제할까요?')) apply(req('DELETE', '/update-logs/' + l.id), '삭제했습니다.'); }));
    return card;
  }
  function logForm(l, onSave, saveLabel, onDelete) {
    var box = el('div');
    var co = collectionOptions();
    var colSel = select(co.opts, l.schoolCollectionId || '', co.labels);
    var date = input('date', l.logDate || '');
    var head = input('text', l.headline || '');
    var body = textarea(l.body || '');
    var sort = input('number', l.sortOrder || 0);
    var pub = checkbox('공개', l.published !== false);
    box.appendChild(field('학교 (없으면 캠페인 공용)', colSel));
    box.appendChild(field('날짜', date));
    box.appendChild(field('제목', head));
    box.appendChild(field('내용', body));
    box.appendChild(field('표시 순서', sort));
    box.appendChild(pub);
    var saveBtn = el('button', 'mtx-btn', saveLabel);
    saveBtn.type = 'button';
    saveBtn.addEventListener('click', function () {
      onSave({
        schoolCollectionId: colSel.value ? Number(colSel.value) : null, logDate: str(date.value),
        headline: head.value.trim(), body: str(body.value), sortOrder: num(sort.value), published: pub.control.checked
      });
    });
    var row = el('div', 'mtx-btn-row'); row.appendChild(saveBtn);
    if (onDelete) { var d = el('button', 'mtx-btn danger', '삭제'); d.type = 'button'; d.addEventListener('click', onDelete); row.appendChild(d); }
    box.appendChild(row);
    return box;
  }

  // ── Making-of 탭 ──────────────────────────────────────────────────
  function renderMaking() {
    var host = document.querySelector('[data-mtx-making]');
    host.innerHTML = '';
    if (!view || !view.exists) { host.appendChild(el('p', 'mtx-empty', '먼저 캠페인을 만들어 주세요.')); return; }
    host.appendChild(el('p', 'mtx-empty', 'Phase 1 은 입력만 저장합니다. 학부모 페이지(Swipe Gallery) 노출은 Phase 2 입니다.'));
    (view.makingOf || []).forEach(function (mo) { host.appendChild(makingCard(mo)); });
    var addCard = el('div', 'mtx-card');
    addCard.appendChild(el('h3', null, '새 Making-of 추가'));
    addCard.appendChild(makingForm({}, function (p) { apply(req('POST', '/making-of', p), '추가했습니다.'); }, '추가'));
    host.appendChild(addCard);
  }
  function makingCard(mo) {
    var card = el('div', 'mtx-card');
    card.appendChild(el('h3', null, mo.stage + (mo.caption ? ' · ' + mo.caption : '')));
    card.appendChild(makingForm(mo, function (p) { apply(req('PUT', '/making-of/' + mo.id, p), '저장했습니다.'); }, '저장',
      function () { if (window.confirm('삭제할까요?')) apply(req('DELETE', '/making-of/' + mo.id), '삭제했습니다.'); }));
    return card;
  }
  function makingForm(mo, onSave, saveLabel, onDelete) {
    var box = el('div');
    var co = collectionOptions();
    var colSel = select(co.opts, mo.schoolCollectionId || '', co.labels);
    var stageSel = select(MAKING_STAGES, mo.stage || 'SOURCE');
    var caption = input('text', mo.caption || '');
    var sort = input('number', mo.sortOrder || 0);
    var pub = checkbox('공개(Phase 2)', mo.published);
    box.appendChild(field('학교', colSel));
    box.appendChild(field('단계', stageSel));
    box.appendChild(field('설명', caption));
    box.appendChild(field('표시 순서', sort));
    box.appendChild(pub);
    var saveBtn = el('button', 'mtx-btn', saveLabel);
    saveBtn.type = 'button';
    saveBtn.addEventListener('click', function () {
      onSave({
        schoolCollectionId: colSel.value ? Number(colSel.value) : null, stage: stageSel.value,
        caption: str(caption.value), sortOrder: num(sort.value), published: pub.control.checked
      });
    });
    var row = el('div', 'mtx-btn-row'); row.appendChild(saveBtn);
    if (onDelete) { var d = el('button', 'mtx-btn danger', '삭제'); d.type = 'button'; d.addEventListener('click', onDelete); row.appendChild(d); }
    box.appendChild(row);
    return box;
  }

  // ── Next Exam 탭 ─────────────────────────────────────────────────
  function renderNext() {
    var host = document.querySelector('[data-mtx-next]');
    host.innerHTML = '';
    if (!view || !view.exists) { host.appendChild(el('p', 'mtx-empty', '먼저 캠페인을 만들어 주세요.')); return; }
    var n = view.nextExam || {};
    var card = el('div', 'mtx-card');
    card.appendChild(el('h3', null, 'NEXT EXAM CTA'));
    card.appendChild(el('p', 'mtx-sub', '시험 종료 후 페이지 마지막을 다음 시험 상담으로 잇습니다(§31).'));
    var enabled = checkbox('활성화 (학부모 페이지에 노출)', n.enabled);
    var name = input('text', n.nextExamName || '');
    var head = input('text', n.headline || '');
    var desc = textarea(n.description || '');
    var cta = input('text', n.ctaLabel || '');
    var dest = input('text', n.ctaDestination || '');
    card.appendChild(enabled);
    card.appendChild(field('다음 시험명 (예: 2026 기말고사)', name));
    card.appendChild(field('headline', head));
    card.appendChild(field('설명', desc));
    card.appendChild(field('CTA 라벨', cta));
    card.appendChild(field('CTA 링크 (비우면 상담 홈)', dest));
    var saveBtn = el('button', 'mtx-btn', '저장');
    saveBtn.type = 'button';
    saveBtn.addEventListener('click', function () {
      apply(req('PUT', '/next-exam', {
        enabled: enabled.control.checked, nextExamName: str(name.value), headline: str(head.value),
        description: str(desc.value), ctaLabel: str(cta.value), ctaDestination: str(dest.value)
      }), '저장했습니다.');
    });
    card.appendChild(saveBtn);
    host.appendChild(card);
  }
})();
