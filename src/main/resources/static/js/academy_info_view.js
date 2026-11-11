/**
 * 정보 화면 표시 규칙(순수 함수). 서버 응답을 바꾸지 않고 문장·순서만 만든다.
 * 브라우저 window.AcademyInfoView, 테스트 module.exports.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.AcademyInfoView = api;
})(typeof self !== 'undefined' ? self : this, function () {
  const STATUS_TEXT = {
    READ: '원문',
    EMPTY: '읽을 글자 없음',
    NEEDS_OCR: '글자 인식 필요',
    UNSUPPORTED: '글로 읽을 수 없는 형식',
    FAILED: '원문 읽기 실패'
  };
  const KIND_TEXT = { ROSTER: '학생명단', MOVE: '학원 업무', UNLINKED: '연결 전 업로드' };
  const ROW_TEXT = { EXISTING: '기존(연결됨)', NEW: '추가 후보', HOLD: '연결 보류', EXCLUDED: '제외' };

  function size(bytes) {
    const n = Number(bytes);
    if (!Number.isFinite(n) || n < 0) return '';
    if (n < 1024) return n + 'B';
    if (n < 1024 * 1024) return (n / 1024).toFixed(1) + 'KB';
    return (n / 1024 / 1024).toFixed(1) + 'MB';
  }

  /** 본문으로 먼저 읽을 파일: 학생명단은 원본 Word, 그 밖은 첫 파일. */
  function primaryFile(item) {
    const files = (item && item.files) || [];
    return files.find(f => f.role === '원본 Word') || files[0] || null;
  }

  /** 대조 합계 문장. 합이 원본 이름 수와 다르면 그대로 경고한다. */
  function reconcileSummary(report) {
    if (!report) return '';
    const sum = report.existing + report.added + report.hold + report.excluded;
    return '원본 기록 ' + report.sourceEntries + '건 · 서로 다른 이름 ' + report.sourceNames + '명 = 기존 ' + report.existing +
      ' + 추가 후보 ' + report.added + ' + 연결 보류 ' + report.hold + ' + 제외 ' + report.excluded +
      (sum === report.sourceNames ? ' (합계 일치)' : ' (합계 불일치 ' + sum + '명 — 다시 읽기 필요)');
  }

  /** 행 묶음: 사람이 해결할 보류를 먼저, 그다음 추가 후보·기존·제외. */
  function groupRows(rows) {
    const order = ['HOLD', 'NEW', 'EXISTING', 'EXCLUDED'];
    const groups = {};
    (rows || []).forEach(r => { (groups[r.status] = groups[r.status] || []).push(r); });
    return order.filter(k => groups[k]).map(k => ({ status: k, label: ROW_TEXT[k], rows: groups[k] }));
  }

  /** 스캔 PDF 확인용 페이지 이미지 주소(최대 limit장). */
  function pageImages(read, limit) {
    if (!read || read.extension !== 'pdf' || !read.preview) return [];
    const total = Math.min(Number(read.result && read.result.pageCount) || 1, limit || 5);
    const out = [];
    for (let i = 1; i <= total; i++) out.push(read.preview + '&page=' + i);
    return out;
  }

  return { STATUS_TEXT, KIND_TEXT, ROW_TEXT, size, primaryFile, reconcileSummary, groupRows, pageImages };
});
