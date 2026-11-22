/*
 * Digital SAT 본문 서식 표기 → 안전한 HTML.
 * 원문은 먼저 전부 이스케이프한 뒤, 정해진 표기만 태그로 바꾼다(그 밖의 HTML은 글자로 보인다).
 *   *기울임*  **굵게**  ^{위첨자}  _{아래첨자}  \frac{a}{b}  \sqrt{x}  ______(빈칸)
 *   빈 줄 = 문단 · "|"로 시작하는 줄 = 표(첫 줄 머리) · "• "로 시작하는 줄 = 목록
 *   한 줄이 [[img:/testprep/img/파일.svg]] = 그림(허용 경로만 <img src>로, 그 밖은 지운다)
 * 표(table)·그림(image)은 TestPrep 공용 표시(testprep.js의 TP.richBody)도 함께 쓴다.
 * 설계: docs/design/testprep-digital-sat.md §5
 */
(function (root) {
  'use strict';

  function esc(v) {
    return String(v == null ? '' : v).replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c]));
  }

  // 중괄호 안에는 중괄호가 없는 짧은 표기만 허용한다(중첩 없음).
  function inline(raw) {
    let s = esc(raw);
    s = s.replace(/\\frac\{([^{}]*)\}\{([^{}]*)\}/g, '<span class="sat-frac"><span class="sat-num">$1</span><span class="sat-den">$2</span></span>');
    s = s.replace(/\\sqrt\{([^{}]*)\}/g, '<span class="sat-sqrt">√<span class="sat-rad">$1</span></span>');
    s = s.replace(/\^\{([^{}]*)\}/g, '<sup>$1</sup>');
    s = s.replace(/_\{([^{}]*)\}/g, '<sub>$1</sub>');
    s = s.replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>');
    s = s.replace(/\*([^*]+)\*/g, '<i>$1</i>');
    s = s.replace(/_{4,}/g, '<span class="sat-blank" aria-label="blank">______</span>');
    return s;
  }

  function table(lines) {
    const rows = lines.map(l => l.replace(/^\|/, '').split('|').map(c => c.trim()));
    const head = rows.shift();
    return '<table class="sat-table"><thead><tr>' + head.map(c => '<th>' + inline(c) + '</th>').join('') + '</tr></thead><tbody>'
      + rows.map(r => '<tr>' + r.map(c => '<td>' + inline(c) + '</td>').join('') + '</tr>').join('') + '</tbody></table>';
  }

  // 그림 한 줄 표기. 줄 전체가 [[img:경로]]일 때만 그림으로 본다.
  const IMG_LINE = /^\s*\[\[img:([^\]]*)\]\]\s*$/;
  const IMG_PATH = /^\/testprep\/img\/[A-Za-z0-9_\-./]+\.(?:svg|webp|png)$/;

  // 그림 경로 → <img>. /testprep/img/ 아래의 svg·webp·png만 허용하고(".." 금지), 그 밖은 빈 글을 돌려준다(그리지 않는다).
  function image(path) {
    const src = String(path == null ? '' : path).trim();
    if (!IMG_PATH.test(src) || src.includes('..')) return '';
    return '<img class="sat-img" src="' + esc(src) + '" alt="">';
  }

  // 그림 줄이면 경로(허용 여부는 image가 본다), 아니면 null.
  function imageLine(line) {
    const m = IMG_LINE.exec(line);
    return m ? m[1] : null;
  }

  function render(text) {
    if (text == null || text === '') return '';
    const lines = String(text).replace(/\r\n?/g, '\n').split('\n');
    const out = [];
    let para = [];
    let i = 0;
    const flushPara = () => { if (para.length) { out.push('<p>' + para.map(inline).join('<br>') + '</p>'); para = []; } };
    while (i < lines.length) {
      const line = lines[i];
      if (/^\s*$/.test(line)) { flushPara(); i++; continue; }
      if (imageLine(line) != null) { flushPara(); out.push(image(imageLine(line))); i++; continue; }
      if (/^\s*\|/.test(line)) {
        flushPara();
        const block = [];
        while (i < lines.length && /^\s*\|/.test(lines[i])) block.push(lines[i++].trim());
        out.push(table(block));
        continue;
      }
      if (/^\s*•\s+/.test(line)) {
        flushPara();
        const items = [];
        while (i < lines.length && /^\s*•\s+/.test(lines[i])) items.push(lines[i++].replace(/^\s*•\s+/, ''));
        out.push('<ul class="sat-list">' + items.map(t => '<li>' + inline(t) + '</li>').join('') + '</ul>');
        continue;
      }
      para.push(line);
      i++;
    }
    flushPara();
    return out.join('');
  }

  root.SATRich = { render, inline, esc, table, image, imageLine };
})(typeof window !== 'undefined' ? window : globalThis);
