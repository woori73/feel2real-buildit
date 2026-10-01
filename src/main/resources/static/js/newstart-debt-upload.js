(function () {
  'use strict';

  var MAX_BYTES = 20 * 1024 * 1024;
  var MAX_PDF_PAGES = 20;
  var draftItems = [];

  function el(id) { return document.getElementById(id); }
  function setStatus(message) { var node = el('ns-debt-upload-state'); if (node) node.textContent = message || ''; }
  function clean(text) {
    return String(text || '')
      .replace(/\b\d{6}[- ]?[1-4]\d{6}\b/g, '[개인정보 제거]')
      .replace(/\b\d{10,16}\b/g, '[개인정보 제거]')
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

  function findNear(lines, index, pattern) {
    for (var distance = 0; distance <= 2; distance++) {
      var candidates = [lines[index + distance], lines[index - distance]];
      for (var i = 0; i < candidates.length; i++) if (candidates[i] && pattern.test(candidates[i])) return candidates[i];
    }
    return '';
  }

  function parseDebt(text) {
    var lines = String(text || '').split(/\n+/).map(clean).filter(Boolean);
    var creditorRe = /(은행|카드|캐피탈|저축|대부|보증|신용|금융|채권|공사|기금|기관)/;
    var typeRe = /(대출|카드론|현금서비스|할부|보증|채무|신용공여)/;
    var balanceRe = /(잔액|채무액|대출금액|원금|\d[\d,]*\s*원)/;
    var overdueRe = /(장기연체|연체|신용도판단|공공정보)/;
    var changeRe = /(채권자\s*변동|양도|양수|이관|매각)/;
    var anchors = [];
    lines.forEach(function (line, i) { if (balanceRe.test(line) || creditorRe.test(line)) anchors.push(i); });
    if (!anchors.length && lines.length) anchors.push(0);
    var seen = {};
    var items = anchors.map(function (i) {
      return {
        creditor: findNear(lines, i, creditorRe),
        debtType: findNear(lines, i, typeRe),
        balance: findNear(lines, i, balanceRe),
        overdueInfo: findNear(lines, i, overdueRe),
        creditorChange: findNear(lines, i, changeRe)
      };
    }).filter(function (item) {
      var key = [item.creditor, item.debtType, item.balance, item.overdueInfo, item.creditorChange].join('|');
      if (!key.replace(/\|/g, '') || seen[key]) return false;
      seen[key] = true; return true;
    });
    return items.slice(0, 20);
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
