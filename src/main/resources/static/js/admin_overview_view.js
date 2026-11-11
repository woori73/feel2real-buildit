/**
 * 관리자 현황(/dashboard 관리자 첫 화면) 표시 변환 — 순수 함수. 서버 값은 바꾸지 않고 사람이 읽는 글만 만든다.
 * 브라우저에서는 window.AdminOverviewView, 테스트(Node)에서는 module.exports.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.AdminOverviewView = api;
})(typeof self !== 'undefined' ? self : this, function () {
  const WEEK = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
  const LABEL = { MON: '월', TUE: '화', WED: '수', THU: '목', FRI: '금', SAT: '토', SUN: '일' };

  /** AI 의논 예시(누르면 입력칸에 채움. 자동 전송하지 않음). */
  const DISCUSS_EXAMPLES = [
    '이번 달 수납·지출을 보고 줄일 지출과 챙길 일을 같이 정리하자',
    '요일별 학생 수를 보고 반 편성·시간표를 어떻게 바꾸면 좋을지 의논하자',
    '이번 달 등원이 적은 학생을 어떻게 챙길지 의논하자'
  ];

  /** ["MON","WED"] → "월·수". 비면 "-". */
  function weekdayText(days) {
    const list = WEEK.filter(d => (days || []).includes(d));
    return list.length ? list.map(d => LABEL[d]).join('·') : '-';
  }

  /** 요일을 어디서 알았는지 짧은 말. */
  function sourceNote(source) {
    if (source === 'ATTENDANCE') return '최근 4주 등원';
    if (source === 'CLASS') return '반 요일';
    return '기록 없음';
  }

  /** "새봄중 2학년" / "새봄중" / "-". 학년 0·없음은 생략. */
  function schoolText(school, grade) {
    const s = String(school || '').trim();
    const g = Number(grade) > 0 ? Number(grade) + '학년' : '';
    return [s, g].filter(Boolean).join(' ') || '-';
  }

  /** "2026-11-06" → "11월 6일". 없으면 "-". */
  function dayText(value) {
    const m = String(value || '').match(/^\d{4}-(\d{2})-(\d{2})/);
    return m ? Number(m[1]) + '월 ' + Number(m[2]) + '일' : '-';
  }

  /** 요일별 학생 수 [{day:"MON", label:"월", count}] (월~일, 0명 포함). */
  function weekdayCounts(rows) {
    return WEEK.map(d => ({ day: d, label: LABEL[d],
      count: (rows || []).filter(r => (r.weekdays || []).includes(d)).length }));
  }

  /** 이름·학교 검색 + 요일 거르기. day가 비면 전체. */
  function filterRows(rows, query, day) {
    const q = String(query || '').trim().toLowerCase();
    return (rows || []).filter(r => {
      if (day && !(r.weekdays || []).includes(day)) return false;
      if (!q) return true;
      return (String(r.name || '') + ' ' + String(r.school || '')).toLowerCase().includes(q);
    });
  }

  /** 위젯 응답에서 숫자 카드 4장(수납·지출·미수금·원생 수). */
  function financeCards(widgets) {
    const fin = (widgets || []).find(w => w && w.id === 'easy-finance-summary');
    if (!fin) return { cards: [], error: null };
    if (fin.error) return { cards: [], error: '재무 숫자를 불러오지 못했습니다.' };
    const help = { '수납': '이번 달 들어온 돈', '지출': '이번 달 나간 돈', '미수금': '아직 안 받은 수강료', '원생 수': '장부 기준 재원생' };
    return { error: null, cards: ((fin.data && fin.data.cards) || []).map(c => ({
      label: c.label,
      value: Number(c.value || 0).toLocaleString('ko-KR') + (c.unit || ''),
      help: help[c.label] || ''
    })) };
  }

  /** Claude(MCP)에 붙여 넣을 첫 질문. */
  function claudePrompt(businessName) {
    return 'F2R의 read_admin_dashboard 도구로 ' + (businessName || '우리 학원') +
      ' 이번 달 재무와 학생별 수업 요일을 읽고, 이번 주에 내가 할 일을 우선순위로 같이 정리해 줘.';
  }

  return { WEEK, LABEL, DISCUSS_EXAMPLES, weekdayText, sourceNote, schoolText, dayText, weekdayCounts, filterRows, financeCards, claudePrompt };
});
