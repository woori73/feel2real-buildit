/**
 * 「바뀌는 입시」 글 표시 도우미(순수 함수). 브라우저는 window.AdmissionsArticlesView, 테스트(Node)는 module.exports.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.AdmissionsArticlesView = api;
})(typeof self !== 'undefined' ? self : this, function () {
  /** 글 주소(공유용, 글마다 따로). id가 양의 정수가 아니면 목록 주소. */
  function articleUrl(id) {
    return /^[1-9][0-9]{0,17}$/.test(String(id)) ? '/admissions/articles/' + id : '/admissions/articles';
  }

  /** '2026-11-13' → '2026년 11월 13일'. 형식이 아니면 빈 문자열. */
  function dateText(iso) {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(iso || ''));
    return m ? m[1] + '년 ' + Number(m[2]) + '월 ' + Number(m[3]) + '일' : '';
  }

  /** 입시상담 첫 화면 카드: 서버 순서(최신 게시일 먼저) 그대로 앞에서 limit개. */
  function homeItems(list, limit) {
    return (Array.isArray(list) ? list : []).slice(0, limit || 3).map(a => ({
      href: articleUrl(a.id),
      label: [a.series, a.episode].filter(Boolean).join(' '),
      title: String(a.title || ''),
      summary: String(a.summary || ''),
      date: dateText(a.publishedOn)
    }));
  }

  /** 관리자 폼 값 → 저장 요청 본문. 정렬 순서는 숫자, 빈 값은 0. */
  function formBody(v) {
    const order = Number(v.sortOrder);
    return {
      series: String(v.series || '').trim(), episode: String(v.episode || '').trim(), title: String(v.title || '').trim(),
      summary: String(v.summary || '').trim(), bodyHtml: String(v.bodyHtml || ''), publishedOn: String(v.publishedOn || '').trim(),
      visible: v.visible !== false, sortOrder: Number.isFinite(order) ? order : 0
    };
  }

  return { articleUrl, dateText, homeItems, formBody };
});
