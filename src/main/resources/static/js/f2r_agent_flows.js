/*
 * F2R AI 비서 단계별 업무 안내 — 세븐·서수니가 필요한 내용을 한 번에 하나씩 묻고, 모은 값으로 기존 업무 API를 부른다.
 * - 새 업무 기능을 만들지 않는다. 학생 등록·출결 기록·상담 기록은 기존 /api/dashboard/assistant/v1/drafts(초안),
 *   출결 확인·공지 초안·현황은 기존 /conversation, 학습자료는 기존 화면 링크로 연결한다.
 * - 저장은 기존 초안 카드의 '승인하고 실행'을 눌렀을 때만 일어난다. 이 파일은 서버에 직접 쓰지 않는다.
 * - 홀로그램 업무 버튼([data-flow])을 누르거나, 대화창에 "학생 등록하고 싶어"처럼 세부 내용 없이 요청하면 시작한다.
 */
(function () {
  'use strict';

  var GRADES = ['초4', '초5', '초6', '중1', '중2', '중3', '고1', '고2', '고3'];
  var FLOWS = {
    student: {
      title: '학생 등록', operation: 'STUDENT_CREATE',
      steps: [
        { key: 'name', ask: '등록할 학생의 이름을 알려 주세요.', placeholder: '예: 김하늘', max: 20 },
        { key: 'grade', ask: '학년을 골라 주세요.', choices: GRADES.map(function (g) { return [g, g]; }), optional: true },
        { key: 'schoolName', ask: '학교 이름을 알려 주세요. 모르면 건너뛰어도 됩니다.', placeholder: '예: 새봄중', optional: true, max: 40 },
        { key: 'parentName', ask: '학부모 이름을 알려 주세요. 건너뛸 수 있습니다.', placeholder: '예: 김민정', optional: true, max: 20 },
        { key: 'parentPhone', ask: '학부모 연락처를 알려 주세요. 건너뛸 수 있습니다.', placeholder: '예: 010-1234-5678', optional: true, phone: true }
      ]
    },
    attendance: {
      title: '출결',
      steps: [
        { key: 'mode', ask: '출결을 확인할까요, 기록할까요?', choices: [['query', '출결 확인'], ['record', '출결 기록']] },
        { key: 'studentId', ask: '어느 학생인가요? 이름을 입력하거나 아래에서 고르세요.', pick: 'students', placeholder: '학생 이름' },
        { key: 'eventType', when: function (a) { return a.mode === 'record'; }, ask: '어떤 출결로 기록할까요?', choices: [['CHECK_IN', '등원'], ['CHECK_OUT', '하원'], ['ABSENT', '결석'], ['LATE', '지각'], ['EARLY_LEAVE', '조퇴']] },
        { key: 'note', when: function (a) { return a.mode === 'record'; }, ask: '메모가 있으면 적어 주세요. 없으면 건너뛰기를 누르세요.', optional: true, max: 200 }
      ]
    },
    consult: {
      title: '상담 기록', operation: 'CONSULT_CREATE',
      steps: [
        { key: 'studentId', ask: '어느 학생의 상담인가요? 이름을 입력하거나 아래에서 고르세요.', pick: 'students', placeholder: '학생 이름' },
        { key: 'title', ask: '상담 제목을 알려 주세요.', placeholder: '예: 2학기 학습 상담', max: 100 },
        { key: 'content', ask: '상담 내용을 적어 주세요.', placeholder: '상담 내용', max: 2000 }
      ]
    },
    notice: {
      title: '공지 초안',
      steps: [
        { key: 'title', ask: '공지 제목을 알려 주세요.', placeholder: '예: 추석 휴강 안내', max: 100 },
        { key: 'content', ask: '공지에 들어갈 내용을 적어 주세요.', placeholder: '공지 내용', max: 2000 }
      ]
    },
    materials: {
      title: '학습자료 확인',
      steps: [
        { key: 'kind', ask: '어떤 자료를 볼까요?', choices: [['study-materials', '학습자료'], ['exam-materials', '시험자료'], ['materials', '전체 자료']] }
      ]
    },
    overview: { title: '운영 현황', steps: [] }
  };

  var LABELS = { name: '이름', grade: '학년', schoolName: '학교', parentName: '학부모', parentPhone: '학부모 연락처', studentId: '학생', eventType: '출결', note: '메모', title: '제목', content: '내용', mode: '할 일', kind: '자료' };

  // ---- 순수 규칙 (단위 테스트 대상) ----

  /** 세부 내용 없이 업무만 말한 요청이면 단계 안내를 고른다. 이름·ID·제목 등이 이미 있으면 기존 대화 분석에 맡긴다. */
  function detectFlow(text) {
    var t = String(text || '').replace(/\s+/g, ' ').trim();
    if (!t || t.length > 60) return null;
    if (/삭제|승인|실행해/.test(t)) return null;
    var hasId = /(학생|상담|반)\s*(ID|아이디|#)\s*[:=]?\s*\d+/i.test(t);
    var hasField = /(이름|제목|내용|학교|학년)\s*[:=]/.test(t);
    if (hasId || hasField) return null;
    if (/정정/.test(t)) return null;
    if (/학생/.test(t) && /(등록|추가)/.test(t) && !/학생\s+[가-힣A-Za-z]{2,20}\s*(을|를)?\s*(등록|추가)/.test(t)) return 'student';
    if (/(출결|출석|결석|지각|조퇴|등원|하원)/.test(t)) return 'attendance';
    if (/상담/.test(t) && /(작성|기록|남기|쓰)/.test(t)) return 'consult';
    if (/공지/.test(t)) return 'notice';
    if (/(학습\s*자료|시험\s*자료|교재|자료\s*(확인|보기|보여))/.test(t)) return 'materials';
    return null;
  }

  /** 지금 물어볼 단계 번호. 조건(when)에 맞지 않거나 이미 답한 단계는 건너뛴다. 다 모았으면 -1. */
  function nextStep(flowId, answers) {
    var flow = FLOWS[flowId];
    if (!flow) return -1;
    for (var i = 0; i < flow.steps.length; i++) {
      var s = flow.steps[i];
      if (s.when && !s.when(answers)) continue;
      if (!Object.prototype.hasOwnProperty.call(answers, s.key)) return i;
    }
    return -1;
  }

  /** 입력값 확인. 건너뛰기는 optional 단계만, 선택지 단계는 선택지 값(또는 선택지 이름)만 받는다. */
  function acceptAnswer(step, raw) {
    var v = String(raw == null ? '' : raw).trim();
    if (!v) {
      if (step.optional) return { ok: true, skip: true };
      return { ok: false, error: '내용을 입력해 주세요.' };
    }
    if (step.choices) {
      for (var i = 0; i < step.choices.length; i++) {
        if (step.choices[i][0] === v || step.choices[i][1] === v) return { ok: true, value: step.choices[i][0], label: step.choices[i][1] };
      }
      return { ok: false, error: '아래 선택지 중에서 골라 주세요.' };
    }
    if (step.max && v.length > step.max) return { ok: false, error: step.max + '자 이하로 입력해 주세요.' };
    if (step.phone) {
      var digits = v.replace(/[\s.-]/g, '');
      if (!/^0\d{8,10}$/.test(digits)) return { ok: false, error: '연락처는 숫자로 입력해 주세요. 예: 010-1234-5678' };
      var phone = digits.replace(/^(\d{2,3})(\d{3,4})(\d{4})$/, '$1-$2-$3');
      return { ok: true, value: phone, label: phone };
    }
    return { ok: true, value: v, label: v };
  }

  /** 학생 목록에서 이름으로 찾기. 정확히 같은 이름이 하나면 그 학생, 아니면 포함하는 후보들. */
  function findStudents(list, query) {
    var q = String(query || '').trim();
    if (!q) return [];
    if (/^\d+$/.test(q)) return (list || []).filter(function (s) { return String(s.id) === q; });
    var exact = (list || []).filter(function (s) { return s.name === q; });
    if (exact.length) return exact;
    return (list || []).filter(function (s) { return String(s.name || '').indexOf(q) >= 0; });
  }

  function quote(v) {
    return '"' + String(v).replace(/"/g, '”') + '"';
  }

  /** 모은 값 → 기존 기능 호출 방법. draft: /drafts 초안, ask: /conversation 조회형 요청, link: 기존 화면. */
  function finish(flowId, a) {
    switch (flowId) {
      case 'student': {
        var input = { name: a.name };
        ['grade', 'schoolName', 'parentName', 'parentPhone'].forEach(function (k) { if (a[k]) input[k] = a[k]; });
        return { kind: 'draft', operation: 'STUDENT_CREATE', input: input };
      }
      case 'attendance':
        if (a.mode === 'query') return { kind: 'ask', message: '학생 ID ' + a.studentId + ' 출결 조회해줘' };
        return { kind: 'draft', operation: 'ATTENDANCE_RECORD', input: a.note ? { studentId: Number(a.studentId), eventType: a.eventType, note: a.note } : { studentId: Number(a.studentId), eventType: a.eventType } };
      case 'consult':
        return { kind: 'draft', operation: 'CONSULT_CREATE', input: { studentId: Number(a.studentId), title: a.title, content: a.content } };
      case 'notice':
        return { kind: 'ask', message: '공지 작성해줘, 제목: ' + quote(a.title) + ', 내용: ' + quote(a.content) };
      case 'materials':
        return { kind: 'link', href: '/tutor/conversation/materials?menu=' + encodeURIComponent(a.kind), label: '학습자료 화면 열기' };
      case 'overview':
        return { kind: 'ask', message: '학원 운영과 재무 현황 보여줘' };
      default:
        return null;
    }
  }

  var api = { FLOWS: FLOWS, detectFlow: detectFlow, nextStep: nextStep, acceptAnswer: acceptAnswer, findStudents: findStudents, finish: finish };
  if (typeof window !== 'undefined') window.f2rAgentFlows = api;
  if (typeof document === 'undefined' || !document.getElementById) return;

  // ---- 화면 ----
  var $ = function (id) { return document.getElementById(id); };
  var flow = null; // { id, answers, labels }
  var students = null; // { academy, list }
  var WELCOME_KEY = 'f2r.dashboard.welcome';
  var store = {
    get: function (k) { try { return localStorage.getItem(k); } catch (_) { return null; } },
    set: function (k, v) { try { localStorage.setItem(k, v); } catch (_) { /* 저장 불가 환경 */ } }
  };
  function ua() { return window.unifiedAssistant; }
  function agent() { return window.f2rAgentDashboard || {}; }

  function el(tag, text, cls) {
    var e = document.createElement(tag);
    if (text != null) e.textContent = text;
    if (cls) e.className = cls;
    return e;
  }

  function scroll() { var log = $('uaDrafts'); if (log) log.scrollTop = log.scrollHeight; }

  /** 비서 말풍선 + 선택 버튼. 질문은 캐릭터 말풍선에도 보여준다. */
  function say(text, choices) {
    var node = ua() && ua().bubble ? ua().bubble('assistant', text) : null;
    if (node && choices && choices.length) {
      var row = el('div', null, 'ua-choices');
      choices.forEach(function (c) {
        var b = el(c.href ? 'a' : 'button', c.label, c.primary ? 'ua-choice is-primary' : 'ua-choice');
        if (c.href) b.href = c.href; else b.type = 'button';
        if (c.onClick) b.addEventListener('click', function (e) {
          if (row.dataset.used === '1' && !c.repeatable) { e.preventDefault(); return; }
          if (!c.repeatable) { row.dataset.used = '1'; row.querySelectorAll('button').forEach(function (x) { x.disabled = true; }); b.classList.add('is-picked'); }
          c.onClick();
        });
        row.append(b);
      });
      node.append(row);
      scroll();
    }
    // 초안·응답을 기다리는 중이면 대화 감시(f2r_agent_dashboard.js)가 이 말풍선을 캐릭터 응답으로 읽어 준다.
    if (agent().guide && !(agent().awaiting && agent().awaiting())) agent().guide(text);
    return node;
  }

  function placeholder(text) {
    var input = $('uaCommand');
    if (!input) return;
    if (!input.dataset.defaultPlaceholder) input.dataset.defaultPlaceholder = input.placeholder;
    input.placeholder = text || input.dataset.defaultPlaceholder;
  }

  function stop(message) {
    flow = null;
    placeholder(null);
    document.body.removeAttribute('data-active-flow');
    document.querySelectorAll('button[data-flow]').forEach(function (b) { b.setAttribute('aria-pressed', 'false'); });
    if (message) say(message);
  }

  function start(id) {
    if (!FLOWS[id]) return;
    if (!ua() || !ua().ready()) { say('업무 비서를 준비하고 있습니다. 잠시 후 다시 눌러 주세요.'); return; }
    flow = { id: id, answers: {}, labels: {} };
    document.body.dataset.activeFlow = id;
    document.querySelectorAll('button[data-flow]').forEach(function (b) { b.setAttribute('aria-pressed', b.dataset.flow === id ? 'true' : 'false'); });
    if (!FLOWS[id].steps.length) { complete(); return; }
    ask();
  }

  function loadStudents() {
    var academy = ua().academy();
    if (students && students.academy === academy) return Promise.resolve(students.list);
    return ua().json('/catalog/students' + (academy ? '?academyId=' + academy : '')).then(function (list) {
      students = { academy: academy, list: (list || []).map(function (s) { return { id: s.id, name: s.name || s.title || '', school: s.schoolName || '' }; }) };
      return students.list;
    });
  }

  function studentChoice(s) {
    return { label: s.name + (s.school ? ' · ' + s.school : '') + ' · ID ' + s.id, onClick: function () { record('studentId', String(s.id), s.name); } };
  }

  function ask() {
    var i = nextStep(flow.id, flow.answers);
    if (i < 0) { complete(); return; }
    var step = FLOWS[flow.id].steps[i];
    placeholder(step.placeholder || '여기에 입력하세요');
    var extra = [];
    if (step.optional) extra.push({ label: '건너뛰기', onClick: function () { record(step.key, '', '건너뜀'); } });
    extra.push({ label: '그만두기', onClick: function () { stop('업무 안내를 멈췄습니다. 필요하면 언제든 다시 불러 주세요.'); } });
    if (step.choices) {
      say(step.ask, step.choices.map(function (c) { return { label: c[1], onClick: function () { record(step.key, c[0], c[1]); } }; }).concat(extra));
      return;
    }
    if (step.pick === 'students') {
      loadStudents().then(function (list) {
        if (!flow) return;
        if (!list.length) { stop('이 학원에 등록된 학생이 없습니다. 먼저 학생을 등록해 주세요.'); return; }
        say(step.ask, list.slice(0, 8).map(studentChoice).concat(extra));
      }).catch(function (e) {
        say('학생 목록을 불러오지 못했습니다: ' + (e && e.message ? e.message : '연결 오류') + ' 학생 이름 대신 학생 ID 숫자를 입력해도 됩니다.', extra);
      });
      return;
    }
    say(step.ask, extra);
  }

  function record(key, value, label) {
    if (!flow) return;
    if (value === '') { flow.answers[key] = ''; flow.labels[key] = '건너뜀'; }
    else { flow.answers[key] = value; flow.labels[key] = label; }
    if (ua()) ua().bubble('user', label || value);
    ask();
  }

  /** 대화창 입력을 현재 단계의 답으로 받는다. */
  function answer(text) {
    var i = nextStep(flow.id, flow.answers);
    if (i < 0) return;
    var step = FLOWS[flow.id].steps[i];
    if (/^(그만|취소|그만두기)$/.test(text.trim())) { ua().bubble('user', text); stop('업무 안내를 멈췄습니다.'); return; }
    if (step.pick === 'students') {
      var raw = text.trim().replace(/^ID\s*/i, '');
      ua().bubble('user', text);
      var list = students ? students.list : [];
      var found = findStudents(list, raw);
      if (!found.length && /^\d+$/.test(raw)) { flow.answers[step.key] = raw; flow.labels[step.key] = 'ID ' + raw; ask(); return; }
      if (found.length === 1) { flow.answers[step.key] = String(found[0].id); flow.labels[step.key] = found[0].name; ask(); return; }
      if (!found.length) { say('"' + raw + '" 학생을 찾지 못했습니다. 이름을 다시 입력하거나 아래에서 골라 주세요.', list.slice(0, 8).map(studentChoice)); return; }
      say('같은 이름의 학생이 여러 명입니다. 한 명을 골라 주세요.', found.slice(0, 8).map(studentChoice));
      return;
    }
    var r = acceptAnswer(step, text);
    ua().bubble('user', text);
    if (!r.ok) { say(r.error); return; }
    flow.answers[step.key] = r.skip ? '' : r.value;
    flow.labels[step.key] = r.skip ? '건너뜀' : r.label;
    ask();
  }

  function summary() {
    var lines = [];
    Object.keys(flow.labels).forEach(function (k) {
      if (flow.labels[k] && flow.labels[k] !== '건너뜀' && k !== 'mode') lines.push((LABELS[k] || k) + ': ' + flow.labels[k]);
    });
    return lines.join('\n');
  }

  function complete() {
    var current = flow;
    var plan = finish(current.id, current.answers);
    placeholder(null);
    if (!plan) { stop(); return; }
    var title = FLOWS[current.id].title;
    if (plan.kind === 'link') {
      var isTutor = ua().role() === 'TUTOR';
      stop();
      say(isTutor ? title + ' 화면을 열어 드릴게요. 기존 자료 화면에서 확인합니다.' : '학습자료 화면은 강사 계정 전용입니다. 강사 계정으로 들어가서 확인해 주세요.',
        isTutor ? [{ label: plan.label, href: plan.href, primary: true }] : []);
      return;
    }
    if (plan.kind === 'ask') {
      stop();
      if (agent().expect) agent().expect();
      ua().send(plan.message, { silent: true });
      return;
    }
    say(title + ' 내용을 확인해 주세요.\n' + summary() + '\n초안을 만들면 승인 버튼을 누를 때만 실제로 저장됩니다.', [
      { label: '초안 만들기', primary: true, onClick: function () { createDraft(current, plan); } },
      { label: '처음부터 다시', onClick: function () { start(current.id); } },
      { label: '그만두기', onClick: function () { stop('업무 안내를 멈췄습니다.'); } }
    ]);
  }

  function createDraft(current, plan) {
    stop();
    if (agent().think) agent().think();
    ua().createDraft(plan.operation, plan.input).then(function () {
      say('초안을 만들었습니다. 아래 초안에서 내용을 확인하고 "승인하고 실행"을 눌러 주세요. 승인 전에는 저장하지 않습니다.');
    }).catch(function (e) {
      say('초안을 만들지 못했습니다: ' + (e && e.message ? e.message : '연결 오류'), [
        { label: '다시 시도', primary: true, onClick: function () { flow = current; createDraft(current, plan); } },
        { label: '전체 대시보드에서 처리', onClick: function () { var b = document.querySelector('[data-view-btn="all"]'); if (b) b.click(); } }
      ]);
    });
  }

  // ---- 첫 방문 안내: 팝업 대신 비서가 대화로 묻는다. '나중에'를 고르면 다시 묻지 않는다. ----
  function welcome(force) {
    if (!force && store.get(WELCOME_KEY)) return;
    var name = agent().currentName ? agent().currentName() : '세븐';
    var done = function () { store.set(WELCOME_KEY, 'done'); };
    // 기록이 없을 때 넣는 기본 인사와 겹치지 않게 한다.
    var log = $('uaDrafts');
    if (log && log.children.length === 1 && log.firstElementChild.classList.contains('ua-assistant')) log.firstElementChild.remove();
    say('안녕하세요. 저는 ' + name + '입니다. 무엇을 만들고 싶으신가요?', [
      { label: 'AI 비서와 업무 시작', primary: true, onClick: function () { done(); say('홀로그램 업무 버튼을 누르거나 하고 싶은 일을 말씀해 주세요. 예: "학생 등록하고 싶어"'); } },
      { label: 'AI 영어교육', onClick: function () {
        done();
        say('영어교육 서비스를 열 수 있습니다.', [
          { label: '문법 문제은행', href: '/grammar-items' }, { label: '모의고사위키', href: '/wiki' }, { label: '내신 문제 제작', href: '/problem-generator' }
        ]);
      } },
      { label: 'AI AGENT 제작소', onClick: function () {
        done();
        var link = document.querySelector('a[data-studio-entry]');
        if (link && !link.hidden) say('AI AGENT 제작소로 이동할 수 있습니다.', [{ label: '제작소 열기', href: '/ai-agent-studio', primary: true }]);
        else say('AI AGENT 제작소는 관리자·운영 계정 전용입니다.');
      } },
      { label: '나중에', onClick: function () { done(); say('알겠습니다. 메뉴의 "처음 안내 다시 보기"에서 언제든 다시 볼 수 있습니다.'); } }
    ]);
  }

  function bind() {
    // 진행 중인 단계 안내가 있으면 대화창 입력을 그 단계의 답으로 받는다(기존 대화 전송보다 먼저).
    document.addEventListener('submit', function (e) {
      if (!e.target || e.target.id !== 'uaCommandForm') return;
      var input = $('uaCommand');
      var text = input ? input.value : '';
      if (!text.trim()) return;
      if (flow) {
        e.preventDefault(); e.stopImmediatePropagation();
        input.value = '';
        answer(text);
        return;
      }
      var id = detectFlow(text);
      if (id && ua() && ua().ready()) {
        e.preventDefault(); e.stopImmediatePropagation();
        input.value = '';
        ua().bubble('user', text);
        start(id);
      }
    }, true);
    document.addEventListener('click', function (e) {
      var b = e.target.closest && e.target.closest('button[data-flow]');
      if (b) { e.preventDefault(); start(b.dataset.flow); var input = $('uaCommand'); if (input && window.matchMedia && !window.matchMedia('(pointer: coarse)').matches) input.focus({ preventScroll: true }); }
      var again = e.target.closest && e.target.closest('[data-welcome-again]');
      if (again) { welcome(true); }
    });
    document.addEventListener('ua:ready', function () {
      var p = ua() && ua().progress ? ua().progress() : null;
      if (p && !p.completed) welcome(false);
      // 홈페이지에서 넘겨받은 요청은 입력창에만 채운다(자동 전송하지 않음).
      try {
        var pending = sessionStorage.getItem('f2r.pendingAsk');
        if (pending && $('uaCommand')) { $('uaCommand').value = pending.slice(0, 2000); sessionStorage.removeItem('f2r.pendingAsk'); }
      } catch (_) { /* 저장소 차단 환경 */ }
    });
    var academy = $('uaAcademy');
    if (academy) academy.addEventListener('change', function () { students = null; if (flow) stop(); });
  }

  api.start = start;
  api.welcome = welcome;
  api.active = function () { return flow ? flow.id : null; };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', bind); else bind();
})();
