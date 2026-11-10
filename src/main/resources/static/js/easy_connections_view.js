/**
 * F2R 장부 — 자동수집 연결 화면의 표시 변환(순수 함수). 저장된 값은 바꾸지 않고 사람이 읽는 이름·상태만 만든다.
 * 브라우저에서는 window.EasyConnView, 테스트(Node)에서는 module.exports로 쓴다.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.EasyConnView = api;
})(typeof self !== 'undefined' ? self : this, function () {
  /** 카탈로그(/sites)에 없는 파일 이름 앞부분. 긴 것부터 맞춘다. */
  const LABEL_ALIASES = [
    ['sales_slip', '카드매출 전표'],
    ['cardsales', '카드매출(여신협회)'],
    ['crefia', '카드매출(여신협회)'],
    ['hometax', '홈택스']
  ];
  const BANK_ORGS = { '0004': 'KB국민은행', '0088': '신한은행' };

  /** "202609" / "20260727" 같은 날짜 조각 → "2026년 9월". 없으면 null. */
  function monthText(label) {
    const m = String(label || '').match(/(?:^|[^0-9])(20\d{2})(0[1-9]|1[0-2])(?:\d{2})?(?=$|[^0-9])/);
    return m ? m[1] + '년 ' + Number(m[2]) + '월' : null;
  }

  /** 작업용 파일 이름(예: KB_CARD_202609)을 "KB국민카드 · 2026년 9월 파일"로 바꾼다. 모르면 원래 이름. */
  function labelName(label, siteNames) {
    const raw = String(label || '').trim();
    if (!raw || raw === 'default') return '파일 넣기';
    const lower = raw.toLowerCase();
    let institution = null;
    const codes = Object.keys(siteNames || {}).sort((a, b) => b.length - a.length);
    for (const code of codes) {
      if (lower === code.toLowerCase() || lower.startsWith(code.toLowerCase() + '_')) { institution = siteNames[code]; break; }
    }
    if (!institution) {
      const alias = LABEL_ALIASES.find(([prefix]) => lower === prefix || lower.startsWith(prefix + '_'));
      if (alias) institution = alias[1];
    }
    const month = monthText(raw);
    if (!institution) return raw;
    return month ? institution + ' · ' + month + ' 파일' : institution;
  }

  /** 수집 방식: MCP(직접 로그인 수집) / FILE(파일) / AUTO(기관 자동 연동). */
  function method(conn) {
    if (conn.latestRunAdapterCode === 'MCP_SITE' || String(conn.label || '').startsWith('MS_')) return 'MCP';
    if (conn.sourceCode === 'EXCEL_FILE') return 'FILE';
    return 'AUTO';
  }

  const METHOD_TEXT = { MCP: '직접 로그인 수집', FILE: '파일 넣기', AUTO: '자동 연동' };

  /** 상태는 네 가지만: 연결됨 / 다시 인증 필요 / 파일 수집 / 직접 로그인 수집. */
  function status(conn) {
    const m = method(conn);
    if (m === 'MCP') return '직접 로그인 수집';
    if (m === 'FILE') return '파일 수집';
    if (conn.readiness === 'READY' && conn.connectionStatus !== 'DISCONNECTED' && conn.authStatus !== 'NEEDS_REAUTH') return '연결됨';
    return '다시 인증 필요';
  }

  /** 사람이 읽는 기관 이름. */
  function connectionName(conn, siteNames) {
    const m = method(conn);
    if (m === 'MCP') {
      const name = (siteNames && siteNames[conn.siteCode]) || conn.siteCode || '기관';
      return conn.siteAccountRef ? name + ' · ' + conn.siteAccountRef : name;
    }
    if (m === 'FILE') return labelName(conn.label, siteNames);
    const bank = BANK_ORGS[conn.organization];
    return [conn.label, bank].filter(Boolean).join(' · ') || conn.sourceName || '기관';
  }

  function lastCollected(conn) {
    return conn.latestRunFinishedAt || conn.latestRunStartedAt || conn.lastSuccessAt || null;
  }

  /** 표 한 줄 = 기관 하나. 직접 로그인 수집은 같은 기관끼리 묶는다(계좌가 여러 개여도 한 줄). */
  function rows(conns, siteNames) {
    const out = [];
    const mcpByName = new Map();
    for (const conn of conns || []) {
      const m = method(conn);
      if (m === 'MCP') {
        const name = (siteNames && siteNames[conn.siteCode]) || conn.siteCode || '기관';
        if (!mcpByName.has(name)) {
          const row = { name, method: METHOD_TEXT.MCP, status: status(conn), last: null, saved: 0, connections: [], kind: m };
          mcpByName.set(name, row); out.push(row);
        }
        const row = mcpByName.get(name);
        row.connections.push(conn);
        row.saved += Number(conn.ledgerSavedTotal || 0);
        const last = lastCollected(conn);
        if (last && (!row.last || last > row.last)) row.last = last;
        continue;
      }
      out.push({ name: connectionName(conn, siteNames), method: METHOD_TEXT[m], status: status(conn),
        last: lastCollected(conn), saved: Number(conn.ledgerSavedTotal || 0), connections: [conn], kind: m });
    }
    return out;
  }

  function dayText(value) {
    const m = String(value || '').match(/^\d{4}-(\d{2})-(\d{2})/);
    return m ? Number(m[1]) + '월 ' + Number(m[2]) + '일' : '-';
  }

  /** 맨 위 한 줄 상태. 연결된 기관 = 연결됨 자동 연동 + 직접 로그인 수집 기관. */
  function summary(conns, siteNames) {
    const connected = rows(conns, siteNames).filter(r => r.status === '연결됨' || r.status === '직접 로그인 수집');
    if (!connected.length) return '연결된 기관 0곳 · 지금은 파일 넣기로 수집 중';
    const last = connected.map(r => r.last).filter(Boolean).sort().pop();
    return '연결된 기관 ' + connected.length + '곳 · 마지막 수집 ' + dayText(last);
  }

  /** CSV 한 줄(큰따옴표 지원). */
  function splitCsvLine(line) {
    const cells = []; let cur = ''; let quoted = false;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (quoted) {
        if (ch === '"' && line[i + 1] === '"') { cur += '"'; i++; }
        else if (ch === '"') quoted = false;
        else cur += ch;
      } else if (ch === '"') quoted = true;
      else if (ch === ',') { cells.push(cur.trim()); cur = ''; }
      else cur += ch;
    }
    cells.push(cur.trim());
    return cells;
  }

  const DATE_HEADERS = ['거래일', '거래일자', '거래일시', '일자', 'date'];
  const AMOUNT_HEADERS = ['금액', '거래금액', '지출금액', '입금금액', 'amount'];

  /** 미리보기: 머리글 확인·행 수·앞 5행·첫 날짜. 저장 판단은 서버가 한다. */
  function previewCsv(text) {
    const lines = String(text || '').replace(/^﻿/, '').split(/\r?\n/).filter(l => l.trim());
    if (!lines.length) return { ok: false, error: '파일이 비어 있습니다.' };
    const header = splitCsvLine(lines[0]);
    const norm = header.map(h => h.replace(/\s|_/g, '').toLowerCase());
    const dateIdx = norm.findIndex(h => DATE_HEADERS.includes(h));
    const amountIdx = norm.findIndex(h => AMOUNT_HEADERS.includes(h));
    if (dateIdx < 0 || amountIdx < 0) {
      return { ok: false, error: '첫 줄에 「거래일」과 「금액」 칸이 있어야 합니다. 엑셀 파일은 CSV로 저장해 올려 주세요.' };
    }
    const body = lines.slice(1).map(splitCsvLine);
    const firstDate = (body.map(r => r[dateIdx]).find(v => /^\d{4}[-./]?\d{2}/.test(v || '')) || '');
    return { ok: true, header, rows: body.length, sample: body.slice(0, 5), firstDate };
  }

  /** 파일 넣기 연결 이름: 기관 코드 + 파일의 첫 거래 연월(예: KB_CARD_202609). 기존 이름 규칙과 같다. */
  function fileLabel(siteCode, firstDate, today) {
    const digits = String(firstDate || '').replace(/[^0-9]/g, '');
    const ym = /^20\d{2}(0[1-9]|1[0-2])/.test(digits) ? digits.slice(0, 6)
      : String(today || '').replace(/[^0-9]/g, '').slice(0, 6);
    return (siteCode || 'FILE') + '_' + ym;
  }

  /** 카탈로그 안내 문장의 개발 용어를 사람이 읽는 말로 바꾼다(카탈로그 원문은 MCP용으로 그대로 둔다). */
  function plainStep(text) {
    return String(text || '')
      .replace(/\(개인사업자·법인은 businessUrl\)/g, '(개인사업자·법인은 기업·사업자용 주소)')
      .replace(/businessUrl/g, '기업·사업자용 주소')
      .replace(/\s*save_easy_site_rows로 저장합니다\.?/g, ' Claude가 장부에 저장합니다.')
      .replace(/(dataset=[A-Z_]+로 저장합니다\.?)/g, 'Claude가 장부에 저장합니다.')
      .replace(/expectedCount·expectedTotal로/g, '함께')
      .replace(/MCP 도구 인자/g, '도구 입력');
  }

  return { plainStep, labelName, method, status, connectionName, rows, summary, previewCsv, fileLabel, monthText, dayText };
});
