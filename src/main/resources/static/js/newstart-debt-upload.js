(function () {
  'use strict';

  var MAX_BYTES = 20 * 1024 * 1024;
  var MAX_PDF_PAGES = 20;
  var MAX_DEBT_ROWS = 50;
  var draftItems = [];

  function el(id) { return document.getElementById(id); }
  function setStatus(message) { var node = el('ns-debt-upload-state'); if (node) node.textContent = message || ''; }
  function clean(text) {
    return String(text || '')
      .replace(/\b\d{6}[- ]?[1-4]\d{6}\b/g, '[개인정보 제거]')
      // 10~16자리 숫자는 계좌번호 등으로 보고 제거하되, 금액(뒤에 '원'이 옴)은 보존한다.
      // OCR이 쉼표를 빼먹은 큰 금액(예: 1500000000원)이 오제거되는 것을 방지.
      .replace(/\b\d{10,16}\b(?!\s*원)/g, '[개인정보 제거]')
      .replace(/[ \t]+/g, ' ').trim();
  }

  function imageCanvas(file) {
    return new Promise(function (resolve, reject) {
      var url = URL.createObjectURL(file);
      var image = new Image();
      image.onload = function () {
        var scale = Math.min(1, 2200 / Math.max(image.naturalWidth, image.naturalHeight));
        var canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
        canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
        canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height);
        URL.revokeObjectURL(url);
        resolve(canvas);
      };
      image.onerror = function () { URL.revokeObjectURL(url); reject(new Error('이미지를 읽을 수 없습니다.')); };
      image.src = url;
    });
  }

  function ocr(canvas, label) {
    if (!window.Tesseract) return Promise.reject(new Error('문자 인식 도구를 불러오지 못했습니다.'));
    return window.Tesseract.recognize(canvas, 'kor', {
      workerPath: '/vendor/tesseract/worker.min.js',
      corePath: '/vendor/tesseract/core',
      langPath: '/vendor/tesseract/lang-data',
      // blob 워커에서는 절대경로 importScripts가 실패하므로 실제 스크립트 URL을 쓴다(미지정 시 OCR 전건 실패).
      workerBlobURL: false,
      logger: function (m) {
        if (m.status === 'recognizing text') setStatus(label + ' 문자 인식 중 ' + Math.round((m.progress || 0) * 100) + '%');
      }
    }).then(function (result) { return clean(result.data && result.data.text); });
  }

  async function readPdf(file) {
    if (!window.pdfjsLib) throw new Error('PDF 읽기 도구를 불러오지 못했습니다.');
    window.pdfjsLib.GlobalWorkerOptions.workerSrc = '/vendor/pdfjs/pdf.worker.min.js';
    var pdf = await window.pdfjsLib.getDocument({ data: new Uint8Array(await file.arrayBuffer()) }).promise;
    var texts = [];
    var pages = Math.min(pdf.numPages, MAX_PDF_PAGES);
    for (var pageNo = 1; pageNo <= pages; pageNo++) {
      setStatus('PDF ' + pageNo + '/' + pages + '쪽 읽는 중');
      var page = await pdf.getPage(pageNo);
      var content = await page.getTextContent();
      var text = clean(content.items.map(function (item) { return item.str || ''; }).join('\n'));
      if (text.length < 30) {
        var viewport = page.getViewport({ scale: 1.7 });
        var canvas = document.createElement('canvas');
        canvas.width = viewport.width; canvas.height = viewport.height;
        await page.render({ canvasContext: canvas.getContext('2d'), viewport: viewport }).promise;
        text = await ocr(canvas, 'PDF ' + pageNo + '쪽');
      }
      texts.push(text);
    }
    return texts.join('\n');
  }

  async function extract(file) {
    if (file.size > MAX_BYTES) throw new Error('파일은 20MB 이하만 선택할 수 있습니다.');
    var type = (file.type || '').toLowerCase();
    if (type === 'application/pdf' || /\.pdf$/i.test(file.name)) return readPdf(file);
    if (!/image\/(jpeg|png)/.test(type) && !/\.(jpe?g|png)$/i.test(file.name)) throw new Error('JPG, PNG, PDF만 선택할 수 있습니다.');
    return ocr(await imageCanvas(file), '이미지');
  }

  // ===== 채무 파싱 (블록 기반) =====
  // Credit4U처럼 여러 채무가 반복되는 자료에서 채무 1건 = 1행을 보장한다.
  // 전략: 채권기관명으로 블록 경계를 찾고, 각 블록 안에서 필드를 우선순위로 추출한다.

  // 강한 채권기관 패턴: 기관명 + 접미사 (헤더/채무종류 오인 방지)
  var STRONG_CREDITOR_RE = /([가-힣A-Za-z0-9]+(은행|카드|캐피탈|저축은행|신용금고|농협|수협|새마을금고|신협|자산관리|파이낸스))/;
  // 제외할 헤더 패턴
  var HEADER_RE = /(신용정보|등록현황|대출정보|연체정보|보증정보|조회결과|본인신용|현황조회)/;
  // 채무종류 패턴
  var TYPE_RE = /(대출|카드론|현금서비스|할부|보증|채무|신용공여)/;
  // 연체 패턴
  var OVERDUE_RE = /(장기연체|연체|신용도판단|공공정보)/;
  // 채권자변동 패턴
  var CHANGE_RE = /(채권자\s*변동|양도|양수|이관|매각)/;
  // 잔액 우선 패턴 (진짜 잔액)
  var BALANCE_PRIORITY_RE = /(채무잔액|대출잔액|잔액)/;
  // 대출원금 패턴 (잔액이 없을 때 차선책)
  var PRINCIPAL_RE = /(대출금액|대출액|원금|이용금액)/;
  // 금액 패턴
  var AMOUNT_RE = /([\d,]+)\s*원/;

  function isCreditorBoundary(line) {
    if (!line) return false;
    if (HEADER_RE.test(line)) return false;
    if (CHANGE_RE.test(line)) return false; // "채권자 변동 한국자산관리공사"는 경계가 아님
    // 강한 패턴 매칭 (기관명 + 접미사)
    var m = STRONG_CREDITOR_RE.exec(line);
    if (!m) return false;
    // 채무종류가 주된 내용인 줄 제외 (예: "일반신용대출" — "신용"만으로는 기관 아님)
    // 강한 패턴은 접미사(은행/카드 등)가 있어야 하므로 이미 걸러짐
    return true;
  }

  function extractCreditorName(line) {
    var m = STRONG_CREDITOR_RE.exec(line || '');
    return m ? m[1] : clean(line);
  }

  function extractAmount(line) {
    var m = AMOUNT_RE.exec(line || '');
    return m ? m[1] + '원' : '';
  }

  function segmentDebtBlocks(lines) {
    var boundaries = [];
    lines.forEach(function (line, i) {
      if (isCreditorBoundary(line)) boundaries.push(i);
    });
    if (!boundaries.length) {
      // 채권기관을 못 찾으면 전체를 1개 블록으로 (기존 동작의 안전한 fallback)
      return lines.length ? [lines] : [];
    }
    var blocks = [];
    for (var b = 0; b < boundaries.length; b++) {
      var start = boundaries[b];
      var end = (b + 1 < boundaries.length) ? boundaries[b + 1] : lines.length;
      var block = lines.slice(start, end);
      if (block.length) blocks.push(block);
    }
    return blocks;
  }

  function extractDebtFields(block) {
    var item = { creditor: '', debtType: '', balance: '', overdueInfo: '', creditorChange: '' };
    if (!block || !block.length) return item;

    // 채권기관: 블록의 첫 줄 (경계선 자체)
    item.creditor = extractCreditorName(block[0]);

    var i, line;
    // 채무종류: 블록 안에서 종류 패턴이 있는 첫 줄 (기관줄 제외)
    for (i = 1; i < block.length; i++) {
      line = block[i];
      if (TYPE_RE.test(line) && !isCreditorBoundary(line)) {
        item.debtType = clean(line);
        break;
      }
    }

    // 잔액: 우선순위 1) 잔액/채무잔액/대출잔액 라벨 + 금액
    //       우선순위 2) 라벨 없는 순수 금액 (대출금액/원금 라벨이 없는 것)
    //       우선순위 3) 대출금액/원금 (차선책 — 그래도 비워두기보다는 표시)
    var balanceLine = '';
    for (i = 0; i < block.length; i++) {
      line = block[i];
      if (BALANCE_PRIORITY_RE.test(line) && AMOUNT_RE.test(line)) { balanceLine = line; break; }
    }
    if (!balanceLine) {
      for (i = 0; i < block.length; i++) {
        line = block[i];
        if (AMOUNT_RE.test(line) && !PRINCIPAL_RE.test(line)) { balanceLine = line; break; }
      }
    }
    if (!balanceLine) {
      for (i = 0; i < block.length; i++) {
        line = block[i];
        if (AMOUNT_RE.test(line)) { balanceLine = line; break; }
      }
    }
    if (balanceLine) item.balance = extractAmount(balanceLine);

    // 연체정보: 블록 안에서 연체 패턴이 있는 첫 줄
    for (i = 0; i < block.length; i++) {
      line = block[i];
      if (OVERDUE_RE.test(line)) { item.overdueInfo = clean(line); break; }
    }

    // 채권자변동: 블록 안에서 변동 패턴이 있는 첫 줄
    for (i = 0; i < block.length; i++) {
      line = block[i];
      if (CHANGE_RE.test(line)) { item.creditorChange = clean(line); break; }
    }

    return item;
  }

  function parseDebt(text) {
    var lines = String(text || '').split(/\n+/).map(clean).filter(Boolean);
    if (!lines.length) return [];

    var blocks = segmentDebtBlocks(lines);
    var items = [];
    var seen = {};
    blocks.forEach(function (block) {
      var item = extractDebtFields(block);
      // 빈 블록 제거: 기관도 잔액도 없으면 버림
      if (!item.creditor && !item.balance) return;
      // 완전 동일 블록만 제거 (과도한 중복제거 금지 — 유사한 채무는 별도 행으로 유지)
      var key = [item.creditor, item.debtType, item.balance, item.overdueInfo, item.creditorChange].join('|');
      if (seen[key]) return;
      seen[key] = true;
      items.push(item);
    });
    return items.slice(0, MAX_DEBT_ROWS);
  }

  // 하위호환: 기존 findNear를 쓰는 코드가 있으면 동작하도록 유지
  function findNear(lines, index, pattern) {
    for (var distance = 0; distance <= 5; distance++) {
      var candidates = [lines[index + distance], lines[index - distance]];
      for (var i = 0; i < candidates.length; i++) if (candidates[i] && pattern.test(candidates[i])) return candidates[i];
    }
    return '';
  }

  function input(value, label) {
    var node = document.createElement('input');
    node.type = 'text'; node.className = 'ns-input ns-debt-result-input';
    node.value = clean(value); node.maxLength = 200; node.setAttribute('aria-label', label);
    return node;
  }

  function render() {
    var host = el('ns-debt-analysis-results');
    if (!host) return;
    host.innerHTML = '';
    if (!draftItems.length) { host.hidden = true; return; }
    host.hidden = false;
    draftItems.forEach(function (item, index) {
      var card = document.createElement('div'); card.className = 'ns-debt-result-card'; card.dataset.index = index;
      [['creditor', '채권기관'], ['debtType', '채무종류'], ['balance', '잔액'], ['overdueInfo', '연체·장기연체'], ['creditorChange', '채권자 변동']]
        .forEach(function (field) {
          var label = document.createElement('label'); label.className = 'ns-debt-result-field';
          var title = document.createElement('span'); title.textContent = field[1];
          var control = input(item[field[0]], field[1] + ' ' + (index + 1));
          control.addEventListener('input', function () { draftItems[index][field[0]] = clean(control.value); });
          label.appendChild(title); label.appendChild(control); card.appendChild(label);
        });
      var remove = document.createElement('button'); remove.type = 'button'; remove.className = 'ns-btn ns-btn-ghost';
      remove.textContent = '이 항목 제외'; remove.addEventListener('click', function () { draftItems.splice(index, 1); render(); });
      card.appendChild(remove); host.appendChild(card);
    });
  }

  function draftSummary() {
    if (!draftItems.length) return '';
    return '[Credit4U 분석]\n' + draftItems.map(function (item) {
      return ['기관 ' + item.creditor, '종류 ' + item.debtType, '잔액 ' + item.balance,
        '연체 ' + item.overdueInfo, '변동 ' + item.creditorChange].filter(function (v) { return !/\s$/.test(v); }).join(' / ');
    }).join('\n');
  }

  function init() {
    var inputNode = el('ns-debt-file');
    var upload = el('ns-debt-upload');
    var clear = el('ns-debt-analysis-clear');
    if (!inputNode || !upload) return;
    upload.addEventListener('click', function () { inputNode.click(); });
    inputNode.addEventListener('change', async function () {
      var file = inputNode.files && inputNode.files[0]; inputNode.value = '';
      if (!file) return;
      upload.disabled = true; setStatus('자료를 기기 안에서 읽는 중입니다.');
      try {
        draftItems = parseDebt(await extract(file));
        if (!draftItems.length) draftItems = [{ creditor: '', debtType: '', balance: '', overdueInfo: '', creditorChange: '' }];
        render(); setStatus('분석 결과를 확인하고 수정하세요. 아직 저장되지 않았습니다.');
      } catch (e) { setStatus(e && e.message ? e.message : '자료를 읽지 못했습니다.'); }
      upload.disabled = false;
    });
    if (clear) clear.addEventListener('click', function () { draftItems = []; render(); setStatus('분석 결과를 지웠습니다. 기존 메모는 그대로입니다.'); });
  }

  window.NewStartDebtDraft = {
    summary: draftSummary,
    markSaved: function () { draftItems = []; render(); },
    parseDebt: parseDebt,
    analyzeFile: async function (file) { return parseDebt(await extract(file)); }
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
