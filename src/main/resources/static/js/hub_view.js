/**
 * 통합 대시보드(/admin/hub) 표시 변환(순수 함수). 서버 값은 바꾸지 않고, 사람이 바로 읽을 문장·주소만 만든다.
 * 브라우저에서는 window.HubView, 테스트(Node)에서는 module.exports로 쓴다.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.HubView = api;
})(typeof self !== 'undefined' ? self : this, function () {
  /** AI에게 부탁할 때 쓰는 예시(누르면 AI 업무 비서 입력칸에 채워진다. 자동 실행하지 않는다). */
  const ASK_EXAMPLES = [
    '이번 달 수납·지출·미수금을 정리해줘',
    '미수금 있는 학생과 안내 문자 초안을 만들어줘',
    '학생 등록해줘, 이름: , 학교: , 학년: ',
    '공지 작성해줘, 제목: 휴강 안내, 내용: '
  ];

  /** 숫자 카드 한 장의 한 줄 설명과 자세히 보기 주소. 모르는 이름이면 장부로 보낸다. */
  const CARD_HELP = {
    '수납': { help: '이번 달 들어온 수강료', href: '/easy' },
    '지출': { help: '이번 달 나간 돈', href: '/easy' },
    '미수금': { help: '아직 안 받은 수강료', href: '/easy' },
    '원생 수': { help: '지금 다니는 학생', href: '/easy' }
  };

  /** AI 업무 비서(/dashboard/assistant)로 가는 주소. 빈 글이면 비서 화면만 연다. /dashboard는 학원 홈이다. */
  function askUrl(text) {
    const t = String(text == null ? '' : text).trim().slice(0, 500);
    return t ? '/dashboard/assistant?ask=' + encodeURIComponent(t) : '/dashboard/assistant';
  }

  /** 주소의 ?ask= 값(없거나 비면 null). AI 업무 비서가 입력칸을 채울 때 쓴다. */
  function askFromSearch(search) {
    try {
      const v = new URLSearchParams(String(search || '')).get('ask');
      const t = v == null ? '' : v.trim().slice(0, 500);
      return t || null;
    } catch (e) {
      return null;
    }
  }

  function cardHelp(label) {
    return CARD_HELP[label] || { help: '', href: '/easy' };
  }

  /** "1,234,000원"처럼 단위를 붙인 큰 숫자. */
  function money(value, unit) {
    return Number(value || 0).toLocaleString('ko-KR') + (unit || '');
  }

  function findWidget(widgets, id) {
    return (widgets || []).find(w => w && w.id === id) || null;
  }

  /** 위젯 응답에서 숫자 카드 4장을 꺼낸다(설명·주소 포함). 오류면 error 문장만. */
  function summaryCards(widgets) {
    const fin = findWidget(widgets, 'easy-finance-summary');
    if (!fin) return { cards: [], error: null };
    if (fin.error) return { cards: [], error: '숫자를 불러오지 못했습니다. 새로고침을 눌러 주세요.' };
    const cards = ((fin.data && fin.data.cards) || []).map(c => {
      const h = cardHelp(c.label);
      return { label: c.label, value: money(c.value, c.unit), help: h.help, href: h.href, trend: c.trend || '' };
    });
    return { cards, error: null };
  }

  /** 목록 위젯을 앞에서 limit개만, 제목·설명 두 줄로. */
  function listPreview(widgets, id, limit) {
    const w = findWidget(widgets, id);
    if (!w) return null;
    const all = (w.data && w.data.items) || [];
    return {
      title: w.title,
      href: w.link || '/easy',
      error: w.error || null,
      total: all.length,
      items: all.slice(0, limit || 5).map(i => ({ title: i.title || '', sub: [i.subtitle, i.meta].filter(Boolean).join(' · ') })),
      emptyText: (w.data && w.data.emptyText) || '없습니다.'
    };
  }

  return { ASK_EXAMPLES, askUrl, askFromSearch, cardHelp, money, findWidget, summaryCards, listPreview };
});
