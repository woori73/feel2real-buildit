/* 내신위키 자료 연결(자료실 「내신위키 자료」 탭·제작 공장 요청 공용). 위키는 이 사이트 중계 주소(/problem-generator/wiki/api)로
   로그인한 사람 그대로 부른다 — 원장 계정의 위키 작업 공간 자료가 보인다. 화면 그리기는 WS.esc만 쓴다. */
(function (root) {
  'use strict';
  const WM = {};
  WM.API = '/problem-generator/wiki/api';

  /** 올릴 수 있는 종류 → 위키 material_type, 받는 파일 확장자(위키 서버 규칙과 같게). */
  WM.UPLOAD = {
    PAST_EXAM: { label: '기출문제', ext: ['.pdf', '.hwp', '.hwpx', '.docx', '.html', '.htm', '.md', '.jpg', '.jpeg', '.png'] },
    SCHOOL_PRINT: { label: '시험범위 자료 · 학교 프린트', ext: ['.pdf', '.hwpx', '.docx', '.html', '.htm', '.jpg', '.jpeg', '.png'] },
    EXAM_ANALYSIS: { label: '기출 분석', ext: ['.pdf', '.hwpx', '.docx', '.html', '.htm'] }
  };
  WM.KIND = { PAST_EXAM: '기출문제', SCHOOL_PRINT: '시험범위 · 학교 프린트', EXAM_ANALYSIS: '기출 분석', AI_WORK_LOG: 'AI 작업기록', final: '완성본(FINAL)', jikbo: '직보' };

  /** 시험 Context 한 줄 이름: 「신구중 2학년 2026 2학기 기말」 */
  WM.label = c => [c.school, c.grade, c.year, c.semester, c.exam].filter(v => v !== undefined && v !== null && String(v) !== '').join(' ');

  /** Context 목록을 학교·학년으로 묶는다: [{name, contexts:[…]}] (위키가 준 순서 그대로). */
  WM.group = contexts => {
    const map = new Map();
    (contexts || []).forEach(c => { const k = c.school + ' ' + c.grade; if (!map.has(k)) map.set(k, []); map.get(k).push(c); });
    return Array.from(map, ([name, list]) => ({ name, contexts: list }));
  };

  /** 한 Context의 자료 수 요약: 「기출 2 · 프린트 3 · 완성본 1」 */
  WM.counts = c => [['past_exam', '기출'], ['school_print', '프린트'], ['exam_analysis', '분석'], ['final', '완성본'], ['weekly', '주간 모의고사']]
    .filter(([k]) => c[k] > 0).map(([k, n]) => n + ' ' + c[k]).join(' · ');

  /** 자료 열기 주소. 위키 자료(exam-materials)는 원본이 있으면 원본, 없으면 HTML. 완성본은 HTML이 있으면 HTML. */
  WM.openUrl = item => {
    if (item.from === 'final') return WM.API + '/final-deliverables/' + item.id + (item.has_html ? '/html' : '/original');
    return WM.API + '/exam-materials/' + item.id + (item.original_name ? '/original' : '/html');
  };

  /** 쿼리 문자열(학교·학년·연도·학기·시험). */
  WM.query = c => ['school', 'grade', 'year', 'semester', 'exam'].map(k => k + '=' + encodeURIComponent(c[k] == null ? '' : c[k])).join('&');

  /** 올리기 전 확인: 빈 칸·확장자·크기. 문제가 없으면 ''. */
  WM.checkUpload = (v, file) => {
    for (const [k, name] of [['school', '학교'], ['grade', '학년'], ['year', '연도'], ['semester', '학기'], ['exam', '시험']]) {
      if (!String(v[k] || '').trim()) return name + '을(를) 적어 주세요.';
    }
    if (!/^\d{4}$/.test(String(v.year).trim())) return '연도는 2026처럼 네 자리로 적어 주세요.';
    const rule = WM.UPLOAD[v.type];
    if (!rule) return '자료 종류를 골라 주세요.';
    if (!file) return '올릴 파일을 골라 주세요.';
    const m = /\.[A-Za-z0-9]+$/.exec(file.name || '');
    if (!m || !rule.ext.includes(m[0].toLowerCase())) return rule.label + '는 ' + rule.ext.join(', ') + ' 파일만 올릴 수 있습니다.';
    if (file.size > 30 * 1024 * 1024) return '파일은 30MB까지 올릴 수 있습니다.';
    return '';
  };

  /** 위키 응답 오류 → 사람이 읽을 문장. */
  WM.error = async res => {
    try { const d = await res.json(); return (d && (d.detail && (d.detail.message || d.detail) || d.message)) || ('내신위키 응답 ' + res.status); }
    catch (e) { return '내신위키 응답 ' + res.status; }
  };

  WM.get = async path => {
    const res = await root.fetchAuth(WM.API + path, { redirectOnAuthError: false });
    if (!res.ok) throw new Error(await WM.error(res));
    return res.json();
  };

  /** 한 Context의 자료 전부: 위키 자료(학생 오답 제외) + 완성본. 최근 것부터. */
  WM.materials = async c => {
    const q = '?' + WM.query(c);
    const [sets, finals] = await Promise.all([WM.get('/exam-materials' + q), WM.get('/final-deliverables' + q).catch(() => [])]);
    const a = (sets || []).filter(m => m.core_type === 'school_print').map(m => Object.assign({ from: 'material', kind: (m.material_type || 'SCHOOL_PRINT').toUpperCase() }, m));
    const b = (finals || []).map(f => Object.assign({ from: 'final' }, f));
    return a.concat(b).sort((x, y) => String(y.created_at || '').localeCompare(String(x.created_at || '')));
  };

  /** 자료 올리기(위키 exam-materials, 학교 자료). 올린 자료 한 줄을 돌려준다. */
  WM.upload = async (v, file) => {
    const problem = WM.checkUpload(v, file);
    if (problem) throw new Error(problem);
    const fd = new FormData();
    fd.append('core_type', 'school_print');
    fd.append('material_type', v.type);
    ['school', 'grade', 'year', 'semester', 'exam'].forEach(k => fd.append(k, String(v[k]).trim()));
    fd.append('title', String(v.title || file.name).trim().slice(0, 150));
    fd.append('original', file);
    const res = await root.fetchAuth(WM.API + '/exam-materials', { method: 'POST', body: fd, redirectOnAuthError: false });
    if (!res.ok) throw new Error(await WM.error(res));
    return res.json();
  };

  /** 제작 공장 요청 칸에 넣을 글: 「내신위키 #12 2025 기말 기출(기출문제)」 */
  WM.ref = m => '내신위키 #' + (m.from === 'final' ? 'F' : '') + m.id + ' ' + (m.title || m.original_name || '') + '(' + (WM.KIND[m.kind] || WM.KIND[m.kind && m.kind.toLowerCase()] || m.kind || '자료') + ')';

  root.WM = WM;
  if (typeof module !== 'undefined' && module.exports) module.exports = WM;
})(typeof window !== 'undefined' ? window : globalThis);
