/**
 * wemade-ownership.js
 * 「새 대주주는 누구인가」 지배구조 트리 동적 렌더링.
 * /api/wemade/ownership/tree 응답을 #wemade-ownership-root 아래에 렌더링한다.
 * 데이터가 없으면 조용히 물러난다 (정적 fragment가 공식 확인분을 보여준다).
 */
(function () {
  'use strict';

  var GRADE_LABEL = {
    A: '공식공시', B: '복수매체', C: '단일매체', D: '계산값', U: '미확인'
  };
  var GRADE_COLOR = {
    A: ['#e8f5e9', '#1b5e20', '#a5d6a7'],
    B: ['#e3f2fd', '#0d47a1', '#90caf9'],
    C: ['#fff3e0', '#e65100', '#ffcc80'],
    D: ['#f5f5f5', '#424242', '#bdbdbd'],
    U: ['#ffebee', '#b71c1c', '#ef9a9a']
  };

  function badge(grade) {
    var g = GRADE_LABEL[grade] ? grade : 'U';
    var c = GRADE_COLOR[g];
    return '<span style="display:inline-block;background:' + c[0] + ';color:' + c[1] +
      ';border:1px solid ' + c[2] + ';border-radius:4px;padding:0 5px;font-size:11px;">' +
      g + ' ' + GRADE_LABEL[g] + '</span>';
  }

  function esc(s) {
    if (s === null || s === undefined) return '';
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function renderTree(root, data) {
    var byKey = {};
    (data.nodes || []).forEach(function (n) { byKey[n.nodeKey] = n; });
    var children = data.children || {};

    function nodeBox(key) {
      var n = byKey[key];
      if (!n) return '<span style="color:#b71c1c;">(데이터 없음: ' + esc(key) + ')</span>';
      var pct = (n.stakePct !== null && n.stakePct !== undefined)
        ? ' <span style="color:#666;">' + esc(n.stakePct) + '%</span>' : '';
      return '<div style="border:1px solid #9e9e9e;border-radius:8px;padding:8px 10px;background:#fff;min-width:130px;">' +
        '<div style="font-weight:700;font-size:13px;">' + esc(n.name) + pct + '</div>' +
        '<div style="color:#666;font-size:11px;">' + esc(n.country || '') + ' · ' +
        (n.nodeType === 'PERSON' ? '개인' : '법인') + '</div>' +
        '<div style="margin-top:4px;">' + badge(n.grade) + '</div>' +
        (n.stakeNote ? '<div style="font-size:11px;color:#666;margin-top:4px;">' + esc(n.stakeNote) + '</div>' : '') +
        '</div>';
    }

    function branch(key, depth) {
      var kids = children[key] || [];
      if (!kids.length || depth > 6) return '';
      var html = '<ul style="list-style:none;margin:6px 0 0;padding-left:18px;border-left:2px solid #e0e0e0;">';
      kids.forEach(function (item) {
        var e = item.edge || {};
        var pct = (e.stakePct !== null && e.stakePct !== undefined) ? esc(e.stakePct) + '%' : '—';
        html += '<li style="margin:8px 0;">' +
          '<div style="font-size:11px;color:#666;margin-bottom:4px;">보유 ' + pct + ' ' + badge(e.grade) +
          (e.note ? ' <span style="color:#999;">' + esc(e.note) + '</span>' : '') + '</div>' +
          nodeBox(item.child ? item.child.nodeKey : '') +
          branch(item.child ? item.child.nodeKey : '', depth + 1) +
          '</li>';
      });
      return html + '</ul>';
    }

    var html = '<div style="margin-top:12px;border-top:1px dashed #ccc;padding-top:12px;">' +
      '<div style="font-size:13px;font-weight:700;margin-bottom:8px;">API 기준 동적 트리 (기준일 ' +
      esc(data.asOfDate || '') + ')</div>';
    (data.roots || []).forEach(function (r) {
      html += '<div style="margin-bottom:10px;">' + nodeBox(r) + branch(r, 0) + '</div>';
    });

    if ((data.effectiveStakes || []).length) {
      html += '<div style="font-size:13px;font-weight:700;margin:10px 0 6px;">간접 경제지분 ' + badge('D') + '</div><ul style="margin:0;padding-left:18px;font-size:12px;">';
      data.effectiveStakes.forEach(function (s) {
        html += '<li><strong>' + esc(s.rootName) + '</strong> → 위메이드: 약 <strong>' +
          esc(s.effectivePct) + '%</strong> <span style="color:#999;">(' +
          (s.path || []).map(esc).join(' → ') + ')</span></li>';
      });
      html += '</ul>';
    }
    html += '</div>';
    root.innerHTML = html;
  }

  function init() {
    var root = document.getElementById('wemade-ownership-root');
    if (!root) return;
    var api = root.getAttribute('data-api') || '/api/wemade/ownership/tree';
    fetch(api, { headers: { 'Accept': 'application/json' } })
      .then(function (res) { if (!res.ok) throw new Error('HTTP ' + res.status); return res.json(); })
      .then(function (data) {
        if (!data || !data.nodes || !data.nodes.length) return; // 정적 fragment 유지
        renderTree(root, data);
      })
      .catch(function () { /* 정적 fragment가 공식 확인분을 보여주므로 조용히 유지 */ });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
