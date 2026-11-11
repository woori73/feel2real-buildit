/*
 * 학원 홈(/dashboard) 화면. 서버(AcademyCraft, BuildIt 게이트웨이 경유)에서 읽은 값만 그리며 이 화면에서는 쓰지 않는다.
 * 쓰기(출결 저장·학생 수정·상담 작성)는 각 AcademyCraft 화면에서 기존 권한·승인 규칙 그대로 한다.
 * 규칙(순서·문장·주소)은 academy_home_view.js의 순수 함수가 맡는다.
 */
(function () {
  'use strict';
  var V = window.AcademyHomeView;
  var BASE = '/academycraft/api/rea';
  var ACADEMY_KEY = 'academycraft.academy'; // AcademyCraft 화면(auth.js)과 같은 키를 써서 학원 선택이 이어지게 한다.
  var generation = 0;
  var $ = function (id) { return document.getElementById(id); };

  function el(tag, text, cls) {
    var e = document.createElement(tag);
    if (text != null) e.textContent = text;
    if (cls) e.className = cls;
    return e;
  }

  function stored() { try { return sessionStorage.getItem(ACADEMY_KEY); } catch (_) { return null; } }
  function store(id) { try { if (id == null) sessionStorage.removeItem(ACADEMY_KEY); else sessionStorage.setItem(ACADEMY_KEY, String(id)); } catch (_) {} }

  /** GET → { status, data }. 네트워크 오류는 status 'error'. */
  function get(path) {
    return fetch(path, { credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json' } })
      .then(function (res) {
        var status = V.statusFromHttp(res.status);
        if (status !== 'ok') return { status: status, code: res.status, data: null };
        return res.json().then(function (data) { return { status: 'ok', data: data }; }, function () { return { status: 'error', data: null }; });
      }, function () { return { status: 'error', data: null }; });
  }

  function toLogin() { location.href = '/admin/login?returnUrl=' + encodeURIComponent('/dashboard'); }

  function showAlert(text) { $('ahAlert').hidden = !text; $('ahAlert').textContent = text || ''; }

  /** 학원 전환·첫 로딩: 이전 학원의 숫자·목록이 남지 않도록 먼저 모두 비운다. */
  function resetView() {
    showAlert('');
    $('ahStudentCount').textContent = V.studentCountText('loading');
    $('ahStudentCount').href = V.studentsUrl();
    $('ahBannerKind').textContent = '수업 확인 중';
    $('ahBannerTitle').textContent = '오늘 수업을 불러오고 있습니다';
    $('ahBannerMeta').textContent = '';
    $('ahBannerDesc').hidden = true;
    $('ahBannerAction').hidden = true;
    $('ahBanner').dataset.kind = 'loading';
    $('ahLessons').replaceChildren(el('li', null, 'ah-skel'), el('li', null, 'ah-skel'));
    $('ahLessonsNote').hidden = true;
    $('ahCare').replaceChildren(el('li', null, 'ah-skel'));
    $('ahCareNote').hidden = true;
    $('ahFocus').replaceChildren(el('p', '확인 중입니다.', 'ah-empty'));
    $('ahMain').setAttribute('aria-busy', 'true');
  }

  function renderAcademy(ctx) {
    var options = (ctx.academyOptions || []);
    var current = options.find(function (o) { return Number(o.id) === Number(ctx.academyId); });
    $('ahAcademyName').textContent = current ? current.name : ('학원 #' + ctx.academyId);
    document.title = (current ? current.name + ' · ' : '') + '학원 홈';
    var pick = $('ahAcademyPick');
    if (ctx.builditRole === 'ADMIN' && options.length > 1) {
      pick.replaceChildren();
      options.forEach(function (o) { var opt = el('option', o.name); opt.value = o.id; opt.selected = Number(o.id) === Number(ctx.academyId); pick.append(opt); });
      $('ahAcademyPickWrap').hidden = false;
      $('ahAcademyName').hidden = true;
    } else {
      $('ahAcademyPickWrap').hidden = true;
      $('ahAcademyName').hidden = false;
    }
  }

  function renderCount(result) {
    var link = $('ahStudentCount');
    link.textContent = V.studentCountText(result.status, result.data);
    link.title = result.status === 'ok' ? '재원(활성) 학생 전체. 학생 목록 기본 조회와 같은 기준입니다.' : '';
  }

  function lessonLine(l) {
    return [l.start + (l.end ? '~' + l.end : ''), l.room, l.teacherName ? l.teacherName + ' 선생님' : ''].filter(Boolean).join(' · ');
  }

  function renderBanner(h, date) {
    var banner = $('ahBanner'), action = $('ahBannerAction');
    banner.dataset.kind = h.kind;
    var l = h.lesson;
    if (h.kind === 'live' || h.kind === 'next') {
      $('ahBannerKind').textContent = h.kind === 'live' ? '지금 진행 중인 수업' : '다음 수업';
      $('ahBannerTitle').textContent = l.name;
      $('ahBannerMeta').textContent = lessonLine(l) + (l.studentCount != null ? ' · 수강생 ' + l.studentCount + '명' : '');
      $('ahBannerDesc').textContent = l.description ? '수업 내용 · ' + l.description : '';
      $('ahBannerDesc').hidden = !l.description;
      action.href = V.rosterUrl(l.classId, date);
      action.textContent = '출결부 열기';
      action.hidden = false;
      return;
    }
    if (h.kind === 'finished') {
      var last = h.today[h.today.length - 1];
      $('ahBannerKind').textContent = '오늘 수업 종료';
      $('ahBannerTitle').textContent = '오늘 수업 ' + h.today.length + '개가 모두 끝났습니다';
      action.href = V.rosterUrl(last.classId, date);
      action.textContent = '마지막 수업 출결부';
      action.hidden = false;
    } else {
      $('ahBannerKind').textContent = '오늘 수업 없음';
      $('ahBannerTitle').textContent = '오늘 등록된 수업이 없습니다';
      action.hidden = true;
    }
    $('ahBannerMeta').textContent = l ? '다음 등록 일정 · ' + V.dateLabel(h.date) + ' ' + l.start + ' ' + l.name : '반에 요일·시간이 등록되면 여기에 다음 수업이 보입니다.';
    $('ahBannerDesc').hidden = true;
  }

  var STATE_LABEL = { live: '진행 중', upcoming: '예정', done: '종료' };

  function renderLessons(h, rosters, date, unscheduled) {
    var list = $('ahLessons');
    list.replaceChildren();
    if (!h.today.length) list.append(el('li', '오늘은 등록된 수업이 없습니다.', 'ah-empty'));
    h.today.forEach(function (l) {
      var li = el('li', null, 'ah-lesson is-' + l.state);
      var time = el('span', l.start, 'ah-lesson-time');
      var body = el('div', null, 'ah-lesson-body');
      var head = el('div', null, 'ah-lesson-head');
      head.append(el('strong', l.name), el('span', STATE_LABEL[l.state], 'ah-chip'));
      body.append(head, el('span', lessonLine(l) + (l.studentCount != null ? ' · ' + l.studentCount + '명' : ''), 'ah-sub'));
      if (l.description) body.append(el('span', l.description, 'ah-sub ah-desc'));
      var r = rosters[l.classId];
      if (r && !r.error && l.state !== 'upcoming') {
        var missing = r.entries.filter(function (e) { return e.status === 'NONE'; }).length;
        body.append(el('span', missing ? '출결 미기록 ' + missing + '명' : '출결 기록 완료', missing ? 'ah-flag' : 'ah-ok'));
      } else if (r && r.error) {
        body.append(el('span', '출결부를 불러오지 못했습니다', 'ah-flag'));
      }
      var a = el('a', '출결부', 'ah-lesson-link');
      a.href = V.rosterUrl(l.classId, date);
      a.setAttribute('aria-label', l.name + ' ' + l.start + ' 출결부 열기');
      li.append(time, body, a);
      list.append(li);
    });
    var note = $('ahLessonsNote');
    note.hidden = !unscheduled;
    note.textContent = unscheduled ? '요일·시간이 등록되지 않은 반 ' + unscheduled + '개는 목록에 없습니다. 반/수업에서 시간을 등록하세요.' : '';
  }

  function renderCare(care, consultStatus) {
    var list = $('ahCare');
    list.replaceChildren();
    if (!care.items.length) list.append(el('li', '지금 챙길 출결 미기록·답변 대기 상담이 없습니다.', 'ah-empty'));
    care.items.forEach(function (it) {
      var li = el('li', null, 'ah-care is-' + it.kind);
      var a = el('a');
      a.href = it.href;
      a.append(el('strong', it.title), el('span', it.sub, 'ah-sub'));
      li.append(a);
      list.append(li);
    });
    var notes = [];
    if (care.hidden) notes.push('이 밖에 ' + care.hidden + '건이 더 있습니다.');
    if (consultStatus !== 'ok') notes.push(consultStatus === 'forbidden' ? '상담 목록 조회 권한이 없습니다.' : '상담 목록을 불러오지 못했습니다.');
    $('ahCareNote').hidden = !notes.length;
    $('ahCareNote').textContent = notes.join(' ');
  }

  function renderFocus(result, isAdmin) {
    var box = $('ahFocus');
    box.replaceChildren();
    if (!isAdmin) { box.append(el('p', '이 계정에 등록된 중점 업무가 없습니다.', 'ah-empty')); return; }
    if (result.status !== 'ok') { box.append(el('p', '중점 업무를 불러오지 못했습니다.', 'ah-empty')); return; }
    var task = V.focusTask(result.data);
    if (!task) {
      box.append(el('p', '등록된 중점 업무가 없습니다. 학원이사 노트에서 제목을 ‘중요’로 시작해 등록하면 여기에 보입니다.', 'ah-empty'));
      var add = el('a', '학원이사 노트 열기', 'ah-text-link'); add.href = '/admin/academy-move'; box.append(add);
      return;
    }
    box.append(el('strong', task.title, 'ah-focus-title'));
    if (task.category) box.append(el('span', task.category, 'ah-chip'));
    if (task.body) {
      var d = el('details', null, 'ah-focus-body');
      d.append(el('summary', '업무 본문 읽기'), el('p', task.body));
      box.append(d);
    }
    var actions = el('div', null, 'ah-focus-actions');
    var open = el('a', '업무 열기', 'ah-text-link'); open.href = '/admin/academy-move'; actions.append(open);
    if (task.file) { var f = el('a', '관련 자료 · ' + task.file.name, 'ah-text-link ah-file'); f.href = task.file.href; actions.append(f); }
    box.append(actions);
  }

  async function loadAcademy(ctx, user, g) {
    var id = encodeURIComponent(ctx.academyId);
    var clock = V.seoulClock(new Date());
    $('ahDate').textContent = V.dateLabel(clock.date);
    var isAdmin = user.role === 'ADMIN';
    var results = await Promise.all([
      get(BASE + '/' + id + '/dashboard/stats'),
      get(BASE + '/' + id + '/classes'),
      get(BASE + '/' + id + '/consultations?type=INQUIRY&status=OPEN'),
      isAdmin ? get('/api/admin/academy-move/entries') : Promise.resolve({ status: 'skip' })
    ]);
    if (g !== generation) return;
    if (results.some(function (r) { return r.status === 'login'; })) { toLogin(); return; }
    var stats = results[0], classes = results[1], consults = results[2];
    renderCount(stats);
    var date = clock.date;
    if (classes.status !== 'ok') {
      $('ahBanner').dataset.kind = 'error';
      $('ahBannerKind').textContent = '수업 조회 실패';
      $('ahBannerTitle').textContent = classes.status === 'forbidden' ? '반·수업을 볼 권한이 없습니다' : '반·수업을 불러오지 못했습니다';
      $('ahLessons').replaceChildren(el('li', '반 목록을 불러오지 못했습니다. 잠시 후 새로고침하세요.', 'ah-empty'));
      $('ahCare').replaceChildren(el('li', '수업 정보를 확인한 뒤 표시합니다.', 'ah-empty'));
    } else {
      var h = V.headline(classes.data, clock);
      renderBanner(h, date);
      var rosters = {};
      await Promise.all(h.today.map(function (l) {
        return get(BASE + '/' + id + '/attendance/roster?classId=' + encodeURIComponent(l.classId) + '&date=' + encodeURIComponent(date))
          .then(function (r) { rosters[l.classId] = r.status === 'ok' && r.data && Array.isArray(r.data.entries) ? r.data : { error: true }; });
      }));
      if (g !== generation) return;
      // 반 목록의 studentCount는 비활성 학생 배정까지 세므로, 출결부(활성 수강생)를 읽은 수업은 그 인원을 쓴다.
      h.today.forEach(function (l) { var r = rosters[l.classId]; if (r && !r.error) l.studentCount = r.entries.length; });
      if (h.lesson) { var same = h.today.find(function (l) { return l.classId === h.lesson.classId; }); if (same && h.date === date) h.lesson.studentCount = same.studentCount; }
      renderBanner(h, date);
      renderLessons(h, rosters, date, V.unscheduledCount(classes.data));
      renderCare(V.careItems(h.today, rosters, consults.status === 'ok' ? consults.data : [], date, 5), consults.status);
    }
    renderFocus(results[3], isAdmin);
    $('ahMain').setAttribute('aria-busy', 'false');
  }

  async function context(academyId) {
    var q = academyId ? '?academyId=' + encodeURIComponent(academyId) : '';
    return get(BASE + '/integration/context' + q);
  }

  async function start(user, academyId) {
    var g = ++generation;
    resetView();
    var c = await context(academyId);
    if (c.status === 'forbidden' && academyId) { store(null); c = await context(null); }
    if (g !== generation) return;
    if (c.status === 'login') { toLogin(); return; }
    if (c.status !== 'ok' || !c.data || !c.data.academyId) {
      $('ahAcademyName').textContent = '학원 연결 필요';
      $('ahStudentCount').textContent = V.studentCountText(c.status === 'forbidden' ? 'unlinked' : 'error');
      $('ahBanner').dataset.kind = 'error';
      $('ahBannerKind').textContent = c.status === 'forbidden' ? '학원 연결 필요' : '연결 확인 실패';
      $('ahBannerTitle').textContent = c.status === 'forbidden' ? '이 계정에 연결된 학원이 없습니다' : '학원 서비스에 연결하지 못했습니다';
      $('ahBannerMeta').textContent = c.status === 'forbidden' ? '관리자에게 AcademyCraft 학원 연결을 요청하세요.' : '잠시 후 새로고침하세요.';
      $('ahLessons').replaceChildren(el('li', '학원 연결 후 표시됩니다.', 'ah-empty'));
      $('ahCare').replaceChildren(el('li', '학원 연결 후 표시됩니다.', 'ah-empty'));
      renderFocus({ status: 'skip' }, false);
      $('ahMain').setAttribute('aria-busy', 'false');
      return;
    }
    if (c.data.role === 'PARENT') { showAlert('학부모·학생 계정에는 학원 홈을 표시하지 않습니다.'); return; }
    store(c.data.academyId);
    renderAcademy(c.data);
    await loadAcademy(c.data, user, g);
  }

  async function init() {
    var user;
    try { user = await ensureCurrentUser(); } catch (_) { user = null; }
    if (!user || (user.role !== 'ADMIN' && user.role !== 'TUTOR')) { toLogin(); return; }
    $('ahAcademyPick').addEventListener('change', function () {
      var id = $('ahAcademyPick').value;
      store(id);
      start(user, id).catch(function () { showAlert('학원 정보를 불러오지 못했습니다.'); });
    });
    start(user, stored()).catch(function () { showAlert('학원 정보를 불러오지 못했습니다.'); });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
