/**
 * 학생부 화면(/easy/records). 관리자 전용 — 권한은 서버(/api/admin/**)가 최종 결정.
 * ?businessId=&studentId=&grading= 로 열면(AI가 준 링크) 그 학생·채점 기록을 바로 띄우고 사진 올리기 칸으로 이동한다.
 */
(function () {
  'use strict';
  const BASE = '/api/admin/easy/records';
  const V = window.RecordsView;
  const $ = id => document.getElementById(id);
  let focusGrading = '';
  let editingLesson = null;
  let editingGrading = null;

  function el(tag, cls, text) {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text !== undefined && text !== null) n.textContent = text;
    return n;
  }
  function bid() { return $('business').value; }
  function sid() { return $('student').value; }
  function todayKst() { return new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10); }
  function message(text, isError) {
    const n = $('msg');
    n.textContent = text || '';
    n.className = 'msg' + (isError ? ' err' : '');
    n.style.display = text ? 'block' : 'none';
  }

  async function call(method, path, body, isForm) {
    const opts = { method, headers: {} };
    if (body !== undefined) {
      if (isForm) opts.body = body;
      else { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body); }
    }
    const sep = path.includes('?') ? '&' : '?';
    const res = await fetchAuth(BASE + path + sep + 'businessId=' + encodeURIComponent(bid()), opts);
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = null; }
    if (!res.ok) throw new Error((data && (data.message || data.error)) || text || ('요청 실패 (' + res.status + ')'));
    return data;
  }

  function button(label, onClick, cls) {
    const b = el('button', cls || 'btn-secondary', label);
    b.type = 'button';
    b.addEventListener('click', async () => {
      b.disabled = true;
      try { await onClick(); } catch (e) { message(e.message, true); } finally { b.disabled = false; }
    });
    return b;
  }

  async function loadStudents() {
    const res = await fetchAuth('/api/admin/easy/attendance/student-overview?businessId=' + encodeURIComponent(bid()));
    const rows = res.ok ? await res.json() : [];
    const sel = $('student');
    sel.replaceChildren(new Option('학생을 고르세요', ''));
    rows.forEach(r => sel.add(new Option(r.name + (r.school ? ' · ' + r.school : '') + (r.grade ? ' · ' + r.grade + '학년' : ''), String(r.studentId))));
  }

  function renderGrading(g) {
    const box = el('div', 'rec' + (String(g.gradingId) === focusGrading ? ' focus' : ''));
    box.id = 'grading-' + g.gradingId;
    box.appendChild(el('h4', null, V.gradingHeadline(g) + (g.source === 'MCP' ? ' · AI 기록' : '')));
    if (V.statusText(g)) box.appendChild(el('p', 'badge', V.statusText(g)));
    if (g.wrongItems.length) {
      const ul = el('ul');
      g.wrongItems.forEach(w => ul.appendChild(el('li', null, V.wrongLine(w))));
      box.appendChild(ul);
    }
    if (g.feedback) box.appendChild(el('p', null, '피드백: ' + g.feedback));
    const photos = el('div', 'photos');
    g.photos.forEach(p => {
      const fig = el('figure');
      const url = BASE + '/photos/' + p.photoId + '?businessId=' + encodeURIComponent(bid());
      const a = el('a'); a.href = url; a.target = '_blank'; a.rel = 'noopener';
      const img = el('img'); img.src = url; img.alt = '채점 답지 사진'; a.appendChild(img);
      fig.appendChild(a);
      fig.appendChild(el('div', null, p.kindLabel));
      fig.appendChild(button('사진 지우기', async () => {
        if (!confirm('이 사진을 지울까요?')) return;
        await call('DELETE', '/photos/' + p.photoId); await loadBook();
      }));
      photos.appendChild(fig);
    });
    box.appendChild(photos);
    const btns = el('div', 'btns');
    const input = el('input'); input.type = 'file'; input.accept = 'image/jpeg,image/png,image/webp'; input.multiple = true;
    input.id = 'upload-' + g.gradingId; input.hidden = true;
    const kind = el('select');
    [['QUESTION', '문제지'], ['ANSWER_KEY', '정답지'], ['STUDENT', '학생 답안'], ['GRADED', '채점된 답지']]
      .forEach(([v, t]) => kind.add(new Option(t, v)));
    kind.value = g.status === 'PENDING' ? (g.photos.some(p => p.kind === 'QUESTION') ? 'ANSWER_KEY' : 'QUESTION') : 'GRADED';
    kind.style.width = 'auto';
    input.addEventListener('change', async () => {
      try {
        for (const file of input.files) {
          const form = new FormData(); form.append('file', file);
          await call('POST', '/gradings/' + g.gradingId + '/photos?kind=' + kind.value, form, true);
        }
        message('사진을 올렸습니다.');
        await loadBook();
      } catch (e) { message(e.message, true); }
    });
    const label = el('label', 'btn-primary', '사진 올리기');
    label.htmlFor = input.id;
    btns.append(kind, input, label, button('고치기', async () => startEditGrading(g)), button('기록 지우기', async () => {
      if (!confirm('이 채점 기록과 사진을 모두 지울까요? 되돌릴 수 없습니다.')) return;
      await call('DELETE', '/gradings/' + g.gradingId); await loadBook();
    }));
    box.appendChild(btns);
    return box;
  }

  function renderLesson(l) {
    const box = el('div', 'rec');
    box.appendChild(el('h4', null, V.day(l.lessonDate) + (l.source === 'MCP' ? ' · AI 기록' : '')));
    const ul = el('ul');
    V.lessonLines(l).forEach(t => ul.appendChild(el('li', null, t)));
    box.appendChild(ul);
    const btns = el('div', 'btns');
    btns.appendChild(button('고치기', async () => startEditLesson(l)));
    btns.appendChild(button('기록 지우기', async () => {
      if (!confirm('이 수업 기록을 지울까요? 연결된 채점 기록은 남습니다.')) return;
      await call('DELETE', '/lessons/' + l.lessonRecordId); await loadBook();
    }));
    box.appendChild(btns);
    return box;
  }

  async function loadBook() {
    if (!sid()) { ['summaryCard', 'weakCard', 'addCard', 'gradingCard', 'lessonCard'].forEach(id => { $(id).hidden = true; }); return; }
    const book = await call('GET', '/students/' + sid());
    $('studentTitle').textContent = book.name + (book.school ? ' · ' + book.school : '') + (book.grade ? ' · ' + book.grade + '학년' : '');
    $('topics').textContent = V.topicsText(book.wrongTopics);
    const att = $('attendance'); att.replaceChildren();
    if (!book.attendance.length) att.appendChild(el('span', 'muted', '출결 기록 없음'));
    book.attendance.slice(0, 10).forEach(e => att.appendChild(el('span', 'ev', V.day(e.occurredAt) + ' ' + e.label)));
    const gs = $('gradings'); gs.replaceChildren();
    if (!book.gradings.length) gs.appendChild(el('p', 'muted', '채점 기록이 없습니다.'));
    book.gradings.forEach(g => gs.appendChild(renderGrading(g)));
    const wk = $('weakness'); wk.replaceChildren();
    if (!book.weakness) wk.appendChild(el('p', 'muted', '아직 약점 정리가 없습니다. 채점 뒤 Claude가 정리해 남깁니다.'));
    else {
      wk.appendChild(el('p', 'muted', (book.weakness.createdAt ? V.day(book.weakness.createdAt) + ' · ' : '') + (book.weakness.source === 'MCP' ? 'Claude 정리' : '직접 작성')));
      wk.appendChild(el('p', null, book.weakness.summary));
      const ul = el('ul', 'weak');
      book.weakness.topics.forEach(t => ul.appendChild(el('li', null, V.weaknessLine(t))));
      wk.appendChild(ul);
      if (book.weaknessHistory.length) {
        const d = el('details'); d.appendChild(el('summary', null, '이전 정리 ' + book.weaknessHistory.length + '건'));
        book.weaknessHistory.forEach(h => d.appendChild(el('p', 'muted', (h.createdAt ? V.day(h.createdAt) + ' · ' : '') + h.summary)));
        wk.appendChild(d);
      }
    }
    const ls = $('lessons'); ls.replaceChildren();
    if (!book.lessons.length) ls.appendChild(el('p', 'muted', '수업 기록이 없습니다.'));
    book.lessons.forEach(l => ls.appendChild(renderLesson(l)));
    ['summaryCard', 'weakCard', 'addCard', 'gradingCard', 'lessonCard'].forEach(id => { $(id).hidden = false; });
    if (focusGrading) {
      const target = $('grading-' + focusGrading);
      focusGrading = '';
      if (target) {
        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
        message('요청한 채점 기록입니다.');
      }
    }
  }

  function openForm(id) {
    const d = $(id).closest('details');
    d.open = true;
    d.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function startEditLesson(l) {
    editingLesson = l.lessonRecordId;
    $('lDate').value = l.lessonDate; $('lRange').value = l.lessonRange || ''; $('lCheck').value = l.homeworkCheck || '';
    $('lDone').value = l.homeworkDoneNote || ''; $('lNext').value = l.nextHomework || ''; $('lNote').value = l.teacherNote || '';
    $('lSave').textContent = '수정 저장';
    openForm('lSave');
  }

  function startEditGrading(g) {
    editingGrading = { id: g.gradingId, lessonRecordId: g.lessonRecordId };
    $('gDate').value = g.gradedOn; $('gArea').value = g.subjectArea || ''; $('gMaterial').value = g.material || '';
    $('gTotal').value = g.totalCount == null ? '' : g.totalCount; $('gCorrect').value = g.correctCount == null ? '' : g.correctCount;
    $('gWrong').value = g.wrongItems.map(w => [w.no || '', w.topic || '', w.reason || ''].join(', ')).join('\n');
    $('gFeedback').value = g.feedback || '';
    $('gSave').textContent = '수정 저장';
    openForm('gSave');
  }

  async function saveLesson() {
    const body = {
      lessonDate: $('lDate').value, lessonRange: $('lRange').value, homeworkCheck: $('lCheck').value || null,
      homeworkDoneNote: $('lDone').value, nextHomework: $('lNext').value, teacherNote: $('lNote').value
    };
    if (editingLesson) await call('PUT', '/lessons/' + editingLesson, body);
    else await call('POST', '/students/' + sid() + '/lessons', body);
    message(editingLesson ? '수업 기록을 고쳤습니다.' : '수업 기록을 저장했습니다.');
    editingLesson = null;
    $('lSave').textContent = '수업 기록 저장';
    ['lRange', 'lDone', 'lNext', 'lNote', 'lCheck'].forEach(id => { $(id).value = ''; });
    await loadBook();
  }

  async function saveGrading() {
    const num = id => ($(id).value === '' ? null : Number($(id).value));
    const body = {
      gradedOn: $('gDate').value, subjectArea: $('gArea').value, material: $('gMaterial').value,
      totalCount: num('gTotal'), correctCount: num('gCorrect'), wrongItems: V.parseWrongText($('gWrong').value),
      feedback: $('gFeedback').value, status: 'GRADED'
    };
    if (editingGrading) {
      body.lessonRecordId = editingGrading.lessonRecordId;
      await call('PUT', '/gradings/' + editingGrading.id, body);
    } else {
      await call('POST', '/students/' + sid() + '/gradings', body);
    }
    message(editingGrading ? '채점 기록을 고쳤습니다.' : '채점 기록을 저장했습니다. 아래에서 답지 사진을 올릴 수 있습니다.');
    editingGrading = null;
    $('gSave').textContent = '채점 기록 저장';
    ['gArea', 'gMaterial', 'gTotal', 'gCorrect', 'gWrong', 'gFeedback'].forEach(id => { $(id).value = ''; });
    await loadBook();
  }

  async function prepareGrading() {
    const g = await call('POST', '/students/' + sid() + '/gradings', {
      gradedOn: $('pDate').value, subjectArea: $('pArea').value, material: $('pMaterial').value, status: 'PENDING', wrongItems: []
    });
    ['pArea', 'pMaterial'].forEach(id => { $(id).value = ''; });
    focusGrading = String(g.gradingId);
    await loadBook();
    message('채점 준비를 만들었습니다. 사진 종류를 고르고 「사진 올리기」로 문제지·정답지·학생 답안을 올려 주세요.');
  }

  async function init() {
    await Easy.guardAdmin();
    const link = V.deepLink(location.search);
    focusGrading = link.grading;
    const businesses = await Easy.loadBusinesses();
    businesses.forEach(b => $('business').add(new Option(b.name, String(b.businessId))));
    if (link.businessId && businesses.some(b => String(b.businessId) === link.businessId)) $('business').value = link.businessId;
    $('lDate').value = todayKst();
    $('gDate').value = todayKst();
    $('pDate').value = todayKst();
    $('pSave').addEventListener('click', async () => { try { await prepareGrading(); } catch (e) { message(e.message, true); } });
    const guarded = fn => async () => { try { await fn(); } catch (e) { message(e.message, true); } };
    $('business').addEventListener('change', guarded(async () => { await loadStudents(); await loadBook(); }));
    $('student').addEventListener('change', guarded(loadBook));
    $('lSave').addEventListener('click', guarded(saveLesson));
    $('gSave').addEventListener('click', guarded(saveGrading));
    if (!businesses.length) return;
    await loadStudents();
    if (link.studentId && [...$('student').options].some(o => o.value === link.studentId)) $('student').value = link.studentId;
    await loadBook();
  }

  document.addEventListener('DOMContentLoaded', () => init().catch(e => message(e.message, true)));
})();
